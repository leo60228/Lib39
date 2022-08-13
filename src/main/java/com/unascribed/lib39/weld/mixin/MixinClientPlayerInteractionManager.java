package com.unascribed.lib39.weld.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.unascribed.lib39.weld.api.BigBlock;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {

	@Shadow @Final
	private MinecraftClient client;
	
	@ModifyVariable(at=@At("HEAD"), method={
			"attackBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z",
			"updateBlockBreakingProgress(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z"
	}, argsOnly=true, ordinal=0)
	public BlockPos lib39Weld$modifyAttackPos(BlockPos pos) {
		if (client.world != null) {
			BlockState bs = client.world.getBlockState(pos);
			if (bs.getBlock() instanceof BigBlock) {
				BigBlock b = (BigBlock)bs.getBlock();
				BlockPos origin = pos.add(-b.getX(bs), -b.getY(bs), -b.getZ(bs));
				return origin;
			}
		}
		return pos;
	}
	
}
