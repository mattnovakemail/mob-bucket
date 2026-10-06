package com.matt.mobbucket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Renders the captured mob as a small live model peeking out of the bucket.
 *
 * <p>The transform (scale / vertical offset / rotation) is read from the item
 * model JSON so placement can be tuned without recompiling.
 */
public class CapturedMobRenderer implements SpecialModelRenderer<LivingEntity> {
	private static final String ENTITY_TAG = "CapturedEntity";
	private static final CameraRenderState CAMERA = new CameraRenderState();

	// Bounded cache of reconstructed client-side entities, keyed by their NBT,
	// so we don't rebuild an entity every frame. Cleared when the level changes.
	private static final int CACHE_MAX = 32;
	private static final Map<String, LivingEntity> CACHE =
			new LinkedHashMap<>(16, 0.75F, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, LivingEntity> eldest) {
					return size() > CACHE_MAX;
				}
			};
	private static ClientLevel cacheLevel;

	private final float scale;
	private final float yOffset;
	private final float rotationDegrees;

	public CapturedMobRenderer(float scale, float yOffset, float rotationDegrees) {
		this.scale = scale;
		this.yOffset = yOffset;
		this.rotationDegrees = rotationDegrees;
	}

	@Override
	public LivingEntity extractArgument(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag root = data.copyTag();
		if (!root.contains(ENTITY_TAG)) {
			return null;
		}
		return getOrCreate(root.getCompoundOrEmpty(ENTITY_TAG));
	}

	private static LivingEntity getOrCreate(CompoundTag tag) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return null;
		}
		if (level != cacheLevel) {
			CACHE.clear();
			cacheLevel = level;
		}

		String key = tag.toString();
		LivingEntity cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}

		try {
			ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
			Optional<Entity> created = EntityType.by(input)
					.flatMap(type -> EntityType.create(type, input, level, EntitySpawnReason.BUCKET));
			if (created.isPresent() && created.get() instanceof LivingEntity living) {
				CACHE.put(key, living);
				return living;
			}
		} catch (Exception ignored) {
			// A mob we can't rebuild client-side just shows no face; the bucket still works.
		}
		return null;
	}

	@Override
	public void submit(LivingEntity entity, PoseStack pose, SubmitNodeCollector collector,
						int light, int overlay, boolean hasFoil, int outlineColor) {
		if (entity == null) {
			return;
		}
		try {
			EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
			EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
			EntityRenderState state = renderer.createRenderState(entity, 1.0F);
			state.shadowPieces.clear();
			state.outlineColor = 0;
			// Face the viewer, upright, no tilt.
			if (state instanceof LivingEntityRenderState living) {
				living.bodyRot = 0.0F;
				living.yRot = 0.0F;
				living.xRot = 0.0F;
			}

			pose.pushPose();
			pose.translate(0.5F, yOffset, 0.5F);
			pose.scale(scale, scale, scale);
			pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(rotationDegrees)));
			dispatcher.submit(state, CAMERA, 0.0, 0.0, 0.0, pose, collector);
			pose.popPose();
		} catch (Exception ignored) {
			// Never let a rendering hiccup crash the game.
		}
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		output.accept(new Vector3f(0.0F, 0.0F, 0.0F));
		output.accept(new Vector3f(1.0F, 1.0F, 1.0F));
	}

	public record Unbaked(float scale, float yOffset, float rotationDegrees)
			implements SpecialModelRenderer.Unbaked<LivingEntity> {

		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.FLOAT.optionalFieldOf("scale", 0.5F).forGetter(Unbaked::scale),
				Codec.FLOAT.optionalFieldOf("y_offset", 0.25F).forGetter(Unbaked::yOffset),
				Codec.FLOAT.optionalFieldOf("rotation", 180.0F).forGetter(Unbaked::rotationDegrees)
		).apply(i, Unbaked::new));

		@Override
		public SpecialModelRenderer<LivingEntity> bake(SpecialModelRenderer.BakingContext context) {
			return new CapturedMobRenderer(scale, yOffset, rotationDegrees);
		}

		@Override
		public MapCodec<? extends SpecialModelRenderer.Unbaked<LivingEntity>> type() {
			return MAP_CODEC;
		}
	}
}
