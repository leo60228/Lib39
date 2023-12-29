package com.unascribed.lib39.mesh.api;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.unascribed.lib39.core.Lib39Log;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;

public class BlockNetworkManager extends PersistentState {

	private static final BiMap<Identifier, BlockNetworkType<?, ?>> networkTypes = HashBiMap.create();
	
	public static void registerNetworkType(Identifier id, BlockNetworkType<?, ?> type) {
		networkTypes.put(id, type);
	}

	public static Identifier getId(BlockNetworkType<?, ?> type) {
		return networkTypes.inverse().get(type);
	}

	public static PersistentState.C_vpqwshwc<BlockNetworkManager> getPersistentStateType(ServerWorld world) {
		return new PersistentState.C_vpqwshwc<>(() -> new BlockNetworkManager(world), tag -> BlockNetworkManager.readNbt(world, tag), null);
	}
	
	protected final ServerWorld world;
	private final Map<UUID, BlockNetwork<?, ?>> networks = Maps.newHashMap();
	protected final Table<BlockPos, BlockNetworkType<?, ?>, BlockNetwork<?, ?>> networksByPos = HashBasedTable.create();

	public BlockNetworkManager(ServerWorld world) {
		this.world = world;
	}

	public static BlockNetworkManager get(ServerWorld world) {
		BlockNetworkManager fn = world.getPersistentStateManager().getOrCreate(
				getPersistentStateType(world),
				"lib39_mesh_networks");
		return fn;
	}

	public static BlockNetworkManager readNbt(ServerWorld world, NbtCompound tag) {
		BlockNetworkManager ret = new BlockNetworkManager(world);
		NbtCompound networks = tag.getCompound("Networks");
		for (String k : networks.getKeys()) {
			NbtCompound nbt = networks.getCompound(k);
			Identifier typeId = Identifier.tryParse(nbt.getString("Type"));
			UUID id = UUID.fromString(k);
			if (typeId == null || !networkTypes.containsKey(typeId)) {
				Lib39Log.warn("Encountered unknown network type id {} while loading network {}. Discarding!", typeId, id);
				continue;
			}
			BlockNetworkType<?, ?> type = networkTypes.get(typeId);
			BlockNetwork<?, ?> fn = type.construct(ret, id);
			fn.readNbt(nbt);
			ret.addNetworkDirectly(fn);
		}
		return ret;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtCompound networks = new NbtCompound();
		for (BlockNetwork<?, ?> net : this.networks.values()) {
			NbtCompound en = new NbtCompound();
			net.writeNbt(en);
			networks.put(net.getId().toString(), en);
		}
		nbt.put("Networks", networks);
		return nbt;
	}
	
	public void tick() {
		for (BlockNetwork<?, ?> fn : networks.values()) {
			fn.tick();
		}
	}
	
	@SuppressWarnings("unchecked")
	public <N extends BlockNetworkNode, T extends BlockNetworkNodeType> Optional<BlockNetwork<N, T>> getNetworkAt(BlockNetworkType<N, T> type, BlockPos pos) {
		return Optional.ofNullable((BlockNetwork<N, T>)networksByPos.get(pos, type));
	}

	public void addNetwork(BlockNetwork<?, ?> network) {
		addNetworkDirectly(network);
		markDirty();
	}
	
	private void addNetworkDirectly(BlockNetwork<?, ?> network) {
		if (network.getOwner() != this) throw new IllegalArgumentException("Network does not belong to this world");
		networks.put(network.getId(), network);
		network.onAdded();
	}

	public void removeNetwork(BlockNetwork<?, ?> network) {
		removeNetworkDirectly(network);
		markDirty();
	}

	private void removeNetworkDirectly(BlockNetwork<?, ?> network) {
		if (network.getOwner() != this) throw new IllegalArgumentException("Network does not belong to this world");
		networks.remove(network.getId(), network);
		network.onRemoved();
	}

	@SuppressWarnings("unchecked")
	public <N extends BlockNetworkNode, T extends BlockNetworkNodeType> void introduce(BlockNetworkType<N, T> networkType, BlockPos pos, T nodeType) {
		N n = networkType.createNode(pos, nodeType);
		if (networksByPos.contains(pos, networkType)) {
			((BlockNetwork<N, T>)networksByPos.get(pos, networkType)).addNode(n);
			return;
		}
		for (BlockPos neighbor : neighbors(pos)) {
			if (networksByPos.contains(neighbor, networkType)) {
				((BlockNetwork<N, T>)networksByPos.get(neighbor, networkType)).addNode(n);
				return;
			}
		}
		BlockNetwork<N, T> net = networkType.construct(this, UUID.randomUUID());
		Lib39Log.debug("Creating new network {} of type {} for orphan at {}", net.getId(), getId(networkType), pos.toShortString());
		net.addNode(n);
		addNetwork(net);
	}

	public void destroy(BlockNetworkType<?, ?> type, BlockPos pos) {
		if (networksByPos.contains(pos, type)) {
			networksByPos.get(pos, type).removeNodeAt(pos);
		}
	}

	private static final ImmutableList<Direction> DIRECTIONS = ImmutableList.copyOf(Direction.values());
	
	public static Set<BlockPos> neighbors(BlockPos pos) {
		BlockPos fpos = pos.toImmutable();
		return new AbstractSet<BlockPos>() {
			@Override
			public Iterator<BlockPos> iterator() {
				return new AbstractIterator<BlockPos>() {
					private final BlockPos.Mutable mut = new BlockPos.Mutable();
					private final Iterator<Direction> dirIter = DIRECTIONS.iterator();
					
					@Override
					protected BlockPos computeNext() {
						if (!dirIter.hasNext()) return endOfData();
						return mut.set(fpos).move(dirIter.next());
					}
				};
			}
			
			@Override
			public boolean contains(Object o) {
				return o instanceof BlockPos && fpos.getManhattanDistance((BlockPos)o) == 1;
			}
			
			@Override
			public int size() {
				return DIRECTIONS.size();
			}
		};
	}
	
}
