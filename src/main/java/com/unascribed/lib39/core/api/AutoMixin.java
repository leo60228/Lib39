package com.unascribed.lib39.core.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.unascribed.lib39.core.Lib39Log;
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
	
	@Override
	public void onLoad(String pkg) {
		Lib39Log.debug("AutoMixin loaded for {}", pkg);
		this.pkg = pkg;
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
	
	protected boolean shouldAnnotationSkipMixin(String name, AnnotationNode an) {
		if (an.desc.equals("Lnet/fabricmc/api/Environment;")) {
			if (an.values == null) return false;
			for (int i = 0; i < an.values.size(); i += 2) {
				String k = (String)an.values.get(i);
				Object v = an.values.get(i+1);
				if ("value".equals(k) && v instanceof String[]) {
					String[] arr = (String[])v;
					if (arr[0].equals("Lnet/fabricmc/api/EnvType;")) {
						EnvType e = EnvType.valueOf(arr[1]);
						if (e != FabricLoader.getInstance().getEnvironmentType()) {
							Lib39Log.debug("Skipping {} mixin {}", e, name);
							return true;
						}
					}
				}
			}
		}
		return false;
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
				// Hours wasted on Quilt refactors: ||||| |
				
				Path base = null;
				try {
					// QLoader >= 0.18.1-beta.18 via api
					var modC = (Optional<ModContainer>) MethodHandles.publicLookup()
							.findVirtual(FabricLoader.class, "quilt_getModContainer", MethodType.methodType(Optional.class, Class.class))
							.invoke(FabricLoader.getInstance(), getClass());
					if (modC.isPresent()) {
						Lib39Log.debug("Discovering mixins via Quilt API (Quilt >= 0.18.1-beta.18)");
						base = modC.get().getRootPath(); // not deprecated on Quilt
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
				} else {
					try {
						// QLoader <= 0.17
						try (ZipInputStream zip = new ZipInputStream(url.openStream())) {
							Lib39Log.debug("Discovering mixins via URL ZIP iteration (Quilt <= 0.17)");
							ZipEntry en;
							while ((en = zip.getNextEntry()) != null) {
								total++;
								if (discover(rtrn, en.getName(), () -> zip)) {
									skipped++;
								}
							}
						}
					} catch (FileSystemException e) {
						Lib39Log.error("Failed to discover origin for {}", pkg);
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
		if (path.endsWith(".class") && path.startsWith(pkg.replace('.', '/')+"/")) {
			String name = path.replace('/', '.').replace(".class", "");
			// we want nothing to do with inner classes and the like
			if (name.contains("$")) return false;
			try {
				ClassReader cr = new ClassReader(opener.openStream());
				ClassNode cn = new ClassNode();
				cr.accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				if (cn.invisibleAnnotations != null) {
					for (AnnotationNode an : cn.invisibleAnnotations) {
						if (shouldAnnotationSkipMixin(name, an)) {
							return true;
						}
					}
				}
				li.add(name.substring(pkg.length()+1));
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
