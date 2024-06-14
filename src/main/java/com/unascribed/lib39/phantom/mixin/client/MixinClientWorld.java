package com.unascribed.lib39.phantom.mixin.client;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.unascribed.lib39.phantom.quack.PhantomWorld;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;

@Environment(EnvType.CLIENT)
@Mixin(ClientWorld.class)
public abstract class MixinClientWorld implements PhantomWorld {

	@Final
	@Shadow
	private WorldRenderer worldRenderer;
	
	@Override
	public void lib39Phantom$scheduleRenderUpdate(BlockPos pos) {
		// state arguments aren't used, so don't waste time retrieving information
		worldRenderer.updateBlock((World)(Object)this, pos, null, null, 8);
	}
	
}
