package com.unascribed.lib39.util.api;

import net.minecraft.registry.HolderLookup;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class NBTUtils {

	/**
	 * Convert a Vec3d into a list of 3 doubles.
	 */
	public static NbtList vecToList(Vec3d vec) {
		NbtList li = new NbtList();
		li.add(NbtDouble.of(vec.x));
		li.add(NbtDouble.of(vec.y));
		li.add(NbtDouble.of(vec.z));
		return li;
	}

	/**
	 * Convert a list of 3 doubles into a Vec3d, or null if it can't be converted.
	 */
	public static @Nullable Vec3d listToVec(NbtList li) {
		if (li.getHeldType() != NbtType.DOUBLE) return null;
		if (li.size() != 3) return null;
		return new Vec3d(li.getDouble(0), li.getDouble(1), li.getDouble(2));
	}

	/**
	 * Convert a BlockPos into a list of 3 ints.
	 */
	public static NbtList blockPosToList(BlockPos pos) {
		NbtList li = new NbtList();
		li.add(NbtInt.of(pos.getX()));
		li.add(NbtInt.of(pos.getY()));
		li.add(NbtInt.of(pos.getZ()));
		return li;
	}

	/**
	 * Convert a list of 3 ints into a BlockPos, or null if it can't be converted.
	 */
	public static @Nullable BlockPos listToBlockPos(NbtList li) {
		if (li.getHeldType() != NbtType.INT) return null;
		if (li.size() != 3) return null;
		return new BlockPos(li.getInt(0), li.getInt(1), li.getInt(2));
	}

	/**
	 * Serialize an Inventory to an NbtList. Unlike {@link Inventories#writeNbt}, this supports arbitrarily
	 * large stack sizes. Unlike {@link SimpleInventory#toNbtList}, this keeps slot indexes and therefore
	 * empty slots.
	 * @see #deserializeInv
	 */
	public static NbtList serializeInv(HolderLookup.Provider provider, Inventory inv) {
		NbtList out = new NbtList();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack is = inv.getStack(i);
			if (!is.isEmpty()) {
				NbtCompound c = (NbtCompound) is.encode(provider, new NbtCompound());
				if (is.getCount() > 127) {
					c.putInt("Count", is.getCount());
				}
				c.putInt("Slot", i);
				out.add(c);
			}
		}
		return out;
	}
	
	/**
	 * Deserialize an NbtList created by {@link #serializeInv} into the given Inventory. The
	 * Inventory will be cleared first. Can load large stacks written by serializeInv.
	 */
	public static void deserializeInv(HolderLookup.Provider provider, NbtList tag, Inventory inv) {
		inv.clear();
		for (int i = 0; i < tag.size(); i++) {
			NbtCompound c = tag.getCompound(i);
			int count = c.getInt("Count");
			if (count > 127) {
				c = c.copy();
				c.putInt("Count", 1);
			}
			ItemStack is = ItemStack.fromNbt(provider, c);
			is.setCount(count);
			inv.setStack(c.getInt("Slot"), is);
		}
	}

}
