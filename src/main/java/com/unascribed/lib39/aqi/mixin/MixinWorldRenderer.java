package com.unascribed.lib39.aqi.mixin;

import net.minecraft.class_9779;
import net.minecraft.world.tick.TickManager;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.unascribed.lib39.aqi.AQIState;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderEffect;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public class MixinWorldRenderer {

	@Shadow @Final
	private MinecraftClient client;
	@Shadow @Final
	private BufferBuilderStorage bufferBuilders;
	@Shadow
	private ShaderEffect transparencyShader;
	@Shadow
	private ClientWorld world;
	
	@Inject(method="render",
			at=@At(value="CONSTANT", args="stringValue=blockentities", ordinal=0))
	private void lib39Aqi$afterEntities(class_9779 arg, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, Matrix4f matrix4f, CallbackInfo ci) {
		if (transparencyShader != null) return;
		world.getProfiler().swap("lib39-aqi:opaque_particles");
		AQIState.onlyRenderNonOpaqueParticles = false;
		AQIState.onlyRenderOpaqueParticles = true;
		TickManager tickManager = this.client.world.getTickManager();
		float tickDelta = arg.method_60637(false);
		try {
			client.particleManager.renderParticles(lightmapTextureManager, camera, tickDelta);
		} finally {
			AQIState.onlyRenderOpaqueParticles = false;
		}
	}
	
	@ModifyConstant(method="render",
		constant=@Constant(stringValue="particles", ordinal=1))
	private String lib39Aqi$beforeParticles(String orig) {
		if (transparencyShader != null) return orig;
		AQIState.onlyRenderNonOpaqueParticles = true;
		return "lib39-aqi:translucent_particles";
	}
	
	@Inject(method="render",
		at=@At(value="INVOKE", target="net/minecraft/client/particle/ParticleManager.renderParticles(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/render/LightmapTextureManager;Lnet/minecraft/client/render/Camera;F)V",
				remap=true, shift=Shift.AFTER))
	private void lib39Aqi$afterParticles(CallbackInfo ci) {
		AQIState.onlyRenderNonOpaqueParticles = false;
	}

}
