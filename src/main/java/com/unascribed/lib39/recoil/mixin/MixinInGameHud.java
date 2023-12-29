package com.unascribed.lib39.recoil.mixin;

import com.unascribed.lib39.recoil.api.RecoilEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.hud.in_game.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public class MixinInGameHud {

	@Inject(at=@At("HEAD"), method="renderCrosshair", cancellable=true)
	private void lib39recoil$renderCrosshair(GuiGraphics state, CallbackInfo ci) {
		if (RecoilEvents.RENDER_CROSSHAIRS.invoker().onRenderCrosshairs(state)) {
			ci.cancel();
		}
	}
	
}
