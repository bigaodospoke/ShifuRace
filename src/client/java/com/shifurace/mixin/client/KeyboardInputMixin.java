package com.shifurace.mixin.client;

import com.shifurace.client.ClientRace;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Trava os controles durante a contagem 3, 2, 1 (ninguém sai antes do VAI nem desmonta com o shift). */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void shifurace$travarNaContagem(boolean isSneaking, float sneakingSpeedMultiplier, CallbackInfo ci) {
        if (!ClientRace.INSTANCE.getFrozen()) return;
        this.up = false;
        this.down = false;
        this.left = false;
        this.right = false;
        this.jumping = false;
        this.shiftKeyDown = false;
        this.forwardImpulse = 0f;
        this.leftImpulse = 0f;
    }
}
