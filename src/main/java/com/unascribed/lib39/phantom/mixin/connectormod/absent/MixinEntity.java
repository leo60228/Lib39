package com.unascribed.lib39.phantom.mixin.connectormod.absent;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.unascribed.lib39.phantom.quack.PhantomWorld;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class MixinEntity {

    @Shadow
    public World world;

    @Unique
    private BlockPos lib39Phantom$currentlyCollidingPos = null;

    @ModifyExpressionValue(at=@At(value="INVOKE", target="net/minecraft/util/math/BlockPos$Mutable.set(III)Lnet/minecraft/util/math/BlockPos$Mutable;", ordinal=0),
            method="updateMovementInFluid")
    public BlockPos.Mutable lib39Phantom$storeMutableForFluid(BlockPos.Mutable mut) {
        lib39Phantom$currentlyCollidingPos = mut;
        return mut;
    }

    @Inject(at=@At("RETURN"), method="updateMovementInFluid")
    public void lib39Phantom$forgetMutableForFluid(CallbackInfoReturnable<Boolean> ci) {
        lib39Phantom$currentlyCollidingPos = null;
    }

    @ModifyExpressionValue(at=@At(value="INVOKE", target="net/minecraft/world/World.getFluidState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/fluid/FluidState;", ordinal=0),
            method="updateMovementInFluid")
    public FluidState lib39Phantom$replaceFluidStateForTouch(FluidState in) {
        if (in.isEmpty()) return in;
        if (world instanceof PhantomWorld && ((PhantomWorld)world).lib39Phantom$isPhased(lib39Phantom$currentlyCollidingPos)) {
            return Fluids.EMPTY.getDefaultState();
        }
        return in;
    }
}
