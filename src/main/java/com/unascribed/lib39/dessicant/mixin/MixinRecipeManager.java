package com.unascribed.lib39.dessicant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.gson.JsonObject;
import com.unascribed.lib39.core.Lib39Log;
import com.unascribed.lib39.dessicant.DessicantData;

import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

@Mixin(RecipeManager.class)
public class MixinRecipeManager {

	@Inject(at=@At("RETURN"), method="deserialize")
	private static void deserialize(Identifier identifier, JsonObject jsonObject, CallbackInfoReturnable<Recipe<?>> ci) {
		if (jsonObject.has("lib39:discovered_via")) {
			Identifier iid = new Identifier(jsonObject.get("lib39:discovered_via").getAsString());
			if (!Registry.ITEM.getOrEmpty(iid).isPresent()) {
				Lib39Log.warn("Recipe {} is discovered by unknown item {}", identifier, iid);
			}
			DessicantData.discoveries.put(iid, identifier);
		}
	}
	
}
