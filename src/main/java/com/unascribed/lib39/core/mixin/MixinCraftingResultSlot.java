package com.unascribed.lib39.core.mixin;

import java.util.Optional;

import net.minecraft.recipe.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.unascribed.lib39.core.Lib39Mod;
import com.unascribed.lib39.core.P39;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.slot.CraftingResultSlot;

@Mixin(CraftingResultSlot.class)
public class MixinCraftingResultSlot {

	@Shadow @Final
	private RecipeInputInventory input;
	@Shadow @Final
	private PlayerEntity player;
	
	@Inject(at=@At("HEAD"), method="onCrafted(Lnet/minecraft/item/ItemStack;)V")
	protected void lib39Core$onCrafted(ItemStack stack, CallbackInfo ci) {
		if (player == null) return;
		if (player.getWorld().isClient) return;
		Optional<RecipeHolder<CraftingRecipe>> recipe = player.getWorld().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, player.getWorld());
		if (recipe.isPresent() && Lib39Mod.craftingSounds.containsKey(recipe.get().id())) {
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), Lib39Mod.craftingSounds.get(recipe.get().id()), player.getSoundCategory(), (float) 1, (float) 1);
		}
	}
	
}
