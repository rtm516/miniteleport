package dev.luxmiyu.miniteleport;

import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WarpManager {
    public static Path getDir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(Constants.MOD_ID);
    }

    public static File getFile(MinecraftServer server, @Nullable UUID uuid) {
        Path worldDir = getDir(server);
        Path path = (uuid == null) ? worldDir.resolve("warps.json") : worldDir.resolve("homes/" + uuid + ".json");
        return path.toFile();
    }

    public static void createDir(MinecraftServer server) {
        try {
            Files.createDirectories(getDir(server).resolve("homes"));
        } catch (IOException e) {
            Constants.LOGGER.error("Failed to create data directory", e);
        }
    }

    public static Warp[] getWarps(File file) {
        if (!file.exists()) return new Warp[0];

        try (FileReader reader = new FileReader(file)) {
            return Constants.GSON.fromJson(reader, Warp[].class);
        } catch (IOException e) {
            Constants.LOGGER.error("Failed to load warps from {}", file, e);
            return new Warp[0];
        }
    }

    public static @Nullable Warp getWarp(MinecraftServer server, String name, @Nullable UUID uuid) {
        for (Warp warp : getWarps(getFile(server, uuid))) {
            if (warp.name().equals(name)) return warp;
        }
        return null;
    }

    public static void writeFile(File file, Object object) {
        try {
            Files.createDirectories(file.getParentFile().toPath());

            Path tempFile = Files.createTempFile(file.getParentFile().toPath(), "tmp-", ".json");
            try (FileWriter writer = new FileWriter(tempFile.toFile())) {
                Constants.GSON.toJson(object, writer);
            }

            Files.move(
                tempFile,
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        } catch (IOException e) {
            Constants.LOGGER.error("Failed to save warps to {}", file, e);
        }
    }

    public static void setWarp(String name, ServerPlayerEntity player, @Nullable UUID uuid) {
        MinecraftServer server = player.getEntityWorld().getServer();
        ArrayList<Warp> warps = new ArrayList<>(List.of(getWarps(getFile(server, uuid))));
        String dimension = player.getEntityWorld().getRegistryKey().getValue().toString();
        Warp warp = new Warp(name, (int) Math.floor(player.getX()), (int) Math.floor(player.getY()),
            (int) Math.floor(player.getZ()), dimension);

        boolean warpExists = false;
        for (int i = 0; i < warps.size(); i++) {
            if (warps.get(i).name().equals(name)) {
                warps.set(i, warp);
                warpExists = true;
            }
        }

        if (!warpExists) {
            warps.add(warp);
        }

        CompletableFuture.runAsync(() -> writeFile(getFile(server, uuid), warps));
    }

    public static int delWarp(String name, ServerPlayerEntity player, @Nullable UUID uuid) {
        MinecraftServer server = player.getEntityWorld().getServer();
        ArrayList<Warp> warps = new ArrayList<>(List.of(getWarps(getFile(server, uuid))));

        int delIndex = -1;
        for (int i = 0; i < warps.size(); i++) {
            if (warps.get(i).name().equals(name)) {
                delIndex = i;
                break;
            }
        }

        String start = uuid == null ? "Warp '" : "Home '";

        if (delIndex == -1) {
            player.sendMessage(
                Text.literal(start + name + "' does not exist!").formatted(Formatting.RED),
                false);
            return 0;
        } else {
            warps.remove(delIndex);
            CompletableFuture.runAsync(() -> writeFile(getFile(server, uuid), warps));

            player.sendMessage(
                Text.literal(start + name + "' deleted!").formatted(Formatting.AQUA), false);
            return 1;
        }
    }

    public static void doTeleportEffect(ServerWorld world, ServerPlayerEntity player) {
        world.playSound(
            null,
            player.getBlockX() + 0.5,
            player.getBlockY() + 0.5,
            player.getBlockZ() + 0.5,
            SoundEvents.ENTITY_ENDERMAN_TELEPORT,
            SoundCategory.PLAYERS,
            1.0f,
            1.0f
        );

        world.spawnParticles(
            ParticleTypes.PORTAL,
            player.getBlockX() + 0.5,
            player.getBlockY() + 0.5,
            player.getBlockZ() + 0.5,
            25,
            0.25, 0.25, 0.25,
            0.0
        );
    }

    public static int warpPlayer(ServerPlayerEntity player, @Nullable Warp warp) {
        if (warp == null) {
            player.sendMessage(Text.literal("That warp doesn't exist!").formatted(Formatting.RED), false);
            return 0;
        }

        ServerWorld world = player.getEntityWorld().getServer()
            .getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(warp.dimension())));
        if (world == null) {
            player.sendMessage(Text.literal("That dimension doesn't exist!").formatted(Formatting.RED), false);
            return 0;
        }

        setWarp("back", player, player.getUuid());

        player.teleport(world, warp.x() + 0.5, warp.y() + 0.1, warp.z() + 0.5, EnumSet.noneOf(PositionFlag.class),
            player.getYaw(), player.getPitch(), true);

        doTeleportEffect(world, player);

        if (List.of("home", "back").contains(warp.name())) {
            player.sendMessage(
                Text.literal(String.format("Teleported %s!", warp.name())).formatted(Formatting.AQUA),
                false
            );
        } else {
            player.sendMessage(
                Text.literal(String.format("Teleported to %s!", warp.name())).formatted(Formatting.AQUA),
                false
            );
        }

        return 1;
    }

    public static Text listWarps(MinecraftServer server, @Nullable UUID uuid) {
        Warp[] warps = getWarps(getFile(server, uuid));

        if (warps.length == 0) {
            return Text.literal(uuid == null ? "There are no warps." : "You have no homes.").formatted(Formatting.RED);
        }

        MutableText text = Text.literal(uuid == null ? "Warps:" : "Homes:");
        for (Warp warp : warps) {
            text
                .append(Text.literal(" "))
                .append(Text.literal(warp.name()).formatted(Formatting.GOLD).styled(style -> style
                        .withClickEvent(new ClickEvent.RunCommand((uuid == null ? "/warp " : "/home ") + warp.name()))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Teleport to " + warp.name())))
                    )
                );
        }
        return text;
    }
}
