package com.unascribed.lib39.waypoint;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import com.mojang.blaze3d.vertex.VertexFormats;
import com.unascribed.lib39.core.Lib39Log;
import com.unascribed.lib39.util.api.DelegatingVertexConsumer;
import com.unascribed.lib39.waypoint.api.HaloBlockEntity;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.World;

public class HaloRenderer {

	private static final MinecraftClient mc = MinecraftClient.getInstance();
	
	private static final Long2ReferenceMap<BlockEntity> lampsByBlock = new Long2ReferenceOpenHashMap<>();
	private static final Reference2ReferenceMap<BlockEntity, Object> lastState = new Reference2ReferenceOpenHashMap<>();
	private static final Long2ReferenceMultimap<BlockEntity> lampsBySection = new Long2ReferenceMultimap<>();
	private static final Long2ReferenceMap<VertexBuffer> buffers = new Long2ReferenceOpenHashMap<>();
	private static final Long2ReferenceMap<Box> boundingBoxes = new Long2ReferenceOpenHashMap<>();
	private static final Long2ReferenceMultimap<Sprite> sprites = new Long2ReferenceMultimap<>();

	public static void clearCache() {
		buffers.values().forEach(VertexBuffer::close);
		buffers.clear();
		boundingBoxes.clear();
		sprites.clear();
	}

	public static void render(World world, MatrixStack matrices, VertexConsumer vc, BlockState state,
			int color, @Nullable Direction facing, @Nullable BlockPos pos) {
		render(world, matrices, vc, state, color, facing, pos, s -> {});
	}
	
	public static void render(World world, MatrixStack matrices, VertexConsumer vc, BlockState state,
			int color, @Nullable Direction facing, @Nullable BlockPos pos,
			Consumer<Sprite> spriteMemoizer) {
		if (color == 0) color = 0x222222;
		float r = ((color >> 16)&0xFF)/255f;
		float g = ((color >> 8)&0xFF)/255f;
		float b = (color&0xFF)/255f;
		BakedModel base = MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
		BakedModel bm;
		try {
			Lib39Waypoint.retrievingHalo = true;
			bm = base.getOverrides().apply(base, ItemStack.EMPTY, MinecraftClient.getInstance().world, MinecraftClient.getInstance().player, 39);
		} finally {
			Lib39Waypoint.retrievingHalo = false;
		}
		if (bm == null) return;
		DelegatingVertexConsumer dvc = new DelegatingVertexConsumer(vc) {
			@Override
			public void vertex(
					float x, float y, float z,
					float red, float green, float blue, float alpha,
					float u, float v,
					int overlay, int light,
					float normalX, float normalY, float normalZ
				) {
					vertex(x, y, z);
					uv(u, v);
					color(red, green, blue, alpha);
					normal(normalX, normalY, normalZ);
					next();
				}
		};

		matrices.push();

		if (facing != null) {
			int x = 0;
			int y = 0;
			switch (facing) {
				case DOWN: break;
				case WEST: x = 90; y = 90; break;
				case NORTH: x = 90; break;
				case SOUTH: x = 90; y = 180; break;
				case EAST: x = 90; y = 270; break;
				case UP: x = 180; break;
			}
			matrices.translate(0.5, 0.5, 0.5);
			matrices.multiply(new Quaternionf(new AxisAngle4f(y*MathHelper.RADIANS_PER_DEGREE, 0, 1, 0)));
			matrices.multiply(new Quaternionf(new AxisAngle4f(x*MathHelper.RADIANS_PER_DEGREE, 1, 0, 0)));
			matrices.translate(-0.5, -0.5, -0.5);
		}

		for (BakedQuad bq : bm.getQuads(state, null, world == null ? null : world.random)) {
			spriteMemoizer.accept(bq.getSprite());
			dvc.bakedQuad(matrices.peek(), bq, r, g, b, 0, 0);
		}
		for (Direction dir : Direction.values()) {
			if (pos == null || Block.shouldDrawSide(state, MinecraftClient.getInstance().world, pos, dir, pos.offset(dir))) {
				for (BakedQuad bq : bm.getQuads(state, dir, world == null ? null : world.random)) {
					spriteMemoizer.accept(bq.getSprite());
					dvc.bakedQuad(matrices.peek(), bq, r, g, b, 0, 0);
				}
			}
		}

		matrices.pop();
	}
	
	private static Boolean driverIsBuggy;

