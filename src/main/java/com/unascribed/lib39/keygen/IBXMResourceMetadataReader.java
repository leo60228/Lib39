package com.unascribed.lib39.keygen;

import com.google.gson.JsonObject;
import com.unascribed.lib39.keygen.IBXMAudioStream.InterpolationMode;

import com.google.common.base.Ascii;

import net.minecraft.resource.pack.metadata.ResourceMetadataSectionReader;
import net.minecraft.util.JsonHelper;

public class IBXMResourceMetadataReader implements ResourceMetadataSectionReader<IBXMResourceMetadata> {
	@Override
	public IBXMResourceMetadata fromJson(JsonObject jsonObject) {
		return new IBXMResourceMetadata(
				jsonObject.has("mode") ? InterpolationMode.valueOf(Ascii.toUpperCase(JsonHelper.getString(jsonObject, "mode"))) : null,
				JsonHelper.getBoolean(jsonObject, "stereo", false)
			);
	}

	@Override
	public String getKey() {
		return "lib39:ibxm";
	}
	

	
}
