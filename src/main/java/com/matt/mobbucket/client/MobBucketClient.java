package com.matt.mobbucket.client;

import com.matt.mobbucket.MobBucketMod;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;

public class MobBucketClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Register our custom special model renderer so the item model can use
		// { "type": "mobbucket:captured_mob" }. ID_MAPPER is access-widened by Fabric.
		SpecialModelRenderers.ID_MAPPER.put(
				MobBucketMod.id("captured_mob"),
				CapturedMobRenderer.Unbaked.MAP_CODEC);
	}
}
