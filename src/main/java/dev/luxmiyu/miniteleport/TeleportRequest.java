package dev.luxmiyu.miniteleport;

import java.util.UUID;

public record TeleportRequest(UUID sender, UUID receiver, boolean here, long expiry) {
}
