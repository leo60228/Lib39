package com.unascribed.lib39.dessicant.mixin;

import java.util.List;

import net.minecraft.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.unascribed.lib39.dessicant.DessicantData;
import com.unascribed.lib39.dessicant.api.SimpleLootBlock;

import com.google.common.collect.ImmutableList;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

@Mixin(AbstractBlock.class)
public class MixinAbstractBlock {

	@Inject(at=@At("RETURN"), method="getDroppedStacks", cancellable=true)
	public void getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder, CallbackInfoReturnable<List<ItemStack>> ci) {
		if (ci.getReturnValue().isEmpty()) {
			var self = (AbstractBlock)(Object)this;
			RegistryKey<LootTable> id = self.getLootTableId();
			if (id == LootTables.EMPTY) {
				return;
			}
			var lootContext = builder.add(LootContextParameters.BLOCK_STATE, state).build(LootContextTypes.BLOCK);
			ServerWorld serverWorld = lootContext.getWorld();
			LootTable lootTable = serverWorld.getServer().method_58576().getLootTable(id);
			if (lootTable == LootTable.EMPTY && DessicantData.optedInNamespaces.contains(id.getValue().getNamespace()) && (self instanceof SimpleLootBlock || self.asItem() != Items.AIR)) {
				ItemStack loot;
				if (self instanceof SimpleLootBlock slb) {
					loot = slb.getLoot(state);
				} else {
					loot = new ItemStack(self.asItem());
				}
				ci.setReturnValue(ImmutableList.of(loot));
			}
		}
	}
	
}
