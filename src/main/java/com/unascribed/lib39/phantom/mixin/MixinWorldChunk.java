package com.unascribed.lib39.phantom.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.unascribed.lib39.phantom.quack.PhantomWorld;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

@Mixin(WorldChunk.class)
public abstract class MixinWorldChunk {

	@Shadow @Final
    World world;
	
	@Inject(at=@At("HEAD"), method="getBlockEntity(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/chunk/WorldChunk$CreationType;)Lnet/minecraft/block/entity/BlockEntity;", cancellable=true)
	public void lib39Phantom$getBlockEntity(BlockPos pos, WorldChunk.CreationType creationType, CallbackInfoReturnable<BlockEntity> ci) {
		if (world != null && world.isClient && world instanceof PhantomWorld) {
			if (((PhantomWorld)world).lib39Phantom$isPhased(((Chunk)(Object)this).getPos(), pos)) {
				ci.setReturnValue(null);
			}
		}
	}
	
	@Inject(at=@At("HEAD"), method="getBlockState", cancellable=true)
	public void lib39Phantom$getBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> ci) {
		if (world != null && world.isClient && world instanceof PhantomWorld) {
			if (((PhantomWorld)world).lib39Phantom$isPhased(((Chunk)(Object)this).getPos(), pos)) {
				ci.setReturnValue(Blocks.VOID_AIR.getDefaultState());
			}
		}
	}
	
	@Inject(at=@At("HEAD"), method="getFluidState", cancellable=true)
	public void lib39Phantom$getFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> ci) {
		if (world != null && world.isClient && world instanceof PhantomWorld) {
			if (((PhantomWorld)world).lib39Phantom$isPhased(((Chunk)(Object)this).getPos(), pos)) {
				ci.setReturnValue(Fluids.EMPTY.getDefaultState());
			}
		}
	}
	
}
