package com.unascribed.lib39.core.api;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.logging.log4j.util.TriConsumer;

import com.unascribed.lib39.core.api.util.LatchHolder;

import com.google.common.base.Ascii;

import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;

/**
 * Utilities for automatically discovering and registering things in classes.
 */
public final class AutoRegistry {

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface RegisteredAs {
		String value();
	}

	/**
	 * Construct an AutoRegistry for the given namespace.
	 */
	public static AutoRegistry of(String namespace) {
		return new AutoRegistry(namespace);
	}
	
	private final String namespace;
	
	private AutoRegistry(String namespace) {
		this.namespace = namespace;
	}
	
	/**
	 * Invoke the given callback for every field of the given type in the given class. If an
	 * annotation type is supplied, the annotation on the field (if any) will be passed as the
	 * third argument to the callback.
	 * <p>
	 * This is the same method used by {@link #autoRegister}, so it can be used to scan fields in
	 * holder classes for additional information in later passes.
	 */
	@SuppressWarnings("unchecked")
	public <T, A extends Annotation> void eachRegisterableField(Class<?> holder, Class<T> type, Class<A> anno, TriConsumer<Field, T, A> cb) {
		for (Field f : holder.getDeclaredFields()) {
			if (type.isAssignableFrom(f.getType()) && Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers())) {
				try {
					f.setAccessible(true);
					cb.accept(f, (T)f.get(null), anno == null ? null : f.getAnnotation(anno));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	/**
	 * Scan a class {@code holder} for static final fields of type {@code type}, and register them
	 * in the configured namespace with a path equal to the field's name as lower case in the given
	 * registry.
	 */
	@SuppressWarnings("unchecked")
	public <T> void autoRegister(Registry<T> registry, Class<?> holder, Class<? super T> type) {
		eachRegisterableField(holder, type, RegisteredAs.class, (f, v, ann) -> {
			Identifier id = deriveId(f, ann);
			Registry.register(registry, id, (T)v);
			try {
				Field holderField = holder.getDeclaredField(f.getName()+"_HOLDER");
				if (holderField.getType() == LatchHolder.class
						&& Modifier.isStatic(holderField.getModifiers()) && !Modifier.isTransient(holderField.getModifiers())) {
					((LatchHolder)holderField.get(null)).set(registry.getOrCreateHolder(RegistryKey.of(registry.getKey(), id)).getOrThrow(false, s -> {}));
				}
			} catch (Exception e) {}
			try {
				// for Yarn users (they call this RegistryEntry)
				Field entryField = holder.getDeclaredField(f.getName()+"_ENTRY");
				if (entryField.getType() == LatchHolder.class
						&& Modifier.isStatic(entryField.getModifiers()) && !Modifier.isTransient(entryField.getModifiers())) {
					((LatchHolder)entryField.get(null)).set(registry.getOrCreateHolder(RegistryKey.of(registry.getKey(), id)).getOrThrow(false, s -> {}));
				}
			} catch (Exception e) {}
		});
	}

	/**
	 * Scan a class {@code holder} for static final fields of type {@code type}, and register them
	 * in the given ad-hoc registry.
	 */
	public <T> void autoRegister(Consumer<T> adhocRegistry, Class<?> holder, Class<T> type) {
		eachRegisterableField(holder, type, null, (f, v, na) -> {
			adhocRegistry.accept(v);
		});
	}

	/**
	 * Scan a class {@code holder} for static final fields of type {@code type}, and register them
	 * in the configured namespace with a path equal to the field's name as lower case in the given
	 * registry.
	 */
	public <T> void autoRegister(BiConsumer<Identifier, T> registry, Class<?> holder, Class<T> type) {
		eachRegisterableField(holder, type, RegisteredAs.class, (f, v, ann) -> {
			registry.accept(deriveId(f, ann), v);
		});
	}

	private Identifier deriveId(Field f, RegisteredAs ann) {
		Identifier id;
		if (ann != null) {
			if (ann.value().contains(":")) {
				id = new Identifier(ann.value());
			} else {
				id = id(ann.value());
			}
		} else {
			id = id(Ascii.toLowerCase(f.getName()));
		}
		return id;
	}
	
	private Identifier id(String path) {
		return new Identifier(namespace, path);
	}

}
