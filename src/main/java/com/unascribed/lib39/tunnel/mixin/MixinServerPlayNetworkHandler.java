package com.unascribed.lib39.tunnel.mixin;

import com.unascribed.lib39.tunnel.api.NetworkContext;
import net.minecraft.network.listener.AbstractServerPacketHandler;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractServerPacketHandler.class)
public class MixinServerPlayNetworkHandler {
	@Inject(at=@At("HEAD"), method="onCustomPayload", cancellable=true)
	public void onCustomPayload(CustomPayloadC2SPacket packet, CallbackInfo ci) {
		for (NetworkContext ctx : NetworkContext.contexts) {
			if (ctx.handleCustomPacket((AbstractServerPacketHandler)(Object)this, packet)) {
				ci.cancel();
				return;
			}
		}
	}
	
}
