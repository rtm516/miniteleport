package dev.luxmiyu.miniteleport.maphandlers.pl3x;

import dev.luxmiyu.miniteleport.Constants;
import dev.luxmiyu.miniteleport.Warp;
import dev.luxmiyu.miniteleport.WarpManager;
import net.minecraft.server.MinecraftServer;
import net.pl3x.map.core.markers.layer.WorldLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Tooltip;
import net.pl3x.map.core.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class WarpsLayer extends WorldLayer {
    private final MinecraftServer server;

    public WarpsLayer(MinecraftServer server, World world) {
        super(Constants.MOD_ID + "_warps", world, () -> "Warps");
        this.server = server;
    }

    @Override
    public Collection<Marker<?>> getMarkers() {
        Warp[] warps = WarpManager.getWarps(WarpManager.getFile(server, null));
        List<Marker<?>> markers = new ArrayList<>();

        for (Warp warp : warps) {
            if (!warp.dimension().equals(getWorld().getName())) continue;

            Marker<?> marker = Marker.icon(warp.name(), warp.x(), warp.z(), Constants.MOD_ID + "_icon", 24);
            marker.setOptions(new Options.Builder()
                    .tooltipContent(warp.name())
                    .tooltipDirection(Tooltip.Direction.TOP)
                .build());
            markers.add(marker);
        }

        return markers;
    }
}
