package com.unascribed.lib39.tunnel.mixin;

import net.minecraft.client.network.AbstractClientNetworkHandler;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.unascribed.lib39.tunnel.api.NetworkContext;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@Mixin(AbstractClientNetworkHandler.class)
public class MixinClientPlayNetworkHandler {
	@Inject(at=@At("HEAD"), method="onCustomPayload", cancellable=true)
	public void onCustomPayload(CustomPayloadS2CPacket packet, CallbackInfo ci) {
		for (NetworkContext ctx : NetworkContext.contexts) {
			if (ctx.handleCustomPacket((AbstractClientNetworkHandler)(Object)this, packet)) {
				ci.cancel();
				return;
			}
		}
	}

}
