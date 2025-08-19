package net.brickcraftdream.rainworldmc_example;

import net.brickcraftdream.rainworldmc_example.items.RoomSelectorItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.brickcraftdream.rainworldmc_example.networking.NetworkManager.*;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.GlobalPos;

import java.util.List;
import java.util.UUID;

public class Rainworldmc_example implements ModInitializer {
    public static final String MOD_ID = "rainworldmc_example";
    public static final Item SELECTOR_TOOL = new RoomSelectorItem(new Item.Settings());

    /**
     * Runs the mod initializer.
     */
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(SelectedLocationPayload.ID, SelectedLocationPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectedLocationPayload.ID, SelectedLocationPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoveBoxPayload.ID, RemoveBoxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RemoveBoxPayload.ID, RemoveBoxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoxPositionsPayload.ID, BoxPositionsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BoxPositionsPayload.ID, BoxPositionsPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RemoveBoxPayload.ID, (payload, context) -> context.server().execute(() -> syncRemoveBox(context.server(), payload.playerName())));
        ServerPlayNetworking.registerGlobalReceiver(SelectedLocationPayload.ID, (payload, context) -> context.server().execute(() -> syncSelectedLocations(context.server(), payload.firstPos(), payload.secondPos(), payload.playerName())));
        ServerPlayNetworking.registerGlobalReceiver(BoxPositionsPayload.ID, (payload, context) -> context.server().execute(() -> doStuffWithTheBoxPositions(context.server(), payload.pos())));

        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "selector_tool"), SELECTOR_TOOL);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.OPERATOR).register(entries -> entries.add(SELECTOR_TOOL));
    }

    public void syncSelectedLocations(MinecraftServer server, GlobalPos firstPos, GlobalPos secondPos, UUID playerUUID) {
        PlayerLookup.all(server).forEach(p -> ServerPlayNetworking.send(p, new SelectedLocationPayload(firstPos, secondPos, playerUUID)));
    }

    public void syncRemoveBox(MinecraftServer server, UUID playerUUID) {
        PlayerLookup.all(server).forEach(p -> ServerPlayNetworking.send(p, new RemoveBoxPayload(playerUUID)));
    }

    /**
     * Method that gets called when the server receives a BoxPositionsPayload, used to transmit the positions of all boxes selected by the player when pressing a button in a GUI for example.
     * @param server the Minecraft server instance
     * @param positions the list of GlobalPos objects representing the positions of the boxes
     */
    private void doStuffWithTheBoxPositions(MinecraftServer server, List<GlobalPos> positions) {
        // Here you would do stuff with all the positions of the boxes
    }
}
