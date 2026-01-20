package dev.luxmiyu.miniteleport;

import dev.luxmiyu.miniteleport.maphandlers.EmptyMapHandler;
import dev.luxmiyu.miniteleport.maphandlers.IMapHandler;
import dev.luxmiyu.miniteleport.maphandlers.pl3x.Pl3xMapHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.command.DefaultPermissions;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.util.Formatting;
import net.minecraft.world.WorldProperties;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.command.argument.EntityArgumentType;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.world.rule.GameRules;

import java.util.List;
import java.util.UUID;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.Nullable;

public class MiniTeleport implements ModInitializer {

    static final Predicate<ServerCommandSource> PERMISSIONS_NORMAL = source -> true;
    static final Predicate<ServerCommandSource> PERMISSIONS_ADMIN = source -> source.getPermissions().hasPermission(DefaultPermissions.OWNERS);

    static final long REQUEST_TIMEOUT_MS = 60_000; // 60 seconds

    final List<TeleportRequest> pendingRequests = new CopyOnWriteArrayList<>();

    private IMapHandler mapHandler;

    //region REQUESTS
    // ------ REQUESTS -------------------------------------------------------------------------------------------

    void addRequest(TeleportRequest request) {
        // remove duplicate pairs
        pendingRequests.removeIf(r -> r.sender().equals(request.sender()) && r.receiver().equals(request.receiver()));
        pendingRequests.add(request);
    }

    void removeRequest(TeleportRequest request) {
        pendingRequests.remove(request);
    }

    TeleportRequest getMostRecentRequest(UUID receiver) {
        return pendingRequests.stream().filter(r -> r.receiver().equals(receiver))
            .max(Comparator.comparingLong(TeleportRequest::expiry)).orElse(null);
    }

    TeleportRequest getRequest(UUID receiver, UUID sender) {
        return pendingRequests.stream().filter(r -> r.receiver().equals(receiver) && r.sender().equals(sender))
            .findFirst().orElse(null);
    }

    void cleanupExpiredRequests() {
        long now = System.currentTimeMillis();
        pendingRequests.removeIf(r -> r.expiry() < now);
    }

    void sendTeleportRequest(ServerPlayerEntity sender, ServerPlayerEntity receiver, boolean here) {
        cleanupExpiredRequests();

        long expiry = System.currentTimeMillis() + REQUEST_TIMEOUT_MS;
        TeleportRequest request = new TeleportRequest(sender.getUuid(), receiver.getUuid(), here, expiry);
        addRequest(request);

        Text message = Text.literal(
                String.format("%s wants to teleport %s. ", sender.getName().getString(), here ? "you to them" : "to you")
            )
            .formatted(Formatting.YELLOW).append(Text.literal("[Accept]").formatted(Formatting.GREEN).styled(
                style -> style.withClickEvent(new ClickEvent.RunCommand("/tpaccept " + sender.getName().getString()))
                    .withHoverEvent(new HoverEvent.ShowText(
                        Text.literal("Accept teleport request from " + sender.getName().getString()))))

            )
            .append(Text.literal(" "))
            .append(Text.literal("[Deny]").formatted(Formatting.RED).styled(
                style -> style.withClickEvent(new ClickEvent.RunCommand("/tpdeny " + sender.getName().getString()))
                    .withHoverEvent(new HoverEvent.ShowText(
                        Text.literal("Deny teleport request from " + sender.getName().getString())))));

        receiver.sendMessage(message, false);
        sender.sendMessage(
            Text.literal("Teleport request sent to " + receiver.getName().getString()).formatted(Formatting.AQUA),
            false);
    }

    void cancelTeleportRequest(ServerPlayerEntity sender) {
        cleanupExpiredRequests();

        List<TeleportRequest> requests =
            pendingRequests.stream().filter(r -> r.sender().equals(sender.getUuid())).toList();

        if (requests.isEmpty()) {
            sender.sendMessage(Text.literal("You have no pending teleport requests.").formatted(Formatting.RED), false);
            return;
        }

        for (TeleportRequest request : requests) {
            ServerPlayerEntity receiver =
                sender.getEntityWorld().getServer().getPlayerManager().getPlayer(request.receiver());

            if (receiver != null) {
                receiver.sendMessage(
                    Text.literal(sender.getName().getString() + " cancelled their teleport request.")
                        .formatted(Formatting.YELLOW),
                    false
                );
            }

            removeRequest(request);
        }

        sender.sendMessage(Text.literal("Teleport request cancelled.").formatted(Formatting.YELLOW), false);
    }

