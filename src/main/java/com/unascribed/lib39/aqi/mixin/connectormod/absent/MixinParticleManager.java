package com.unascribed.lib39.aqi.mixin.connectormod.absent;

import java.util.Iterator;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.unascribed.lib39.aqi.AQIState;
import com.google.common.collect.Iterators;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;

@Environment(EnvType.CLIENT)
@Mixin(ParticleManager.class)
public class MixinParticleManager {
	
	@ModifyExpressionValue(at=@At(value="INVOKE", target="java/util/List.iterator()Ljava/util/Iterator;"), method="renderParticles")
	public Iterator<ParticleTextureSheet> filterIterator(Iterator<ParticleTextureSheet> orig) {
		if (AQIState.onlyRenderNonOpaqueParticles) {
			return Iterators.filter(orig, pts -> pts != ParticleTextureSheet.PARTICLE_SHEET_OPAQUE);
		}
		if (AQIState.onlyRenderOpaqueParticles) {
			return Iterators.filter(orig, pts -> pts == ParticleTextureSheet.PARTICLE_SHEET_OPAQUE);
		}
		return orig;
	}
	
}
