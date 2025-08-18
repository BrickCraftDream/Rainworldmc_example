package net.brickcraftdream.rainworldmc_example;

import net.brickcraftdream.rainworldmc_example.items.RoomSelectorItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.brickcraftdream.rainworldmc_example.networking.NetworkManager.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.GlobalPos;

import java.util.UUID;

public class Rainworldmc_example implements ModInitializer {
    public static final String MOD_ID = "rainworldmc_example";
    public static final Item ROOM_SELECTOR_ITEM = new RoomSelectorItem(new Item.Settings());

    /**
     * Runs the mod initializer.
     */
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(SelectedLocationPayload.ID, SelectedLocationPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectedLocationPayload.ID, SelectedLocationPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoveBoxPayload.ID, RemoveBoxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RemoveBoxPayload.ID, RemoveBoxPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RemoveBoxPayload.ID, (payload, context) -> context.server().execute(() -> syncRemoveBox(context.server(), payload.playerName())));
        ServerPlayNetworking.registerGlobalReceiver(SelectedLocationPayload.ID, (payload, context) -> context.server().execute(() -> syncSelectedLocations(context.server(), payload.firstPos(), payload.secondPos(), payload.playerName())));

        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "room_selector_item"), ROOM_SELECTOR_ITEM);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.OPERATOR).register(entries -> entries.add(ROOM_SELECTOR_ITEM));
    }

    public void syncSelectedLocations(MinecraftServer server, GlobalPos firstPos, GlobalPos secondPos, UUID playerUUID) {
        PlayerLookup.all(server).forEach(p -> ServerPlayNetworking.send(p, new SelectedLocationPayload(firstPos, secondPos, playerUUID)));
    }

    public void syncRemoveBox(MinecraftServer server, UUID playerUUID) {
        PlayerLookup.all(server).forEach(p -> ServerPlayNetworking.send(p, new RemoveBoxPayload(playerUUID)));
    }
}
