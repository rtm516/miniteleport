package dev.luxmiyu.miniteleport.maphandlers.pl3x;

import dev.luxmiyu.miniteleport.Constants;
import dev.luxmiyu.miniteleport.maphandlers.IMapHandler;
import io.smallrye.common.constraint.NotNull;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.event.EventHandler;
import net.pl3x.map.core.event.EventListener;
import net.pl3x.map.core.event.server.ServerLoadedEvent;
import net.pl3x.map.core.event.world.WorldLoadedEvent;
import net.pl3x.map.core.image.IconImage;
import net.pl3x.map.core.world.World;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class Pl3xMapHandler implements IMapHandler, EventListener {
    private MinecraftServer server;

    public Pl3xMapHandler() {

    }

    @Override
    public String requiredMod() {
        return "pl3xmap";
    }

    @Override
    public void initialize(MinecraftServer server) {
        this.server = server;
        Pl3xMap.api().getEventRegistry().register(this);
    }

    @EventHandler
    public void onServerLoaded(@NotNull ServerLoadedEvent event) {
        try {
            Path iconPath = FabricLoader.getInstance().getModContainer(Constants.MOD_ID).get().findPath("map_marker.png").orElseThrow();
            try (InputStream stream = Files.newInputStream(iconPath)) {
                Pl3xMap.api().getIconRegistry().register(new IconImage(Constants.MOD_ID + "_icon", ImageIO.read(stream), "png"));
            }
        } catch (Exception e) {
            Constants.LOGGER.error("Failed to load map marker icon", e);
        }
    }

    @EventHandler
    public void onWorldLoaded(@NotNull WorldLoadedEvent event) {
        event.getWorld().getLayerRegistry().register(new WarpsLayer(server, event.getWorld()));
    }
}
