package net.brickcraftdream.rainworldmc_example.networking;


import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static net.brickcraftdream.rainworldmc_example.Rainworldmc_example.MOD_ID;


public class NetworkManager {
    public static final Identifier SELECTED_LOCATIONS_PACKET_ID = Identifier.of(MOD_ID, "selected_locations_packet");
    public static final Identifier REMOVE_BOX_PACKET_ID = Identifier.of(MOD_ID, "remove_box_packet");
    public static final Identifier BOX_POSITIONS_PACKET_ID = Identifier.of(MOD_ID, "box_positions_packet");

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

    /**
     * Payload for sending a box of positions to other players.
     * Contains a list of first and second positions, the biome namespace, and the biome path.
     * The positions are compressed to reduce packet size.
     * In the decoder, the positions are expanded to include all blocks within the box defined by the two positions.
     * @param pos the list of positions in the box
     */
    public record BoxPositionsPayload(List<GlobalPos> pos) implements CustomPayload {
        public static final Id<BoxPositionsPayload> ID = new Id<>(BOX_POSITIONS_PACKET_ID);
        public static final PacketCodec<PacketByteBuf, List<GlobalPos>> listCodec = new PacketCodec<>() {
            @Override
            public void encode(PacketByteBuf buf, List<GlobalPos> value) {
                buf.writeInt(value.size());
                for (GlobalPos pos : value) {
                    buf.writeGlobalPos(pos);
                }
            }

            @Override
            public @NotNull List<GlobalPos> decode(PacketByteBuf buf) {
                int size = buf.readInt();
                List<GlobalPos> positions = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    positions.add(buf.readGlobalPos());
                }
                return positions;
            }
        };

        public static final PacketCodec<PacketByteBuf, BoxPositionsPayload> CODEC = new PacketCodec<>() {
            @Override
            public void encode(PacketByteBuf buf, BoxPositionsPayload payload) {
                PacketByteBuf tempBuf = new PacketByteBuf(Unpooled.buffer());

                listCodec.encode(tempBuf, payload.pos());

                byte[] rawBytes = tempBuf.array();
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                try (DeflaterOutputStream deflater = new DeflaterOutputStream(byteOut)) {
                    deflater.write(rawBytes);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to compress BoxPositionsPayload", e);
                }

                byte[] compressed = byteOut.toByteArray();
                buf.writeVarInt(compressed.length);
                buf.writeBytes(compressed);

            }

            @Override
            public BoxPositionsPayload decode(PacketByteBuf buf) {
                int length = buf.readVarInt();
                byte[] compressed = new byte[length];
                buf.readBytes(compressed);

                ByteArrayInputStream byteIn = new ByteArrayInputStream(compressed);
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                try (InflaterInputStream inflater = new InflaterInputStream(byteIn)) {
                    byte[] buffer = new byte[256];
                    int read;
                    while ((read = inflater.read(buffer)) != -1) {
                        byteOut.write(buffer, 0, read);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to decompress BoxPositionsPayload", e);
                }

                PacketByteBuf tempBuf = new PacketByteBuf(Unpooled.wrappedBuffer(byteOut.toByteArray()));
                List<GlobalPos> rawPos = listCodec.decode(tempBuf);

                List<GlobalPos> expanded = new ArrayList<>();

                for (int i = 0; i + 1 < rawPos.size(); i += 2) {
                    GlobalPos first = rawPos.get(i);
                    GlobalPos second = rawPos.get(i + 1);

                    if (!first.dimension().equals(second.dimension())) {
                        throw new IllegalStateException("Mismatched dimensions in BoxPositionsPayload: " +
                                first.dimension() + " vs " + second.dimension());
                    }

                    BlockPos pos1 = first.pos();
                    BlockPos pos2 = second.pos();

                    int minX = Math.min(pos1.getX(), pos2.getX());
                    int maxX = Math.max(pos1.getX(), pos2.getX());
                    int minY = Math.min(pos1.getY(), pos2.getY());
                    int maxY = Math.max(pos1.getY(), pos2.getY());
                    int minZ = Math.min(pos1.getZ(), pos2.getZ());
                    int maxZ = Math.max(pos1.getZ(), pos2.getZ());

                    // Generate all block positions inside the box
                    for (int x = minX; x <= maxX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                expanded.add(GlobalPos.create(first.dimension(), new BlockPos(x, y, z)));
                            }
                        }
                    }
                }

                return new BoxPositionsPayload(expanded);
            }
        };

        @Override
        public @NotNull Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}