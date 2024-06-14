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
import com.unascribed.lib39.core.api.util.LatchRegistryEntry;

import com.google.common.base.Ascii;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

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
	 * Scan a class {@code holdingClass} for static final fields of type {@code type}, and register them
	 * in the given ad-hoc registry.
	 */
	public <T> void autoRegister(Consumer<T> adhocRegistry, Class<?> holdingClass, Class<T> type) {
		eachRegisterableField(holdingClass, type, null, (f, v, na) -> {
			adhocRegistry.accept(v);
		});
	}

	/**
	 * Scan a class {@code holdingClass} for static final fields of type {@code type}, and register them
	 * in the configured namespace with a path equal to the field's name as lower case in the given
	 * registry.
	 */
	public <T> void autoRegister(BiConsumer<Identifier, T> registry, Class<?> holdingClass, Class<T> type) {
		eachRegisterableField(holdingClass, type, RegisteredAs.class, (f, v, ann) -> {
			registry.accept(deriveId(f, ann), v);
		});
	}

	/**
	 * Invoke the given callback for every field of the given type in the given class. If an
	 * annotation type is supplied, the annotation on the field (if any) will be passed as the
	 * third argument to the callback.
	 * <p>
	 * This is the same method used by {@link AutoRegistry#autoRegister autoRegister}, so it can be
	 * used to scan fields in holder classes for additional information in later passes.
	 */
	@SuppressWarnings("unchecked")
	public <T, A extends Annotation> void eachRegisterableField(Class<?> holdingClass, Class<T> type, Class<A> anno, TriConsumer<Field, T, A> cb) {
		for (Field f : holdingClass.getDeclaredFields()) {
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
	 * Scan a class {@code holdingClass} for static final fields of type {@code type}, and register them
	 * in the configured namespace with a path equal to the field's name as lower case in the given
	 * registry.
	 */
	@SuppressWarnings("unchecked")
	public <T> void autoRegister(Registry<T> registry, Class<?> holdingClass, Class<? super T> type) {
		eachRegisterableField(holdingClass, type, RegisteredAs.class, (f, v, ann) -> {
			Identifier id = deriveId(f, ann);
			Registry.register(registry, id, (T)v);
			assignHolder(registry, holdingClass, f.getName()+"_HOLDER", id, LatchHolder.class);
			assignHolder(registry, holdingClass, f.getName()+"_ENTRY", id, LatchRegistryEntry.class);
		});
	}

	<T> void assignHolder(Registry<T> registry, Class<?> holdingClass, String field, Identifier id, Class<? extends LatchHolder> type) {
		try {
			Field holderField = holdingClass.getDeclaredField(field);
			if (holderField.getType() == LatchHolder.class
					&& Modifier.isStatic(holderField.getModifiers()) && !Modifier.isTransient(holderField.getModifiers())) {
				((LatchHolder)holderField.get(null)).set(registry.getHolderOrThrow(RegistryKey.of(registry.getKey(), id)));
			}
		} catch (Exception e) {}
	}

	Identifier deriveId(Field f, RegisteredAs ann) {
		Identifier id;
		if (ann != null) {
			if (ann.value().contains(":")) {
				id = Identifier.tryParse(ann.value());
			} else {
				id = id(ann.value());
			}
		} else {
			id = id(Ascii.toLowerCase(f.getName()));
		}
		return id;
	}

	Identifier id(String path) {
		return Identifier.of(namespace, path);
	}

}
