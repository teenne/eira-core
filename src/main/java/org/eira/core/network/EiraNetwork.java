package org.eira.core.network;

import net.minecraft.world.entity.player.Player;
import org.eira.core.api.team.Team;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Cross-mod networking utilities.
 */
public interface EiraNetwork {
    
    /**
     * Register a packet type.
     */
    <T> void registerPacket(
        Class<T> packetClass,
        BiConsumer<T, Object> encoder,
        Function<Object, T> decoder,
        BiConsumer<T, Object> handler
    );
    
    /**
     * Send packet to a specific player.
     */
    <T> void sendToPlayer(Player player, T packet);
    
    /**
     * Send packet to all team members.
     */
    <T> void sendToTeam(Team team, T packet);
    
    /**
     * Send packet to all players.
     */
    <T> void sendToAll(T packet);
    
    /**
     * Send packet to server (from client).
     */
    <T> void sendToServer(T packet);
}
