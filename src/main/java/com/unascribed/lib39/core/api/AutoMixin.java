package com.unascribed.lib39.core.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.unascribed.lib39.core.Lib39Log;
import com.unascribed.lib39.core.mixinsupport.AutoMixinEligible;

import com.google.common.collect.Lists;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Extend this class and register it as your mixin plugin to autodiscover mixins inside your mixin
 * package in your jar.
 */
public class AutoMixin implements IMixinConfigPlugin {

	private String pkg;
	private String binaryPkgPrefix;
	
	@Override
	public void onLoad(String pkg) {
		Lib39Log.debug("AutoMixin loaded for {}", pkg);
		this.pkg = pkg;
		this.binaryPkgPrefix = pkg.replace('.', '/')+"/";
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
		
	}
	
	/**
	 * Retrieve a config value with the given key, whatever that may mean. Used by
	 * {@link AutoMixinEligible#ifConfigSet()} and {@link AutoMixinEligible#unlessConfigSet()};
	 * <p>
	 * This is an extension point for AutoMixin consumers.
	 * @implNote The default implementation throws an {@code AbstractMethodError}.
	 */
	protected boolean getConfigValue(String key) {
		throw new AbstractMethodError("ifConfigSet or unlessConfigSet was used, but "+getClass().getName()+" does not override getConfigValue!");
	}
	
