package com.unascribed.lib39.weld;

import com.unascribed.lib39.core.P39;

import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class Lib39Weld implements ModInitializer {

	private static final Identifier SILENCE_ID = Identifier.of("lib39-weld", "silence");
	
	public static final SoundEvent SILENCE = SoundEvent.createVariableRangeEvent(SILENCE_ID);
	
	@Override
	public void onInitialize() {
		Registry.register(Registries.SOUND_EVENT, SILENCE_ID, SILENCE);
	}

}
