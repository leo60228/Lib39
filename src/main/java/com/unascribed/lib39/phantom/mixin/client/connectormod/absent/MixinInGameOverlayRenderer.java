package com.unascribed.lib39.phantom.mixin.client.connectormod.absent;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.unascribed.lib39.phantom.quack.PhantomWorld;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
@Mixin(InGameOverlayRenderer.class)
public class MixinInGameOverlayRenderer {

	private static BlockPos lib39Phantom$currentlyCollidingPos = null;
	
	@ModifyVariable(at=@At(value="INVOKE", target="net/minecraft/util/math/BlockPos$Mutable.set(DDD)Lnet/minecraft/util/math/BlockPos$Mutable;"),
			method="getInWallBlockState", ordinal=0)
	private static BlockPos.Mutable lib39Phantom$storeMutable(BlockPos.Mutable mut) {
		lib39Phantom$currentlyCollidingPos = mut;
		return mut;
	}
	
	@Inject(at=@At("RETURN"), method="getInWallBlockState")
	private static void lib39Phantom$forgetMutable(PlayerEntity entity, CallbackInfoReturnable<BlockState> ci) {
		lib39Phantom$currentlyCollidingPos = null;
	}
	
	@ModifyExpressionValue(at=@At(value="INVOKE", target="net/minecraft/world/World.getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;", ordinal = 0),
			method="getInWallBlockState")
	private static BlockState lib39Phantom$replaceBlockState(BlockState in) {
		if (lib39Phantom$currentlyCollidingPos == null) return in;
		World world = MinecraftClient.getInstance().world;
		if (world instanceof PhantomWorld && ((PhantomWorld)world).lib39Phantom$isPhased(lib39Phantom$currentlyCollidingPos)) {
			return Blocks.VOID_AIR.getDefaultState();
		}
		return in;
	}
	
}
