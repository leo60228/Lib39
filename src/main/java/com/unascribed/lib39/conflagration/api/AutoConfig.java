package com.unascribed.lib39.conflagration.api;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.unascribed.lib39.conflagration.Lib39ConflagrationInit;
import com.unascribed.lib39.conflagration.api.qdcss.QDCSS;
import com.unascribed.lib39.core.Lib39Log;
import com.google.common.base.Ascii;
import com.google.common.base.Enums;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public abstract class AutoConfig {
 
	/**
	 * Tags an AutoConfig inner class as being a section.
	 */
	@Documented
	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Section {
		/**
		 * The key of this section. Used for translation keys and in the config file. Inside the
		 * config file, _ will be replaced with - to translate Minecraft conventions to CSS.
		 * <p>
		 * The translation key used for a section is {@code config.NAMESPACE.KEY}. The
		 * translation key for the description is {@code config.NAMESPACE.KEY.desc}.
		 */
		String key();
	}
	
	/**
	 * Describes essential information about an AutoConfig section's fields.
	 */
	@Documented
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Option {
		/**
		 * The key of this option. Used for translation keys and in the config file. Inside the
		 * config file, _ will be replaced with - to translate Minecraft conventions to CSS.
		 * <p>
		 * The translation key used for an option name is {@code config.NAMESPACE.KEY}. The
		 * translation key for the description is {@code config.NAMESPACE.KEY.desc}.
		 */
		String key();
		/**
		 * The marshaller used for this option.
		 * <p>
		 * You can specify AbstractEnumType if you also annotate the field with {@link EnumType}, as
		 * well as AbstractDoubleType or AbstractIntType if you also annotate the field with
		 * {@link Range}. This is implemented via a {@code createImplicit} static method on the
		 * Abstract* classes, and can be used by custom value types.
		 * @see StringType
		 * @see BooleanType
		 * @see AbstractEnumType
		 * @see AbstractDoubleType
		 * @see AbstractIntType
		 */
		Class<? extends ConfigValueType<?, ?>> type();
		/**
		 * The default value for this option. Will be passed to {@link ConfigValueType#unmarshal(String)}.
		 */
		String def() default "";
	}
	
	/**
	 * Specifies an enum class to use for implicit creation of {@link AbstractEnumType}. The
	 * translation key will default to config.NAMESPACE.LOWER_SIMPLE_NAME. For example, if the
	 * namespace is "yttr" and the enum class name is "TrileanSoft", the key will be
	 * "config.yttr.trileansoft" — therefore, the values will be "config.yttr.trileansoft.off",
	 * "config.yttr.trileansoft.soft", and "config.yttr.trileansoft.on".
	 */
	@Documented
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface EnumType {
		Class<? extends Enum<?>> value();
	}
	
	/**
	 * Specifies a range to use for implicit creation of {@link AbstractIntType} and
	 * {@link AbstractDoubleType}.
	 */
	@Documented
	@Target(ElementType.FIELD)
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Range {
		double min();
		double max();
	}
	
	private record Registration(String namespace, String path, Class<? extends AutoConfig> clazz, ImmutableMap<String, SectionDef> sections) {}
	private record SectionDef(String key, ImmutableMap<String, OptionDef> options) {}
	private record OptionDef(String key, ConfigValueType<?, ?> type, String def, Field field) {}
	
	private record ConfigValue<T>(ConfigValueType<T, ?> type, T value) {
		String marshal() { return type().marshal(value()); }
	}
	
	public enum LoadResult {
		/**
		 * The file does not exist. All options adopted default values.
		 */
		MISSING,
		/**
		 * The file exists, but some keys were missing. Those options adopted default values.
		 */
		INCOMPLETE,
		/**
		 * The file exists and all keys were found.
		 */
		NORMAL,
		;
	}
	
	public static final Map<String, Registration> configsByPath = new HashMap<>();
	public static final Map<Class<? extends AutoConfig>, Registration> configsByClass = new HashMap<>();
	
	private static final Executor SAVE_THREAD = Executors.newSingleThreadExecutor(r -> {
		var t = new Thread(r, "Lib39 Conflagration save thread");
		t.setDaemon(true);
		return t;
	});
	
	/**
	 * Register an autoconfig for the given path. You should call this in your static initializer,
	 * to ensure your config is set up no matter how early it's first used.
	 * <p>
	 * The config will be immediately loaded, and if the file does not exist or is incomplete, will
	 * be written back to disk asynchronously.
	 * <p>
	 * If the config is loaded before translations are available, it will be written with no comments
	 * and later re-written once translations are available.
	 * @param path the path, relative to the game directory, for this autoconfig - e.g. config/yttr.css
	 * @param cfg the autoconfig class to register
	 */
	public static void register(String namespace, String path, Class<? extends AutoConfig> cfg) {
		Map<String, SectionDef> sections = new LinkedHashMap<>();
		for (Class<?> clazz : cfg.getDeclaredClasses()) {
			Section s = clazz.getAnnotation(Section.class);
			if (s != null) {
				Map<String, OptionDef> options = new HashMap<>();
				for (Field f : clazz.getDeclaredFields()) {
					Option o = f.getAnnotation(Option.class);
					if (o != null) {
						String key = o.key();
						String def = o.def();
						Class<? extends ConfigValueType<?, ?>> typeClass = o.type();
						ConfigValueType<?, ?> type;
						try {
							var cons = typeClass.getConstructor();
							type = cons.newInstance();
						} catch (NoSuchMethodException e) {
							try {
								var m = typeClass.getMethod("createImplicit", String.class, Field.class);
								type = (ConfigValueType<?, ?>)m.invoke(null, namespace, f);
							} catch (NoSuchMethodException e2) {
								throw new IllegalArgumentException(typeClass+" does not define a no-args constructor or createImplicit(String, Field)");
							} catch (Exception e2) {
								throw new IllegalArgumentException(e2);
							}
						} catch (Exception e) {
							throw new IllegalArgumentException(e);
						}
						
					}
				}
				sections.put(s.key(), new SectionDef(s.key(), ImmutableMap.copyOf(options)));
			}
		}
		var reg = new Registration(namespace, path, cfg, ImmutableMap.copyOf(sections));
		configsByPath.put(path, reg);
		configsByClass.put(cfg, reg);
		var res = load(cfg);
		if (res == LoadResult.MISSING || (res == LoadResult.NORMAL && Lib39ConflagrationInit.isInitialized)) {
			saveAsync(cfg);
		}
		if (res != LoadResult.NORMAL) {
			// potentially subject to a race condition but i don't think it matters in practice
			if (!Lib39ConflagrationInit.isInitialized) {
				Lib39ConflagrationInit.initTasks.add(() -> saveAsync(cfg));
			}
		}
	}
	
	private static Map<String, ConfigValue<?>> snapshot(Class<? extends AutoConfig> cfg) {
		return Map.of(); // TODO
	}
	
	private static void doSave(Registration reg, Map<String, ConfigValue<?>> snapshot) {
		// TODO (make sure to do an atomic save for safety)
	}
	
	/**
	 * Immediately save the given config to disk.
	 */
	public static void save(Class<? extends AutoConfig> cfg) {
		Registration reg = configsByClass.get(cfg);
		if (reg == null) throw new IllegalArgumentException(cfg+" has not been registered");
		doSave(reg, snapshot(cfg));
	}
	
	/**
	 * Save the given config to disk in a background thread.
	 */
	public static void saveAsync(Class<? extends AutoConfig> cfg) {
		Registration reg = configsByClass.get(cfg);
		if (reg == null) throw new IllegalArgumentException(cfg+" has not been registered");
		var snap = snapshot(cfg);
		SAVE_THREAD.execute(() -> {
			doSave(reg, snap);
		});
	}
	
	/**
	 * Immediately load the given config from disk.
	 */
	public static LoadResult load(Class<? extends AutoConfig> cfg) {
		Registration reg = configsByClass.get(cfg);
		if (reg == null) throw new IllegalArgumentException(cfg+" has not been registered");
		File f = new File(reg.path());
		if (f.exists()) {
			try {
				QDCSS qd = QDCSS.load(f);
				
			} catch (IOException e) {
				Lib39Log.warn("Failed to load config {}", reg.path(), e);
			}
		}
		return null; // TODO
	}
	
	protected AutoConfig() {
		throw new AssertionError("AutoConfig and its subclasses cannot be constructed");
	}
	
	// inner classes so they appear in IDE autocomplete
	
	public static final class StringType implements ConfigValueType<String, TextFieldWidget> {

		@Override
		public Optional<String> unmarshal(String s) {
			return Optional.of(s);
		}

		@Override
		public String marshal(String t) {
			return t;
		}
		
		@Override
		@Environment(EnvType.CLIENT)
		public Optional<TextFieldWidget> createWidget(Text desc, int x, int y, int width, int height, String currentValue, Consumer<String> updateCallback) {
			var w = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, x, y, width, height, desc);
			w.setText(currentValue);
			w.setChangedListener(updateCallback);
			return Optional.of(w);
		}
		
	}
	
	public static final class BooleanType implements ConfigValueType<Boolean, ButtonWidget> {

		@Override
		public Optional<Boolean> unmarshal(String s) {
			switch (s) {
				case "true": case "on": return Optional.of(true);
				case "false": case "off": return Optional.of(false);
				default: return Optional.empty();
			}
		}

		@Override
		public String marshal(Boolean t) {
			return t ? "on" : "off";
		}
		
		@Override
		@Environment(EnvType.CLIENT)
		public Optional<ButtonWidget> createWidget(Text desc, int x, int y, int width, int height, Boolean currentValue, Consumer<Boolean> updateCallback) {
			var w = new ButtonWidget(x, y, width, height, Text.translatable(currentValue ? "options.on" : "options.off"), b -> {}, Supplier::get) {
				private boolean value = currentValue;
				
				@Override
				protected MutableText getNarrationMessage() {
					return Text.translatable(value ? "options.on.composed" : "options.off.composed", desc);
				}
				
				@Override
				public void onPress() {
					value = !value;
					updateCallback.accept(value);
					Object[] args = {};
					setMessage(Text.translatable(value ? "options.on" : "options.off", args));
				}
			};
			return Optional.of(w);
		}
		
	}
	
	public static abstract class AbstractEnumType<E extends Enum<E>> implements ConfigValueType<E, ButtonWidget> {

		private final String translationKey;
		private final Class<E> clazz;
		
		public AbstractEnumType(String translationKey, Class<E> clazz) {
			this.translationKey = translationKey;
			this.clazz = clazz;
		}
		
		@Override
		public Optional<E> unmarshal(String s) {
			return Enums.getIfPresent(clazz, s).toJavaUtil();
		}

		@Override
		public String marshal(E t) {
			return Ascii.toLowerCase(t.name());
		}
		
		@Override
		@Environment(EnvType.CLIENT)
		public Optional<ButtonWidget> createWidget(Text desc, int x, int y, int width, int height, E currentValue, Consumer<E> updateCallback) {
			Iterator<E> cycle = Iterators.cycle(clazz.getEnumConstants());
			Iterators.find(cycle, t -> t == currentValue);
			Object[] args = {};
			var w = new ButtonWidget(x, y, width, height, Text.translatable(translationKey+"."+Ascii.toLowerCase(currentValue.name()), args), b -> {}, Supplier::get) {
				private E value = currentValue;
				
				@Override
				protected MutableText getNarrationMessage() {
					Object[] args = {};
					return Text.translatable("options.generic_value", desc, Text.translatable(translationKey+"."+Ascii.toLowerCase(value.name()), args));
				}
				
				@Override
				public void onPress() {
					value = cycle.next();
					updateCallback.accept(value);
					Object[] args = {};
					setMessage(Text.translatable(translationKey+"."+Ascii.toLowerCase(value.name()), args));
				}
			};
			return Optional.of(w);
		}
		
		public static Optional<AbstractEnumType<?>> createImplicit(String namespace, Field field) {
			EnumType type = field.getAnnotation(EnumType.class);
			if (type != null) {
				return Optional.of(new AbstractEnumType("config."+namespace+"."+Ascii.toLowerCase(type.value().getSimpleName()), type.value()) {});
			}
			return Optional.empty();
		}
		
	}
	
	public static abstract class AbstractDoubleType implements ConfigValueType<Double, SliderWidget> {

		private final double min;
		private final double max;
		private final double delta;
		private final Map<Double, String> specialValueTranslationKeys = new HashMap<>();
		
		public AbstractDoubleType(double min, double max) {
			if (!Double.isFinite(min)) throw new IllegalArgumentException("min must be finite");
			if (!Double.isFinite(max)) throw new IllegalArgumentException("max must be finite");
			if (min >= max) throw new IllegalArgumentException("max cannot be less than or equal to min");
			this.min = min;
			this.max = max;
			this.delta = max-min;
		}
		
		protected void addSpecialValue(double d, String translationKey) {
			specialValueTranslationKeys.put(d, translationKey);
		}
		
		@Override
		public Optional<Double> unmarshal(String s) {
			return Optional.ofNullable(Doubles.tryParse(s)).map(d -> MathHelper.clamp(d, min, max));
		}

		@Override
		public String marshal(Double t) {
			return t.toString();
		}
		
		@Override
		@Environment(EnvType.CLIENT)
		public Optional<SliderWidget> createWidget(Text desc, int x, int y, int width, int height, Double currentValue, Consumer<Double> updateCallback) {
			var w = new SliderWidget(x, y, width, height, Text.literal(String.format("%.2f", currentValue)), currentValue) {
				
				private double realValue = currentValue;
				
				@Override
				protected void applyValue() {
					realValue = min + (delta*value);
					updateCallback.accept(realValue);
				}
				
				@Override
				protected void updateMessage() {
					if (specialValueTranslationKeys.containsKey(realValue)) {
						Object[] args = {};
						setMessage(Text.translatable(specialValueTranslationKeys.get(realValue), args));
					} else {
						setMessage(Text.literal(String.format("%.2f", realValue)));
					}
				}
			};
			return Optional.of(w);
		}
		
		public static Optional<AbstractDoubleType> createImplicit(String namespace, Field field) {
			Range range = field.getAnnotation(Range.class);
			if (range != null) {
				return Optional.of(new AbstractDoubleType(range.min(), range.max()) {});
			}
			return Optional.empty();
		}
		
	}
	
	public static abstract class AbstractIntType implements ConfigValueType<Integer, SliderWidget> {

		private final int min;
		private final int max;
		private final int delta;
		private final Map<Integer, String> specialValueTranslationKeys = new HashMap<>();
		
		public AbstractIntType(int min, int max) {
			if (min >= max) throw new IllegalArgumentException("max cannot be less than or equal to min");
			this.min = min;
			this.max = max;
			this.delta = max-min;
		}
		
		protected void addSpecialValue(int i, String translationKey) {
			specialValueTranslationKeys.put(i, translationKey);
		}
		
		@Override
		public Optional<Integer> unmarshal(String s) {
			return Optional.ofNullable(Ints.tryParse(s)).map(i -> MathHelper.clamp(i, min, max));
		}

		@Override
		public String marshal(Integer t) {
			return t.toString();
		}
		
		@Override
		@Environment(EnvType.CLIENT)
		public Optional<SliderWidget> createWidget(Text desc, int x, int y, int width, int height, Integer currentValue, Consumer<Integer> updateCallback) {
			var w = new SliderWidget(x, y, width, height, Text.literal(currentValue.toString()), currentValue) {
				
				private int realValue = currentValue;
				
				@Override
				protected void applyValue() {
					realValue = min + (int)Math.round(delta*value);
					updateCallback.accept(realValue);
				}
				
				@Override
				protected void updateMessage() {
					if (specialValueTranslationKeys.containsKey(realValue)) {
						Object[] args = {};
						setMessage(Text.translatable(specialValueTranslationKeys.get(realValue), args));
					} else {
						setMessage(Text.literal(Integer.toString(realValue)));
					}
				}
				
			};
			return Optional.of(w);
		}
		
		public static Optional<AbstractIntType> createImplicit(String namespace, Field field) {
			Range range = field.getAnnotation(Range.class);
			if (range != null) {
				return Optional.of(new AbstractIntType((int)range.min(), (int)range.max()) {});
			}
			return Optional.empty();
		}
		
	}
	
}
