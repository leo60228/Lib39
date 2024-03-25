package com.unascribed.lib39.phantom.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.unascribed.lib39.phantom.quack.PhantomWorld;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

@Mixin(Entity.class)
public abstract class MixinEntity {

	@Shadow
	public boolean onGround;
	
	@Shadow
	public World world;
	
	@Shadow
	public abstract double getX();
	@Shadow
	public abstract double getEyeY();
	@Shadow
	public abstract double getZ();

	@Unique
	private BlockPos lib39Phantom$currentlyCollidingPos = null;
	
	@Inject(at=@At(value="INVOKE", target="net/minecraft/util/math/BlockPos.getX()I"),
			method="checkBlockCollision")
	public void lib39Phantom$storeMutableForBlock(CallbackInfo ci, @Local BlockPos.Mutable mut) {
		lib39Phantom$currentlyCollidingPos = mut;
	}
	
	@Inject(at=@At("RETURN"), method="checkBlockCollision")
	public void lib39Phantom$forgetMutableForBlock(CallbackInfo ci) {
		lib39Phantom$currentlyCollidingPos = null;
	}
	
	@ModifyExpressionValue(at=@At(value="INVOKE", target="net/minecraft/world/World.getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;", ordinal=0),
			method="checkBlockCollision")
	public BlockState lib39Phantom$replaceBlockState(BlockState in) {
		if (lib39Phantom$currentlyCollidingPos == null) return in;
		if (world instanceof PhantomWorld && ((PhantomWorld)world).lib39Phantom$isPhased(lib39Phantom$currentlyCollidingPos)) {
			return Blocks.VOID_AIR.getDefaultState();
		}
		return in;
	}
	
	@ModifyExpressionValue(at=@At(value="INVOKE", target="net/minecraft/world/World.getFluidState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/fluid/FluidState;", ordinal=0),
			method="updateSubmergedInWaterState")
	public FluidState lib39Phantom$replaceFluidStateForSubmerge(FluidState in) {
		if (in.isEmpty()) return in;
		if (world instanceof PhantomWorld && ((PhantomWorld)world).lib39Phantom$isPhased(BlockPos.create(getX(), getEyeY() - 0.1111111119389534, getZ()))) {
			return Fluids.EMPTY.getDefaultState();
		}
		return in;
	}
	
	// isInsideWall lambda
	@ModifyExpressionValue(at =@At(value="INVOKE", target="net/minecraft/world/World.getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;", ordinal=0),
			method={
					"m_htzprnzn(Lnet/minecraft/util/math/Box;Lnet/minecraft/util/math/BlockPos;)Z",
					"method_30022(Lnet/minecraft/util/math/Box;Lnet/minecraft/util/math/BlockPos;)Z"
			})
	public BlockState lib39Phantom$replaceBlockStateForSuffocation(BlockState in, Box box, BlockPos pos) {
		if (world instanceof PhantomWorld && ((PhantomWorld)world).lib39Phantom$isPhased(pos)) {
			return Blocks.VOID_AIR.getDefaultState();
		}
		return in;
	}
	
}
