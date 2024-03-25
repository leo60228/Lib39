package com.unascribed.lib39.phantom.mixin.connectormod.present;

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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinEntity {

    @Shadow
    public World world;

    @Unique
    private BlockPos lib39Phantom$currentlyCollidingPos = null;

    @ModifyExpressionValue(at=@At(value="INVOKE", target="net/minecraft/util/math/BlockPos$Mutable.set(III)Lnet/minecraft/util/math/BlockPos$Mutable;", ordinal=0),
            method="updateFluidHeightAndDoFluidPushing")
    public BlockPos.Mutable lib39Phantom$storeMutableForFluid(BlockPos.Mutable mut) {
        lib39Phantom$currentlyCollidingPos = mut;
        return mut;
    }

    @Inject(at=@At("RETURN"), method="updateFluidHeightAndDoFluidPushing")
    public void lib39Phantom$forgetMutableForFluid(CallbackInfo ci) {
        lib39Phantom$currentlyCollidingPos = null;
    }

    @ModifyExpressionValue(at=@At(value="INVOKE", target="net/minecraft/world/World.getFluidState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/fluid/FluidState;", ordinal=0),
            method="updateFluidHeightAndDoFluidPushing")
    public FluidState lib39Phantom$replaceFluidStateForTouch(FluidState in) {
        if (in.isEmpty()) return in;
        if (world instanceof PhantomWorld && ((PhantomWorld)world).lib39Phantom$isPhased(lib39Phantom$currentlyCollidingPos)) {
            return Fluids.EMPTY.getDefaultState();
        }
        return in;
    }
}
