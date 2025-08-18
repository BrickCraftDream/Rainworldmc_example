package net.brickcraftdream.rainworldmc_example.client;

import net.brickcraftdream.rainworldmc_example.client.rendering.BoxRenderer;
import net.brickcraftdream.rainworldmc_example.networking.NetworkManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import static net.brickcraftdream.rainworldmc_example.Rainworldmc_example.ROOM_SELECTOR_ITEM;

public class Rainworldmc_exampleClient implements ClientModInitializer {
    private static BlockPos firstCorner = null;
    private static BlockPos secondCorner = null;
    private static boolean isSelectionConfirmed = false;
    private static boolean prevRight = false;
    private static boolean prevLeft = false;
    private static boolean prevCtrl = false;

    private static long ctrlPressTime = 0;
    private static final long CTRL_TIMEOUT_MS = 300;

    private static int maxTicksToIgnoreInputsAfterGuiExit = 6;
    public static int ticksSinceGuiExit = 0;

    private Screen prevScreen = null;


    /**
     * Runs the mod initializer on the client environment.
     */
    @Override
    public void onInitializeClient() {
        // Networking receivers
        ClientPlayNetworking.registerGlobalReceiver(NetworkManager.SelectedLocationPayload.ID, (payload, context) -> context.client().execute(() -> executeSelectedLocationPacket(payload, context)));
        ClientPlayNetworking.registerGlobalReceiver(NetworkManager.RemoveBoxPayload.ID, (payload, context) -> context.client().execute(() -> BoxRenderer.removeOtherPeoplesBoxesByUUID(payload.playerName())));

        // Rendering the actual shit
        WorldRenderEvents.LAST.register(this::render);


        ClientTickEvents.END_CLIENT_TICK.register(this::tickHandler);
    }

    /**
     * Handles the tick events for the client.
     * This method checks for player inputs and manages the selection of areas using the selector item.
     * @param client The Minecraft client instance.
     */
    public void tickHandler(MinecraftClient client) {
        if (client.world == null || client.player == null) return;

        Screen current = client.currentScreen;

        // detect transition: screen just closed
        if (prevScreen != null && current == null) {
            ticksSinceGuiExit = 1; // start counting ignore ticks
        }

        prevScreen = current; // update tracker

        if (current != null) return;

        if (ticksSinceGuiExit > 0) {
            ticksSinceGuiExit++;
            if (ticksSinceGuiExit > maxTicksToIgnoreInputsAfterGuiExit) {
                ticksSinceGuiExit = 0;
            }
            return;
        }

        ItemStack mainHandItem = client.player.getMainHandStack();
        if (mainHandItem.getItem() != ROOM_SELECTOR_ITEM) {
            return;
        }

        long window = client.getWindow().getHandle();
        boolean rightClick = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean leftClick = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean ctrlPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        handleAreaSelectMode(client, leftClick, rightClick, ctrlPressed);

        prevRight = rightClick;
        prevLeft = leftClick;
        prevCtrl = ctrlPressed;
    }

    /**
     * Renders the connected boxes and the selection box if applicable.
     * This method is called during the world rendering phase.
     * @param context The rendering context containing camera and matrix stack information.
     */
    public void render(WorldRenderContext context) {
        Vec3d cameraPos = context.camera().getPos();
        MatrixStack matrixStack = context.matrixStack();

        if (!BoxRenderer.locations.isEmpty()) {
            BoxRenderer.renderConnectedBoxes(matrixStack, cameraPos);
        }
        if(!BoxRenderer.otherPeoplesLocations.isEmpty()) {
            BoxRenderer.renderOtherPeoplesConnectedBoxes(matrixStack, cameraPos);
        }
        if(!isSelectionConfirmed && firstCorner != null && secondCorner != null) {
            BoxRenderer.renderBox(matrixStack, firstCorner, secondCorner, cameraPos);
            BoxRenderer.renderBlockHighlight(matrixStack, firstCorner, cameraPos, 1.0f, 0.3f, 0.3f, 0.95f);
            BoxRenderer.renderBlockHighlight(matrixStack, secondCorner, cameraPos, 0.3f, 0.3f, 1.0f, 0.95f);
        }
    }

    /**
     * Executes the selected location packet received from the server.
     * This method checks if the locations are already present in the renderer and adds them if not.
     * @param payload The payload containing the first and second positions and the player's name.
     * @param context The context of the networking event.
     */
    public void executeSelectedLocationPacket(NetworkManager.SelectedLocationPayload payload, ClientPlayNetworking.Context context) {
        if(!(BoxRenderer.firstAndSecondLocations.contains(payload.firstPos()) && BoxRenderer.firstAndSecondLocations.contains(payload.secondPos()))) {
            BoxRenderer.addOtherPeoplesBox(payload.firstPos().pos(), payload.secondPos().pos(), context.client().world, payload.playerName());
        }
    }

