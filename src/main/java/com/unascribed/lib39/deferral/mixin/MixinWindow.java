package com.unascribed.lib39.deferral.mixin;

import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_ANY_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.system.Platform;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.glfw.Window;
import com.unascribed.lib39.deferral.Lib39Deferral;
import com.unascribed.lib39.deferral.api.GLCompatVoter;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
@Mixin(Window.class)
public class MixinWindow {

	@Inject(at=@At(value="FIELD", target="com/mojang/blaze3d/glfw/Window.handle:J", opcode=Opcodes.PUTFIELD), method="<init>")
	private void lib39Deferral$modifyGlfwHints(CallbackInfo ci) {
		if (Platform.get() == Platform.MACOSX) {
			Lib39Deferral.log.info("Not enabling OpenGL compat profile as we are on macOS.");
			return;
		}
		Set<String> whoWantsIt = new LinkedHashSet<>();
		for (var en : FabricLoader.getInstance().getEntrypointContainers("lib39:gl_compat_voter", GLCompatVoter.class)) {
			if (en.getEntrypoint().wantsCompatibilityProfile()) {
				whoWantsIt.add(en.getProvider().getMetadata().getName());
			}
		}
		if (Boolean.getBoolean("lib39.forceCoreProfile") && !whoWantsIt.isEmpty()) {
			Lib39Deferral.log.warn("Not enabling OpenGL compat profile by your request (-Dlib39.forceCoreProfile=true), but some mods want it: {}", describe(whoWantsIt));
			return;
		}
		boolean forcedOn = Boolean.getBoolean("lib39.forceCompatProfile");
		if (!forcedOn && whoWantsIt.isEmpty()) {
			Lib39Deferral.log.info("Not enabling OpenGL compat profile as no mods want it.");
			return;
		}
		
		// Asking for any profile, version 1.0, causes GLFW to attempt to negotiate the newest possible
		// compatibility context it can. On Windows and Linux, this works as you would expect, and
		// results in a 4.6 (or whatever the newest GL the driver supports is) context with
		// ARB_compatibility available. This will not work on macOS.
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_ANY_PROFILE);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_FALSE);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 1);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 0);
		
		Lib39Deferral.didLoadCompatMode = true;
		
		if (forcedOn && whoWantsIt.isEmpty()) {
			Lib39Deferral.log.info("Enabling OpenGL compat profile on your request. (-Dlib39.forceCompatProfile=true)");
		} else {
			Lib39Deferral.log.info("Enabling OpenGL compat profile on the request of {}.", describe(whoWantsIt));
		}
	}

	@Unique
	private static String describe(Set<String> whoWantsIt) {
		if (whoWantsIt.size() == 1) {
			return Iterables.getOnlyElement(whoWantsIt);
		} else if (whoWantsIt.size() == 2) {
			return Joiner.on(" and ").join(whoWantsIt);
		} else {
			List<String> values = new ArrayList<>(whoWantsIt);
			values.set(values.size()-1, "and "+values.get(values.size()-1));
			return Joiner.on(", ").join(values);
		}
	}
	
}
