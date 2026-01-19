package org.eira.core.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.eira.core.EiraCore;
import org.eira.core.api.team.Team;
import org.eira.core.network.EiraNetwork;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Network implementation for local packet handling between Eira mods.
 *
 * <p>Uses NeoForge's payload system for client-server communication
 * within the same Minecraft instance.
 */
public class EiraNetworkImpl implements EiraNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static final Gson GSON = new GsonBuilder().create();

    // Registry of custom packet handlers
    private final Map<Class<?>, PacketHandler<?>> handlers = new ConcurrentHashMap<>();

    /**
     * Register network payloads with NeoForge.
     */
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(EiraCore.MOD_ID)
            .versioned(PROTOCOL_VERSION);

        // Register cross-mod payload
        registrar.playBidirectional(
            CrossModPayload.TYPE,
            CrossModPayload.STREAM_CODEC,
            this::handleCrossModPayload
        );

        EiraCore.LOGGER.info("Eira Core network payloads registered");
    }

    // ==================== EiraNetwork Interface ====================

    @Override
    public <T> void registerPacket(
            Class<T> packetClass,
            BiConsumer<T, Object> encoder,
            Function<Object, T> decoder,
            BiConsumer<T, Object> handler
    ) {
        handlers.put(packetClass, new PacketHandler<>(encoder, decoder, handler));
        EiraCore.LOGGER.debug("Registered packet type: {}", packetClass.getSimpleName());
    }

    @Override
    public <T> void sendToPlayer(Player player, T packet) {
        if (player instanceof ServerPlayer serverPlayer) {
            CrossModPayload payload = wrapPacket(packet);
            PacketDistributor.sendToPlayer(serverPlayer, payload);
        }
    }

    @Override
    public <T> void sendToTeam(Team team, T packet) {
        CrossModPayload payload = wrapPacket(packet);
        for (Player player : team.getOnlineMembers()) {
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, payload);
            }
        }
    }

    @Override
    public <T> void sendToAll(T packet) {
        CrossModPayload payload = wrapPacket(packet);
        PacketDistributor.sendToAllPlayers(payload);
    }

    @Override
    public <T> void sendToServer(T packet) {
        CrossModPayload payload = wrapPacket(packet);
        PacketDistributor.sendToServer(payload);
    }

    // ==================== Internal Methods ====================

    private <T> CrossModPayload wrapPacket(T packet) {
        String className = packet.getClass().getName();
        String data = GSON.toJson(packet);
        return new CrossModPayload(className, data);
    }

    private void handleCrossModPayload(CrossModPayload payload, IPayloadContext context) {
        try {
            Class<?> packetClass = Class.forName(payload.packetClass());
            @SuppressWarnings("unchecked")
            PacketHandler<Object> handler = (PacketHandler<Object>) handlers.get(packetClass);

            if (handler != null) {
                Object packet = GSON.fromJson(payload.data(), packetClass);
                context.enqueueWork(() -> handler.handler().accept(packet, context));
            } else {
                EiraCore.LOGGER.debug("No handler for packet class: {}", payload.packetClass());
            }
        } catch (ClassNotFoundException e) {
            EiraCore.LOGGER.warn("Unknown packet class: {}", payload.packetClass());
        } catch (Exception e) {
            EiraCore.LOGGER.error("Error handling cross-mod payload: {}", e.getMessage());
        }
    }

    // ==================== Payload Definition ====================

    /**
     * Cross-mod payload for arbitrary packet types.
     */
    public record CrossModPayload(String packetClass, String data) implements CustomPacketPayload {

        public static final Type<CrossModPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EiraCore.MOD_ID, "cross_mod")
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, CrossModPayload> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, CrossModPayload::packetClass,
                ByteBufCodecs.STRING_UTF8, CrossModPayload::data,
                CrossModPayload::new
            );

        @Override
        public Type<CrossModPayload> type() {
            return TYPE;
        }
    }

    // ==================== Helper Record ====================

    private record PacketHandler<T>(
        BiConsumer<T, Object> encoder,
        Function<Object, T> decoder,
        BiConsumer<T, Object> handler
    ) {}
}