    /**
     * Handles the area selection mode logic.
     * This method manages the selection of corners, confirmation of selection, and actions based on player inputs.
     * @param client The Minecraft client instance.
     * @param leftClick Whether the left mouse button is pressed.
     * @param rightClick Whether the right mouse button is pressed.
     * @param ctrlPressed Whether the control key is pressed.
     */
    private void handleAreaSelectMode(MinecraftClient client, boolean leftClick, boolean rightClick, boolean ctrlPressed) {
        if (ctrlPressed && !prevCtrl) {
            ctrlPressTime = System.currentTimeMillis();
        }

        if (!ctrlPressed && prevCtrl) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - ctrlPressTime <= CTRL_TIMEOUT_MS) {
                if (firstCorner != null && secondCorner != null && !isSelectionConfirmed) {
                    confirmSelection(client);
                    return;
                }

                if (isSelectionConfirmed) {
                    doStuff();
                    return;
                }
            }
        }

        if (ctrlPressed) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - ctrlPressTime > CTRL_TIMEOUT_MS) {
                ctrlPressTime = 0;
            }
        }

        if (!ctrlPressed) {
            if (leftClick && !prevLeft) {
                setCorner(client, true);
            }

            if (rightClick && !prevRight) {
                setCorner(client, false);
            }
        }
    }

    /**
     * Sets the first or second corner of the selection based on the player's actions.
     * If the player is looking at a block, it sets the corner to that block's position.
     * If not, it sets the corner to the player's current position.
     * @param client The Minecraft client instance.
     * @param isFirstCorner Whether this is the first corner being set (true) or the second corner (false).
     */
    private void setCorner(MinecraftClient client, boolean isFirstCorner) {
        BlockPos pos = null;
        HitResult hitResult = client.crosshairTarget;

        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            pos = ((BlockHitResult) hitResult).getBlockPos();
        }
        else if (client.player != null) {
            pos = BlockPos.ofFloored(client.player.getPos());
        }

        if (pos != null) {
            if (isFirstCorner) {
                firstCorner = pos;
                client.player.sendMessage(Text.literal(
                        "First corner set at: " + firstCorner.toShortString()), true);
                isSelectionConfirmed = false;
            } else {
                secondCorner = pos;
                client.player.sendMessage(Text.literal(
                        "Second corner set at: " + secondCorner.toShortString()), true);


                // If you want you can also send a message with the dimensions of the selected area. I personally don't like it tho
                //
                //if (firstCorner != null) {
                //    int xSize = Math.abs(secondCorner.getX() - firstCorner.getX()) + 1;
                //    int ySize = Math.abs(secondCorner.getY() - firstCorner.getY()) + 1;
                //    int zSize = Math.abs(secondCorner.getZ() - firstCorner.getZ()) + 1;
                //    int volume = xSize * ySize * zSize;
                //
                //    client.player.sendMessage(Text.literal(
                //            String.format("Room dimensions: %d x %d x %d = %d blocks",
                //                    xSize, ySize, zSize, volume)), false);
                //}
            }
        }
    }

    /**
     * Confirms the selection of the area by adding a box to the renderer.
     * This method is called when the player presses ctrl after selecting both corners.
     * @param client The Minecraft client instance.
     */
    private void confirmSelection(MinecraftClient client) {
        assert client.player != null;
        BoxRenderer.addBox(firstCorner, secondCorner, client.world, client.player.getUuid());
        isSelectionConfirmed = true;
        client.player.sendMessage(Text.literal(
                "Selection confirmed. Press Ctrl again to open room selection GUI, or continue adding areas."), true);
    }

    /**
     * Method that gets called when the selection is confirmed and the player presses ctrl again
     */
    public void doStuff() {
        // Here you can implement the logic that should happen when the selection is confirmed
    }

    /**
     * Method for resetting the selection of a player.
     * @param player the {@link PlayerEntity} whose selection should be reset
     */
    public static void resetSelections(PlayerEntity player) {
        ClientPlayNetworking.send(new NetworkManager.RemoveBoxPayload(player.getUuid()));
        firstCorner = null;
        secondCorner = null;
        isSelectionConfirmed = false;
        BoxRenderer.clearBoxes();
    }
}
