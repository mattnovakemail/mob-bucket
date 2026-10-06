package com.matt.mobbucket;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MobBucketMod implements ModInitializer {
	public static final String MOD_ID = "mobbucket";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceKey<Item> MOB_BUCKET_KEY =
			ResourceKey.create(Registries.ITEM, id("mob_bucket"));

	public static final Item MOB_BUCKET = new MobBucketItem(
			new Item.Properties().setId(MOB_BUCKET_KEY).stacksTo(1));

	// The vanilla "Tools & Utilities" creative tab. Its ResourceKey is no longer
	// a public constant in 26.3, so we rebuild it from its registry id.
	private static final ResourceKey<CreativeModeTab> TOOLS_AND_UTILITIES =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB,
					Identifier.fromNamespaceAndPath("minecraft", "tools_and_utilities"));

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.ITEM, MOB_BUCKET_KEY, MOB_BUCKET);

		CreativeModeTabEvents.modifyOutputEvent(TOOLS_AND_UTILITIES)
				.register(output -> output.accept(MOB_BUCKET));

		LOGGER.info("Mob In A Bucket loaded - scoop responsibly!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