	public static void render(WorldRenderContext wrc) {
		if (driverIsBuggy == null) {
			driverIsBuggy = Pattern.compile("NVIDIA 39[0-9]\\.").matcher(RenderSystem.getApiDescription()).find();
			if (driverIsBuggy) {
				clearCache();
				Lib39Log.error("====================== Lib39 Waypoint Error ======================");
				Lib39Log.error("       Your graphics driver is known to be buggy with VBOs.       ");
				Lib39Log.error("    To avoid a game crash, Lib39 Waypoint is disabling itself.    ");
				Lib39Log.error("  Any mods which utilize its features will not render correctly!  ");
				Lib39Log.error("  DO NOT REPORT BUGS RELATED TO HALO RENDERING ON THIS COMPUTER.  ");
				Lib39Log.error("==================================================================");
			}
		}
		if (driverIsBuggy) {
			return;
		}
		wrc.profiler().swap("lib39-waypoint");
		if (!lampsBySection.isEmpty()) {
			wrc.profiler().push("prepare");
			LongSet needsRebuild = null;
			for (BlockEntity be : lampsBySection.values()) {
				if (!(be instanceof HaloBlockEntity)) continue;
				Object s = ((HaloBlockEntity)be).getStateObject();
				long csp = ChunkSectionPos.toLong(be.getPos());
				if (lastState.get(be) != s || !buffers.containsKey(csp)) {
					lastState.put(be, s);
					if (needsRebuild == null) {
						needsRebuild = new LongArraySet();
					}
					needsRebuild.add(csp);
				}
			}
			wrc.profiler().swap("rebuild");
			MatrixStack scratch = new MatrixStack();
			if (needsRebuild != null) {
				LongIterator iter = needsRebuild.iterator();
				while (iter.hasNext()) {
					long csp = iter.nextLong();
					Collection<BlockEntity> l = lampsBySection.get(csp);
					sprites.removeAll(csp);
					if (l.isEmpty()) {
						if (buffers.containsKey(csp)) {
							buffers.remove(csp).close();
							boundingBoxes.remove(csp);
						}
						continue;
					}
					Box bounds = null;
					// POSITION_TEXTURE_COLOR_NORMAL is one of the few generic shaders with fog support
					BufferBuilder vc = new BufferBuilder(24 * VertexFormats.POSITION_TEXTURE_COLOR_NORMAL.getVertexSize() * l.size());
					vc.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
					for (BlockEntity be : l) {
						if (!(be instanceof HaloBlockEntity) || !((HaloBlockEntity)be).shouldRenderHalo()) continue;
						scratch.push();
							scratch.translate(be.getPos().getX()-minX(csp), be.getPos().getY()-minY(csp), be.getPos().getZ()-minZ(csp));
							int color = ((HaloBlockEntity)be).getGlowColor();
							BlockState state = be.getCachedState();
							Direction facing = ((HaloBlockEntity)be).getFacing();
							render(mc.world, scratch, vc, state, color, facing, be.getPos(), s -> sprites.put(csp, s));
							Box myBox = new Box(be.getPos()).expand(0.5);
							if (bounds == null) {
								bounds = myBox;
							} else {
								bounds = bounds.union(myBox);
							}
						scratch.pop();
					}
					VertexBuffer vb = buffers.computeIfAbsent(csp, blah -> new VertexBuffer(VertexBuffer.Usage.STATIC));
					vb.bind();
					vb.upload(vc.end());
					buffers.put(csp, vb);
					boundingBoxes.put(csp, bounds);
				}
			}
			wrc.profiler().swap("render");
			sprites.values().forEach(SodiumAccess.markSpriteActive);
			MatrixStack matrices = wrc.matrixStack();
			matrices.push();
			Vec3d cam = wrc.camera().getPos();
			matrices.translate(-cam.x, -cam.y, -cam.z);
			boolean started = false;
			LongIterator iter = buffers.keySet().iterator();
			while (iter.hasNext()) {
				long pos = iter.nextLong();
				Box box = boundingBoxes.get(pos);
				if (box != null && wrc.frustum().isVisible(box) && wrc.worldRenderer().method_40050(ChunkSectionPos.from(pos).getMinPos())) {
					matrices.push();
						matrices.translate(minX(pos), minY(pos), minZ(pos));
						VertexBuffer buf = buffers.get(pos);
						buf.bind();
						if (!started) {
							WaypointRenderLayers.getHalo().startDrawing();
							started = true;
						}
						buf.draw(matrices.peek().getModel(), RenderSystem.getProjectionMatrix(), GameRenderer.getPositionTexColorNormalShader());
						VertexBuffer.unbind();
					matrices.pop();
				}
			}
			if (started) WaypointRenderLayers.getHalo().endDrawing();
			matrices.pop();
			wrc.profiler().pop();
		}
		wrc.profiler().swap("particles");
	}
	
	private static int minX(long csp) {
		return ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackX(csp));
	}
	
	private static int minY(long csp) {
		return ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackY(csp));
	}
	
	private static int minZ(long csp) {
		return ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackZ(csp));
	}

	public static <T extends BlockEntity & HaloBlockEntity> void notifyCreated(T be) {
		long cs = ChunkSectionPos.toLong(be.getPos());
		if (!lampsBySection.containsEntry(cs, be)) {
			long bp = be.getPos().asLong();
			if (lampsByBlock.containsKey(bp)) {
				BlockEntity other = lampsByBlock.remove(bp);
				lampsBySection.remove(cs, other);
			}
			lampsByBlock.put(bp, be);
			lampsBySection.put(cs, be);
		}
	}
	

	public static void tick() {
		if (driverIsBuggy == Boolean.TRUE) return;
		mc.getProfiler().swap("lib39-waypoint");
		if (mc.world != null) {
			Profiler p = mc.getProfiler();
			p.push("validate");
			Iterator<BlockEntity> iter = lampsBySection.values().iterator();
			while (iter.hasNext()) {
				BlockEntity be = iter.next();
				if (be.isRemoved() || be.getWorld() != mc.world) {
					long cs = ChunkSectionPos.toLong(be.getPos());
					if (buffers.containsKey(cs)) {
						buffers.remove(cs).close();
					}
					lampsByBlock.remove(be.getPos().asLong(), be);
					iter.remove();
				}
			}
			p.pop();
		} else {
			lampsByBlock.clear();
			lampsBySection.clear();
			lastState.clear();
			clearCache();
		}
	}

}
