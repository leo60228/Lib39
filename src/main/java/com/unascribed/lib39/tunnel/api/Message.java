package com.unascribed.lib39.tunnel.api;

import java.util.Map;

import com.unascribed.lib39.core.Lib39Log;
import com.unascribed.lib39.tunnel.api.annotation.type.Asynchronous;
import com.unascribed.lib39.tunnel.api.annotation.type.ReceivedOn;
import com.unascribed.lib39.tunnel.api.annotation.type.ValidForSpectators;
import com.unascribed.lib39.tunnel.api.exception.BadMessageException;
import com.unascribed.lib39.tunnel.api.exception.WrongSideException;
import com.google.common.collect.Maps;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public abstract class Message {
	private static final class ClassInfo {
		public final boolean async;
		public final EnvType env;
		public final boolean validForSpectators;
		public ClassInfo(boolean async, EnvType env, boolean validForSpectators) {
			this.async = async;
			this.env = env;
			this.validForSpectators = validForSpectators;
		}
	}
	private static final Map<Class<?>, ClassInfo> classInfo = Maps.newHashMap();
	
	
	private transient final NetworkContext ctx;
	
	private transient final EnvType env;
	private transient final boolean async;
	private transient final boolean validForSpectators;
	
	public Message(NetworkContext ctx) {
		this.ctx = ctx;
		
		ClassInfo ci = classInfo.get(getClass());
		if (ci == null) {
			if (this instanceof C2SMessage) {
				env = EnvType.SERVER;
			} else if (this instanceof S2CMessage) {
				env = EnvType.CLIENT;
			} else {
				ReceivedOn ro = getClass().getDeclaredAnnotation(ReceivedOn.class);
				if (ro == null) {
					throw new BadMessageException("Must specify @ReceivedOn for an old-style Message");
				} else {
					env = ro.value();
				}
			}
			
			async = getClass().getDeclaredAnnotation(Asynchronous.class) != null;
			validForSpectators = getClass().getDeclaredAnnotation(ValidForSpectators.class) != null;
			classInfo.put(getClass(), new ClassInfo(async, env, validForSpectators));
		} else {
			async = ci.async;
			env = ci.env;
			validForSpectators = ci.validForSpectators;
		}
		
	}
	
	@Environment(EnvType.CLIENT)
	void doHandleClient() {
		if (async) {
			handle(MinecraftClient.getInstance().player);
		} else {
			MinecraftClient.getInstance().execute(new Runnable() {
				@Override
				@Environment(EnvType.CLIENT)
				public void run() {
					handle(MinecraftClient.getInstance().player);
				}
			});
		}
	}
	
	void doHandleServer(PlayerEntity sender) {
		if (sender == null) throw new IllegalArgumentException("sender is null while handling "+getClass().getName());
		if (sender.isSpectator() && !validForSpectators) {
			Lib39Log.warn("Spectator {} sent {}, which is not valid for spectators. Ignoring.", sender.getName(), getClass().getName());
			return;
		}
		if (async) {
			handle(sender);
		} else {
			((ServerWorld)sender.getWorld()).getServer().execute(() -> handle(sender));
		}
	}
	
	/**
	 * Handles this Message when received.
	 *
	 * @param player The player that sent this Message if received on the server.
	 *               The player that received this Message if received on the client.
	 */
	protected abstract void handle(PlayerEntity player);
	
	EnvType getEnv() {
		return env;
	}
	
	/**
	 * For use on the server-side. Sends this Message to every player that can
	 * see the given entity.
	 */
	public void sendToAllWatching(Entity e) {
		if (env == EnvType.SERVER) wrongSide();
		if (e.getWorld() instanceof ServerWorld) {
			ServerWorld srv = (ServerWorld) e.getWorld();
			CustomPayloadS2CPacket packet = toClientboundVanillaPacket();
			srv.getChunkManager().sendToNearbyPlayers(e, packet);
		}
	}
	
	/**
	 * For use on the server-side. Sends this Message to the given player.
	 */
	public void sendTo(PlayerEntity player) {
		if (env == EnvType.SERVER) wrongSide();
		if (player instanceof ServerPlayerEntity) {
			((ServerPlayerEntity) player).networkHandler.send(toClientboundVanillaPacket());
		}
	}
	
	/**
	 * For use on the <i>client</i>-side. This is the only valid method for use
	 * on the client side.
	 */
	@Environment(EnvType.CLIENT)
	public void sendToServer() {
		if (env == EnvType.CLIENT) wrongSide();
		ClientPlayNetworkHandler conn = MinecraftClient.getInstance().getNetworkHandler();
		if (conn == null) throw new IllegalStateException("Cannot send a message while not connected");
		conn.getConnection().send(toServerboundVanillaPacket());
	}
	
	/**
	 * Mainly intended for internal use, but can be useful for more complex
	 * use cases.
	 */
	public final CustomPayloadC2SPacket toServerboundVanillaPacket() {
		return new CustomPayloadC2SPacket(ctx.getPayloadFrom(this));
	}
	
	/**
	 * Mainly intended for internal use, but can be useful for more complex
	 * use cases.
	 */
	public final CustomPayloadS2CPacket toClientboundVanillaPacket() {
		return new CustomPayloadS2CPacket(ctx.getPayloadFrom(this));
	}
	
	
	private void wrongSide() {
		throw new WrongSideException(getClass() + " cannot be sent from side " + env);
	}
}
