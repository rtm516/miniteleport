package dev.luxmiyu.miniteleport.maphandlers;

import net.minecraft.server.MinecraftServer;

public class EmptyMapHandler implements IMapHandler {
    public EmptyMapHandler() {

    }

    @Override
    public String requiredMod() {
        return "";
    }

    @Override
    public boolean modPresent() {
        return true;
    }

    @Override
    public void initialize(MinecraftServer server) {

    }
}
