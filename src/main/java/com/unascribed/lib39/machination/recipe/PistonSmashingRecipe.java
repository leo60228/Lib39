package com.unascribed.lib39.machination.recipe;

import java.util.Collections;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.unascribed.lib39.machination.Lib39Machination;
import com.unascribed.lib39.machination.ingredient.BlockIngredient;

import com.google.common.collect.Lists;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.CookingCategory;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.dynamic.Codecs;
import net.minecraft.world.World;

public class PistonSmashingRecipe implements Recipe<Inventory> {

	protected final String group;
	protected final BlockIngredient input;
	protected final BlockIngredient catalyst;
	protected final ItemStack output;
	protected final boolean hasCloud;
	protected final int cloudColor;
	protected final int cloudSize;
	protected final ItemStack cloudOutput;
	protected final List<StatusEffectInstance> cloudEffects;

	public PistonSmashingRecipe(String group, BlockIngredient input, BlockIngredient catalyst, ItemStack output, boolean hasCloud, int cloudColor, int cloudSize, ItemStack cloudOutput, List<StatusEffectInstance> cloudEffects) {
		this.group = group;
		this.input = input;
		this.catalyst = catalyst;
		this.output = output;
		this.hasCloud = hasCloud;
		this.cloudColor = cloudColor;
		this.cloudSize = cloudSize;
		this.cloudOutput = cloudOutput;
		this.cloudEffects = cloudEffects;
	}
	
	public BlockIngredient getInput() {
		return input;
	}
	
	public BlockIngredient getCatalyst() {
		return catalyst;
	}

	@Override
	public ItemStack getResult(DynamicRegistryManager registryManager) {
		return output;
	}
	
	public boolean hasCloud() {
		return hasCloud;
	}
	
	public int getCloudColor() {
		return cloudColor;
	}
	
	public int getCloudSize() {
		return cloudSize;
	}
	
	public ItemStack getCloudOutput() {
		return cloudOutput;
	}
	
	public List<StatusEffectInstance> getCloudEffects() {
		return cloudEffects;
	}

	@Override
	public boolean matches(Inventory inv, World world) {
		return false;
	}

	@Override
	public ItemStack craft(Inventory inv, DynamicRegistryManager manager) {
		return getResult(manager).copy();
	}

	@Override
	public boolean fits(int width, int height) {
		return false;
	}

	@Override
	public DefaultedList<Ingredient> getIngredients() {
		return DefaultedList.of();
	}
	
	@Override
	public String getGroup() {
		return group;
	}
	
	@Override
	public boolean isIgnoredInRecipeBook() {
		return true;
	}

	@Override
	public RecipeType<?> getType() {
		return Lib39Machination.RecipeTypes.PISTON_SMASHING;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return Lib39Machination.RecipeSerializers.PISTON_SMASHING;
	}
	
	public static class Serializer implements RecipeSerializer<PistonSmashingRecipe> {
		public static final Codec<PistonSmashingRecipe> CODEC = RecordCodecBuilder.create(
				instance -> instance.group(
								Codecs.method_53049(Codec.STRING, "group", "").forGetter(r -> r.group),
								BlockIngredient.field_46096.fieldOf("ingredient").forGetter(r -> r.input),
								Registries.ITEM.getCodec().xmap(ItemStack::new, ItemStack::getItem).fieldOf("result").forGetter(abstractCookingRecipe -> abstractCookingRecipe.result),
								Codec.FLOAT.fieldOf("experience").orElse(0.0F).forGetter(abstractCookingRecipe -> abstractCookingRecipe.experience),
								Codec.INT.fieldOf("cookingtime").orElse(i).forGetter(abstractCookingRecipe -> abstractCookingRecipe.cookingTime)
				)
						.apply(instance, PistonSmashingRecipe::new)
		);

		@Override
		public Codec<PistonSmashingRecipe> getCodec() {
			return CODEC;
		}

