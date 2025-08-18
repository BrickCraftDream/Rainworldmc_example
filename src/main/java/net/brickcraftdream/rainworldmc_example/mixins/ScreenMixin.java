package net.brickcraftdream.rainworldmc_example.mixins;

import net.brickcraftdream.rainworldmc_example.client.Rainworldmc_exampleClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "close()V", at = @At("HEAD"))
    private void close(CallbackInfo ci) {
        Rainworldmc_exampleClient.ticksSinceGuiExit = 1;
    }
}