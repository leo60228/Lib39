package com.unascribed.lib39.dessicant.mixin;

import java.util.List;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.unascribed.lib39.dessicant.DessicantData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
@Mixin(ItemStack.class)
public class MixinItemStack {

	@Inject(at=@At(value="INVOKE", target="Lnet/minecraft/item/Item;appendTooltip(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/Item$TooltipContext;Ljava/util/List;Lnet/minecraft/client/item/TooltipConfig;)V"),
			method="getTooltip")
	public void lib39Dessicant$getTooltip(CallbackInfoReturnable<List<Text>> cir, @Local List<Text> tooltip) {
		ItemStack self = (ItemStack)(Object)this;
		if (DessicantData.optedInNamespaces.contains(Registries.ITEM.getId(self.getItem()).getNamespace())) {
			int i = 1;
			while (I18n.hasTranslation(self.getTranslationKey()+".tip."+i)) {
				tooltip.add(Text.translatable(self.getTranslationKey()+".tip."+i));
				i++;
			}
		}
	}
	
}
