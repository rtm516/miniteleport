package dev.luxmiyu.miniteleport.maphandlers;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

public interface IMapHandler {
    String requiredMod();

    default boolean modPresent() {
        return FabricLoader.getInstance().isModLoaded(requiredMod());
    }

    void initialize(MinecraftServer server);
}