	/**
	 * Query if the given annotation describes a situation in which the given mixin class should be
	 * skipped.
	 * <p>
	 * This is an extension point for AutoMixin consumers.
	 * @param name the mixin being checked
	 * @param an the annotation node being queried
	 * @return {@code true} to skip the mixin
	 */
	protected boolean shouldAnnotationSkipMixin(String name, AnnotationNode an) {
		if (an.desc.equals("Lnet/fabricmc/api/Environment;")) {
			var decoded = decodeAnnotationParams(an);
			if (decoded.get("value") != FabricLoader.getInstance().getEnvironmentType()) {
				Lib39Log.debug("Skipping @Environment({}) mixin {}", decoded.get("value"), name);
				return true;
			}
		}
		if (an.desc.equals("Lorg/quiltmc/loader/api/minecraft/ClientOnly;")) {
			if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
				Lib39Log.debug("Skipping @ClientOnly mixin {}", name);
				return true;
			}
		}
		if (an.desc.equals("Lorg/quiltmc/loader/api/minecraft/DedicatedServerOnly;")) {
			if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) {
				Lib39Log.debug("Skipping @DedicatedServerOnly mixin {}", name);
				return true;
			}
		}
		if (an.desc.equals("Lcom/unascribed/lib39/core/mixinsupport/AutoMixinEligible;")) {
			var decoded = decodeAnnotationParams(an);
			if (checkIfList(decoded.get("ifModPresent"), FabricLoader.getInstance()::isModLoaded,
					"Skipping mixin "+name+" as required mod {} is not loaded")) {
				return true;
			}
			if (checkUnlessList(decoded.get("unlessModPresent"), FabricLoader.getInstance()::isModLoaded,
					"Skipping mixin "+name+" as incompatible mod {} is loaded")) {
				return true;
			}
			if (checkIfList(decoded.get("ifSystemProperty"), Boolean::getBoolean,
					"Skipping mixin "+name+" as system property {} is unset or false")) {
				return true;
			}
			if (checkUnlessList(decoded.get("unlessSystemProperty"), Boolean::getBoolean,
					"Skipping mixin "+name+" as system property {} is true")) {
				return true;
			}
			if (checkIfList(decoded.get("ifConfigSet"), this::getConfigValue,
					"Skipping mixin "+name+" as config option {} is false")) {
				return true;
			}
			if (checkUnlessList(decoded.get("unlessConfigSet"), this::getConfigValue,
					"Skipping mixin "+name+" as config option {} is true")) {
				return true;
			}
			if (decoded.containsKey("inEnvType")
					&& decoded.get("inEnvType") != FabricLoader.getInstance().getEnvironmentType()) {
				Lib39Log.debug("Skipping {} mixin {}", decoded.get("inEnvType"), name);
				return true;
			}
			if (decoded.containsKey("inRunType")
					&& decoded.get("inRunType") != RunType.getCurrent()) {
				Lib39Log.debug("Skipping {} mixin {}", decoded.get("inRunType"), name);
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Returns {@code true} if the given mixin should be skipped for any reason.
	 * <p>
	 * Override point for AutoMixin consumers.
	 * @implNode The default implementation checks the ClassNode's annotations with {@link #shouldAnnotationSkipMixin}.
	 */
	protected boolean shouldMixinBeSkipped(String name, ClassNode node) {
		if (checkAnnotations(name, node.invisibleAnnotations)) {
			return true;
		}
		if (checkAnnotations(name, node.visibleAnnotations)) {
			return true;
		}
		return false;
	}

	private boolean checkAnnotations(String name, List<AnnotationNode> annotations) {
		if (annotations != null) {
			for (AnnotationNode an : annotations) {
				if (shouldAnnotationSkipMixin(name, an)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Iterate over each value in {@code list}, returning {@code true} and logging a message with
	 * {@code template} if {@code checker} returns {@code false} for any value.
	 * <p>
	 * The first and only log argument will be the value that was checked. This is a convenience
	 * method for checking "ifX" properties in AutoMixinEligible, but is protected for usage by
	 * consumers that define similar annotations.
	 */
	protected static boolean checkIfList(Object list, Predicate<String> checker, String template) {
		return _checkList(true, list, checker, template);
	}

	/**
	 * Iterate over each value in {@code list}, returning {@code true} and logging a message with
	 * {@code template} if {@code checker} returns {@code true} for any value.
	 * <p>
	 * The first and only log argument will be the value that was checked. This is a convenience
	 * method for checking "unlessX" properties in AutoMixinEligible, but is protected for usage
	 * by consumers that define similar annotations.
	 */
	protected static boolean checkUnlessList(Object list, Predicate<String> checker, String template) {
		return _checkList(false, list, checker, template);
	}
	
	private static boolean _checkList(boolean expect, Object list, Predicate<String> checker, String template) {
		if (list instanceof List<?> li) {
			for (var str : li) {
				if (checker.test(String.valueOf(str)) != expect) {
					Lib39Log.debug(template, str);
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Decode ASM's tangled annotation value format into a sensible Map, automatically deserializing
	 * enums and expanding lists.
	 * <p>
	 * This is a utility method for working with AnnotationNodes, and is protected for usage by
	 * consumers that are overriding {@link #shouldAnnotationSkipMixin(String, AnnotationNode)}.
	 * <p>
	 * <b>Classes may be looked up based on the contents of the AnnotationNode.</b> Only call with
	 * trusted data.
	 */
	protected static Map<String, Object> decodeAnnotationParams(AnnotationNode an) {
		Map<String, Object> out = new HashMap<>();
		for (int i = 0; i < an.values.size(); i += 2) {
			String k = (String)an.values.get(i);
			Object v = decodeAnnotationValue(an.values.get(i+1));
			if (v != null) out.put(k, v);
		}
		return out;
	}

	/**
	 * Decode ASM's tangled annotation value format into a sensible value for a Map entry.
	 * <p>
	 * This is a utility method for working with AnnotationNodes, and is protected for usage by
	 * consumers that are overriding {@link #shouldAnnotationSkipMixin(String, AnnotationNode)}.
	 * <p>
	 * <b>Classes may be looked up based on the contents of the value.</b> Only call with trusted
	 * data.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected static Object decodeAnnotationValue(Object v) {
		// this data format is absolutely absurd
		if (v instanceof String[] arr) {
			v = null;
			try {
				var type = arr[0];
				if (type.startsWith("L") && type.endsWith(";")) {
					Class<?> clazz = Class.forName(arr[0].substring(1, arr[0].length()-1).replace('/', '.'));
					if (Enum.class.isAssignableFrom(clazz)) {
						v = Enum.valueOf((Class<Enum>) clazz, arr[1]);
					}
				}
			} catch (ClassNotFoundException ignore) {}
			return v;
		}
		if (v instanceof List<?> l) {
			return l.stream()
					.map(AutoMixin::decodeAnnotationValue)
					.toList();
		}
		if (v instanceof AnnotationNode an) {
			return decodeAnnotationParams(an);
		}
		return v;
	}

	@Override
	public List<String> getMixins() {
		List<String> rtrn = Lists.newArrayList();
		int total = 0;
		int skipped = 0;
		try {
			URL url = getJarURL(getClass().getProtectionDomain().getCodeSource().getLocation());
			Lib39Log.debug("Jar URL appears to be {}", url);
			if ("file".equals(url.getProtocol())) {
				File f = new File(url.toURI());
				if (f.isDirectory()) {
					// Q/F dev environment
					Path base = f.toPath();
					try (var stream = Files.walk(base)) {
						Lib39Log.debug("Discovering mixins via directory iteration (Quilt/Fabric dev environment)");
						for (Path p : (Iterable<Path>)stream::iterator) {
							total++;
							if (discover(rtrn, base.relativize(p).toString(), () -> Files.newInputStream(p))) {
								skipped++;
							}
						}
					}
				} else {
					// FLoader, old QLoader
					try (ZipFile zip = new ZipFile(f)) {
						Lib39Log.debug("Discovering mixins via direct ZIP iteration (Fabric or old Quilt)");
						for (var en : Collections.list(zip.entries())) {
							total++;
							if (discover(rtrn, en.getName(), () -> zip.getInputStream(en))) {
								skipped++;
							}
						}
					}
				}
			} else {
				// Hours wasted on Quilt refactors: ||||| ||
				try {
					// QLoader <= 0.17
					try (ZipInputStream zip = new ZipInputStream(url.openStream())) {
						ZipEntry en;
						while ((en = zip.getNextEntry()) != null) {
							if (total == 0) {
								Lib39Log.debug("Discovering mixins via URL ZIP iteration (Quilt <= 0.17)");
							}
							total++;
							if (discover(rtrn, en.getName(), () -> zip)) {
								skipped++;
							}
						}
					}
				} catch (Exception e) {}
				if (total == 0) {
					Path base = null;
					try {
						// QLoader >= 0.18.1-beta.18 via api
						var modC = (Optional<ModContainer>) MethodHandles.publicLookup()
								.findVirtual(FabricLoader.class, "quilt_getModContainer", MethodType.methodType(Optional.class, Class.class))
								.invoke(FabricLoader.getInstance(), getClass());
						if (modC.isPresent()) {
							Lib39Log.debug("Discovering mixins via Quilt API (Quilt >= 0.18.1-beta.18)");
							@SuppressWarnings("deprecation") // not deprecated on Quilt
							var baseTmp = modC.get().getRootPath();
							base = baseTmp;
						}
					} catch (NoSuchMethodException nsme) {
						// QLoader 0.18.1-beta before 18 (remove once that's certainly dead)
						var qmfsp = Class.forName("org.quiltmc.loader.impl.filesystem.QuiltMemoryFileSystemProvider");
						var lu = MethodHandles.privateLookupIn(qmfsp, MethodHandles.lookup());
						FileSystemProvider fsp = (FileSystemProvider) lu.findStatic(qmfsp, "instance", MethodType.methodType(qmfsp)).invoke();
						Lib39Log.debug("Discovering mixins via Quilt internals (Quilt 0.18.1-beta)");
						base = fsp.getPath(url.toURI());
					}
					if (base != null) {
						total++;
						for (Path p : (Iterable<Path>)Files.walk(base)::iterator) {
							total++;
							if (discover(rtrn, base.relativize(p).toString(), () -> Files.newInputStream(p))) {
								skipped++;
							}
						}
					}
				}
			}
		} catch (URISyntaxException e) {
			throw new AssertionError(e);
		} catch (Throwable e) {
			throw new RuntimeException("Cannot autodiscover mixins for "+pkg, e);
		}
		if (rtrn.isEmpty()) {
			Lib39Log.warn("Found no mixins in {}", pkg);
		} else {
			Lib39Log.debug("Discovered {} mixins in {} (skipped {}, found {} total files)", rtrn.size(), pkg, skipped, total);
		}
		return rtrn;
	}

	private interface StreamOpener {
		InputStream openStream() throws IOException;
	}
	
	private boolean discover(List<String> li, String path, StreamOpener opener) throws IOException {
		path = path.replace('\\', '/'); // fuck windows
		if (path.endsWith(".class") && path.startsWith(binaryPkgPrefix)) {
			String name = path.replace('/', '.').replace(".class", "");
			// we want nothing to do with inner classes and the like
			if (name.contains("$")) return false;
			try {
				ClassReader cr = new ClassReader(opener.openStream());
				ClassNode cn = new ClassNode();
				cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				if (shouldMixinBeSkipped(name, cn))
					return true;
				li.add(name.substring(binaryPkgPrefix.length()));
			} catch (IOException e) {
				Lib39Log.warn("Exception while trying to read {}", name, e);
			}
		}
		return false;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		
	}
	 
	private static URL getJarURL(URL codeSource) {
		if ("jar".equals(codeSource.getProtocol())) {
			String str = codeSource.toString().substring(4);
			int bang = str.indexOf('!');
			if (bang != -1) str = str.substring(0, bang);
			try {
				return new URL(str);
			} catch (MalformedURLException e) {
				return null;
			}
		} else if ("union".equals(codeSource.getProtocol())) {
			// some ModLauncher nonsense
			String str = codeSource.toString().substring(6);
			int bullshit = str.indexOf("%23");
			if (bullshit != -1) str = str.substring(0, bullshit);
			try {
				return new URL("file:"+str);
			} catch (MalformedURLException e) {
				return null;
			}
		}
		return codeSource;
	}


}