		@Override
		public PistonSmashingRecipe read(JsonObject obj) {
			String group = JsonHelper.getString(obj, "group", "");
			BlockIngredient input = BlockIngredient.fromJson(obj.get("input"));
			BlockIngredient catalyst = BlockIngredient.fromJson(obj.get("catalysts"));
			ItemStack output = obj.has("output") ? ShapedRecipe.outputFromJson(obj.getAsJsonObject("output")) : ItemStack.EMPTY;
			boolean hasCloud = obj.has("cloud");
			int cloudColor = 0;
			int cloudSize = 0;
			ItemStack cloudOutput = ItemStack.EMPTY;
			List<StatusEffectInstance> cloudEffects = Lists.newArrayList();
			if (hasCloud) {
				JsonObject cloud = obj.getAsJsonObject("cloud");
				cloudColor = Integer.parseInt(cloud.get("color").getAsString().substring(1), 16);
				cloudSize = JsonHelper.getInt(cloud, "size", 1);
				if (cloud.has("output")) {
					cloudOutput = ShapedRecipe.outputFromJson(cloud.getAsJsonObject("output"));
				}
				if (cloud.has("effects")) {
					Iterable<JsonElement> iter;
					if (cloud.get("effects").isJsonObject()) {
						iter = Collections.singleton(cloud.get("effects"));
					} else {
						iter = cloud.get("effects").getAsJsonArray();
					}
					for (JsonElement ele : iter) {
						JsonObject enObj = ele.getAsJsonObject();
						StatusEffect effect = Registries.STATUS_EFFECT.get(Identifier.tryParse(enObj.get("effect").getAsString()));
						if (effect == null) continue;
						int amplifier = JsonHelper.getInt(enObj, "amplifier", 0);
						int duration = JsonHelper.getInt(enObj, "duration");
						cloudEffects.add(new StatusEffectInstance(effect, duration, amplifier));
					}
				}
			}
			return new PistonSmashingRecipe(group, input, catalyst, output, hasCloud, cloudColor, cloudSize, cloudOutput, cloudEffects);
		}

		@Override
		public PistonSmashingRecipe read(PacketByteBuf buf) {
			String group = buf.readString(32767);
			BlockIngredient input = BlockIngredient.read(buf);
			BlockIngredient catalyst = BlockIngredient.read(buf);
			ItemStack output = buf.readItemStack();
			boolean hasCloud = buf.readBoolean();
			int cloudColor = 0;
			int cloudSize = 0;
			ItemStack cloudOutput = ItemStack.EMPTY;
			List<StatusEffectInstance> cloudEffects = Lists.newArrayList();
			if (hasCloud) {
				cloudColor = buf.readMedium();
				cloudSize = buf.readVarInt();
				cloudOutput = buf.readItemStack();
				int cloudEffectCount = buf.readVarInt();
				for (int i = 0; i < cloudEffectCount; i++) {
					cloudEffects.add(new StatusEffectInstance(Registries.STATUS_EFFECT.get(Identifier.tryParse(buf.readString())), buf.readVarInt(), buf.readVarInt()));
				}
			}
			return new PistonSmashingRecipe(group, input, catalyst, output, hasCloud, cloudColor, cloudSize, cloudOutput, cloudEffects);
		}

		@Override
		public void write(PacketByteBuf buf, PistonSmashingRecipe recipe) {
			buf.writeString(recipe.group);
			recipe.input.write(buf);
			recipe.catalyst.write(buf);
			buf.writeItemStack(recipe.output);
			buf.writeBoolean(recipe.hasCloud);
			if (recipe.hasCloud) {
				buf.writeMedium(recipe.cloudColor);
				buf.writeVarInt(recipe.cloudSize);
				buf.writeItemStack(recipe.cloudOutput);
				buf.writeVarInt(recipe.cloudEffects.size());
				for (StatusEffectInstance sei : recipe.cloudEffects) {
					buf.writeString(Registries.STATUS_EFFECT.getId(sei.getEffectType()).toString());
					buf.writeVarInt(sei.getDuration());
					buf.writeVarInt(sei.getAmplifier());
				}
			}
		}
		
	}

}