    void acceptTeleportRequest(ServerPlayerEntity receiver, @Nullable ServerPlayerEntity sender) {
        cleanupExpiredRequests();

        TeleportRequest request;

        if (sender != null) {
            request = getRequest(receiver.getUuid(), sender.getUuid());
        } else {
            request = getMostRecentRequest(receiver.getUuid());
        }

        if (request == null) {
            receiver.sendMessage(Text.literal("Teleport request expired or doesn't exist.").formatted(Formatting.RED),
                false);
            return;
        }

        ServerPlayerEntity actualSender =
            receiver.getEntityWorld().getServer().getPlayerManager().getPlayer(request.sender());
        if (actualSender == null) {
            receiver.sendMessage(Text.literal("Request sender is no longer online.").formatted(Formatting.RED), false);
            removeRequest(request);
            return;
        }

        if (request.here()) {
            WarpManager.warpPlayer(receiver,
                new Warp(actualSender.getName().getString(), (int) actualSender.getX(), (int) actualSender.getY(),
                    (int) actualSender.getZ(), actualSender.getEntityWorld().getRegistryKey().getValue().toString()));
            actualSender.sendMessage(Text.literal("Teleport request accepted!").formatted(Formatting.AQUA), false);
        } else {
            WarpManager.warpPlayer(actualSender,
                new Warp(receiver.getName().getString(), (int) receiver.getX(), (int) receiver.getY(),
                    (int) receiver.getZ(), receiver.getEntityWorld().getRegistryKey().getValue().toString()));
            receiver.sendMessage(Text.literal("Teleport request accepted!").formatted(Formatting.AQUA), false);
        }

        removeRequest(request);
    }

    void denyTeleportRequest(ServerPlayerEntity receiver, @Nullable ServerPlayerEntity sender) {
        cleanupExpiredRequests();

        TeleportRequest request;

        if (sender != null) {
            request = getRequest(receiver.getUuid(), sender.getUuid());
        } else {
            request = getMostRecentRequest(receiver.getUuid());
        }

        if (request == null) {
            receiver.sendMessage(Text.literal("Teleport request expired or doesn't exist.").formatted(Formatting.RED),
                false);
            return;
        }

        ServerPlayerEntity actualSender =
            receiver.getEntityWorld().getServer().getPlayerManager().getPlayer(request.sender());
        if (actualSender == null) {
            receiver.sendMessage(Text.literal("Request sender is no longer online.").formatted(Formatting.RED), false);
            removeRequest(request);
            return;
        }

        removeRequest(request);
    }
    //endregion

    //region COMMANDS
    // ------ COMMANDS ----------------------------------------------------------------------------------------

    MinecraftServer getServer(CommandContext<ServerCommandSource> context) {
        return context.getSource().getServer();
    }

