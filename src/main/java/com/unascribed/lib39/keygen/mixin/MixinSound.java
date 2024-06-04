package com.unascribed.lib39.keygen.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.unascribed.lib39.keygen.Lib39Keygen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.Sound;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
@Mixin(Sound.class)
public class MixinSound {

	@Shadow @Final
	private Identifier id;
	
	@Inject(at=@At("HEAD"), method="getLocation", cancellable=true)
	public void lib39Keygen$getLocation(CallbackInfoReturnable<Identifier> ci) {
		if (Lib39Keygen.isModuleFile(id)) {
			ci.setReturnValue(Identifier.method_60655(id.getNamespace(), "sounds/"+id.getPath()));
		}
	}
	
}
