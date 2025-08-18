package net.brickcraftdream.rainworldmc_example.networking;


import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

import static net.brickcraftdream.rainworldmc_example.Rainworldmc_example.MOD_ID;


public class NetworkManager {
    public static final Identifier SELECTED_LOCATIONS_PACKET_ID = Identifier.of(MOD_ID, "selected_locations_packet");
    public static final Identifier REMOVE_BOX_PACKET_ID = Identifier.of(MOD_ID, "remove_box_packet");

    /**
     * Payload for sending selected locations to other players.
     * Contains the first and second positions, and the player's UUID.
     * @param firstPos the first position selected by the player
     * @param secondPos the second position selected by the player
     * @param playerName the UUID of the player who selected the locations
     */
    public record SelectedLocationPayload(GlobalPos firstPos, GlobalPos secondPos, UUID playerName) implements CustomPayload {
        public static final Id<SelectedLocationPayload> ID = new Id<>(SELECTED_LOCATIONS_PACKET_ID);
        public static final PacketCodec<PacketByteBuf, SelectedLocationPayload> CODEC = PacketCodec.tuple(
                GlobalPos.PACKET_CODEC, SelectedLocationPayload::firstPos,
                GlobalPos.PACKET_CODEC, SelectedLocationPayload::secondPos,
                Uuids.PACKET_CODEC, SelectedLocationPayload::playerName,
                SelectedLocationPayload::new);

        @Override
        public @NotNull Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Payload for removing a box from the world and synchronizing it with other players.
     * @param playerName the UUID of the player of whom a box should be removed
     */
    public record RemoveBoxPayload(UUID playerName) implements CustomPayload {
        public static final Id<RemoveBoxPayload> ID = new Id<>(REMOVE_BOX_PACKET_ID);
        public static final PacketCodec<PacketByteBuf, RemoveBoxPayload> CODEC = PacketCodec.tuple(
                Uuids.PACKET_CODEC, RemoveBoxPayload::playerName,
                RemoveBoxPayload::new);

        @Override
        public @NotNull Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}