    ServerPlayerEntity getPlayer(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("You must be a player to use this command."));
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
        }
        return player;
    }

    SuggestionProvider<ServerCommandSource> suggestWarps(boolean player) {
        return (context, builder) -> {
            MinecraftServer server = getPlayer(context.getSource()).getEntityWorld().getServer();
            UUID uuid = null;

            if (player) uuid = getPlayer(context.getSource()).getUuid();

            for (Warp warp : WarpManager.getWarps(WarpManager.getFile(server, uuid))) {
                builder.suggest(warp.name());
            }
            return builder.buildFuture();
        };
    }

    SuggestionProvider<ServerCommandSource> suggestPlayers() {
        return (context, builder) -> {
            ServerPlayerEntity sender = getPlayer(context.getSource());

            List<ServerPlayerEntity> players = sender.getEntityWorld().getServer().getPlayerManager().getPlayerList();

            for (ServerPlayerEntity player : players) {
                if (!sender.getUuid().equals(player.getUuid())) {
                    builder.suggest(player.getName().getString());
                }
            }

            return builder.buildFuture();
        };
    }

    void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("sethome")
            .requires(PERMISSIONS_NORMAL)
            .then(CommandManager.argument("name", StringArgumentType.word())
                .executes(context -> {
                    ServerPlayerEntity player = getPlayer(context.getSource());

                    String homeName = StringArgumentType.getString(context, "name");
                    WarpManager.setWarp(homeName, player, player.getUuid());

                    player.sendMessage(Text.literal(String.format("Home %s set!", homeName)).formatted(Formatting.AQUA),
                        false);
                    return 1;
                })
            )
            .executes(context -> {
                ServerPlayerEntity player = getPlayer(context.getSource());
                WarpManager.setWarp("home", player, player.getUuid());
                player.sendMessage(Text.literal("Home set!").formatted(Formatting.AQUA), false);
                return 1;
            })
        );

        dispatcher.register(CommandManager.literal("delhome")
            .requires(PERMISSIONS_NORMAL)
            .then(CommandManager.argument("name", StringArgumentType.word())
                .suggests(suggestWarps(true))
                .executes(context -> {
                    ServerPlayerEntity player = getPlayer(context.getSource());

                    String homeName = StringArgumentType.getString(context, "name");
                    return WarpManager.delWarp(homeName, player, player.getUuid());
                })
            )
            .executes(context -> {
                ServerPlayerEntity player = getPlayer(context.getSource());
                return WarpManager.delWarp("home", player, player.getUuid());
            })
        );

        dispatcher.register(CommandManager.literal("home")
            .requires(PERMISSIONS_NORMAL)
            .then(CommandManager.argument("name", StringArgumentType.word())
                .suggests(suggestWarps(true))
                .executes(context -> {
                    ServerPlayerEntity player = getPlayer(context.getSource());
                    String homeName = StringArgumentType.getString(context, "name");
                    return WarpManager.warpPlayer(player, WarpManager.getWarp(getServer(context), homeName, player.getUuid()));
                })
            ).executes(context -> {
                ServerPlayerEntity player = getPlayer(context.getSource());

                return WarpManager.warpPlayer(player, WarpManager.getWarp(getServer(context), "home", player.getUuid()));
            })
        );

        dispatcher.register(CommandManager.literal("homes")
            .requires(PERMISSIONS_NORMAL)
            .executes(context -> {
                ServerPlayerEntity player = getPlayer(context.getSource());
                player.sendMessage(WarpManager.listWarps(getServer(context), player.getUuid()), false);
                return 1;
            })
        );

        dispatcher.register(CommandManager.literal("back")
            .requires(PERMISSIONS_NORMAL)
            .executes(context -> {
                ServerPlayerEntity player = getPlayer(context.getSource());
                return WarpManager.warpPlayer(player, WarpManager.getWarp(getServer(context), "back", player.getUuid()));
            })
        );

        dispatcher.register(CommandManager.literal("setwarp")
            .requires(PERMISSIONS_ADMIN)
            .then(CommandManager.argument("name", StringArgumentType.word()).executes(context -> {
                ServerPlayerEntity player = getPlayer(context.getSource());

                String warpName = StringArgumentType.getString(context, "name");
                WarpManager.setWarp(warpName, player, null);

                player.sendMessage(Text.literal(String.format("Warp %s set!", warpName)).formatted(Formatting.AQUA),
                    false);
                return 1;
            }))
        );

        dispatcher.register(CommandManager.literal("delwarp")
            .requires(PERMISSIONS_ADMIN)
            .then(CommandManager.argument("name", StringArgumentType.word())
                .suggests(suggestWarps(false))
                .executes(context -> {
                    ServerPlayerEntity player = getPlayer(context.getSource());

                    String warpName = StringArgumentType.getString(context, "name");
                    return WarpManager.delWarp(warpName, player, null);
                })
            )
        );

        dispatcher.register(CommandManager.literal("warp")
            .requires(PERMISSIONS_NORMAL)
            .then(CommandManager.argument("name", StringArgumentType.word())
                .suggests(suggestWarps(false))
                .executes(context -> {
                    ServerPlayerEntity player = getPlayer(context.getSource());
                    String warpName = StringArgumentType.getString(context, "name");
                    return WarpManager.warpPlayer(player, WarpManager.getWarp(getServer(context), warpName, null));
                })
            )
        );

        dispatcher.register(CommandManager.literal("warps")
            .requires(PERMISSIONS_NORMAL)
            .executes(context -> {
                ServerPlayerEntity player = getPlayer(context.getSource());
                player.sendMessage(WarpManager.listWarps(getServer(context), null), false);
                return 1;
            }));

        dispatcher.register(CommandManager.literal("setspawn")
            .requires(PERMISSIONS_ADMIN)
            .executes(context -> {
                ServerPlayerEntity player = getPlayer(context.getSource());
                WarpManager.setWarp("spawn", player, null);

                ServerWorld world = player.getEntityWorld();
                world.setSpawnPoint(WorldProperties.SpawnPoint.create(
                    player.getEntityWorld().getRegistryKey(),
                    player.getBlockPos(),
                    0,
                    0
                ));
                world.getGameRules().setValue(GameRules.RESPAWN_RADIUS, 0, world.getServer());

                player.sendMessage(Text.literal("Spawn set!").formatted(Formatting.AQUA), false);
                return 1;
            })
        );

        dispatcher.register(CommandManager.literal("spawn")
            .requires(PERMISSIONS_NORMAL)
            .executes(context -> {
                ServerPlayerEntity player = getPlayer(context.getSource());
                return WarpManager.warpPlayer(player, WarpManager.getWarp(getServer(context), "spawn", null));
            })
        );

        dispatcher.register(CommandManager.literal("tpa")
            .then(CommandManager.argument("target", EntityArgumentType.player())
                .requires(PERMISSIONS_NORMAL)
                .suggests(suggestPlayers())
                .executes(context -> {
                    ServerPlayerEntity sender = getPlayer(context.getSource());
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");

                    if (sender.equals(target)) {
                        sender.sendMessage(
                            Text.literal("You cannot teleport to yourself!").formatted(Formatting.RED),
                            false
                        );
                        return 0;
                    }

                    sendTeleportRequest(sender, target, false);
                    return 1;
                })
            )
        );

        dispatcher.register(CommandManager.literal("tpahere")
            .then(CommandManager.argument("target", EntityArgumentType.player())
                .requires(PERMISSIONS_NORMAL)
                .suggests(suggestPlayers())
                .executes(context -> {
                    ServerPlayerEntity sender = getPlayer(context.getSource());
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");

                    if (sender.equals(target)) {
                        sender.sendMessage(
                            Text.literal("You cannot teleport to yourself!").formatted(Formatting.RED),
                            false
                        );
                        return 0;
                    }

                    sendTeleportRequest(sender, target, true);
                    return 1;
                })
            )
        );

        dispatcher.register(CommandManager.literal("tpcancel")
            .requires(PERMISSIONS_NORMAL)
            .executes(context -> {
                ServerPlayerEntity sender = getPlayer(context.getSource());
                cancelTeleportRequest(sender);
                return 1;
            })
        );

        dispatcher.register(CommandManager.literal("tpaccept")
            .requires(PERMISSIONS_NORMAL)
            .executes(context -> {
                ServerPlayerEntity receiver = getPlayer(context.getSource());
                acceptTeleportRequest(receiver, null);
                return 1;
            })
            .then(CommandManager.argument("sender", EntityArgumentType.player())
                .suggests(suggestPlayers())
                .executes(context -> {
                    ServerPlayerEntity receiver = getPlayer(context.getSource());
                    ServerPlayerEntity sender = EntityArgumentType.getPlayer(context, "sender");
                    acceptTeleportRequest(receiver, sender);
                    return 1;
                })
            )
        );

        dispatcher.register(CommandManager.literal("tpdeny")
            .requires(PERMISSIONS_NORMAL)
            .executes(context -> {
                ServerPlayerEntity receiver = getPlayer(context.getSource());
                denyTeleportRequest(receiver, null);
                return 1;
            })
            .then(CommandManager.argument("sender", EntityArgumentType.player())
                .suggests(suggestPlayers())
                .executes(context -> {
                    ServerPlayerEntity receiver = getPlayer(context.getSource());
                    ServerPlayerEntity sender = EntityArgumentType.getPlayer(context, "sender");
                    denyTeleportRequest(receiver, sender);
                    return 1;
                })
            )
        );
    }
    //endregion

    //region INITIALIZE
    // ------ INITIALIZE ----------------------------------------------------------------------------------

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> registerCommands(dispatcher)
        );

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, cause) -> {
            if (entity instanceof ServerPlayerEntity player) {
                WarpManager.setWarp("back", player, player.getUuid());
            }
        });

        ServerWorldEvents.LOAD.register((server, world) -> {
            WarpManager.createDir(server);
        });

        // Find appropriate map handler
        IMapHandler[] mapHandlers = new IMapHandler[] {
            new Pl3xMapHandler(),
            new EmptyMapHandler()
        };
        for (IMapHandler handler : mapHandlers) {
            if (handler.modPresent()) {
                mapHandler = handler;
                Constants.LOGGER.info("Using map handler for mod: {}", handler.requiredMod());
                break;
            }
        }

        // Initialize map handler on server start
        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            mapHandler.initialize(server);
        });

        Constants.LOGGER.info("Initialized!");
    }
    // endregion
}
