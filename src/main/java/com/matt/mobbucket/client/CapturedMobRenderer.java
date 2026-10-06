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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Renders the captured mob as a small live model peeking out of the bucket.
 *
 * <p>The per-stack argument is the stored entity's NBT ({@link CompoundTag}),
 * NOT the entity itself: the game uses the argument as a model-identity key and
 * hashes it, and a client-only entity with no assigned id throws from
 * {@code Entity#hashCode}. The entity is reconstructed (and cached) from that
 * tag at submit time, and given a fake negative id so nothing in the render
 * path trips on {@code getId()} either.
 *
 * <p>Placement (scale / vertical offset / rotation) is read from the item model
 * JSON so it can be tuned without recompiling.
 */
public class CapturedMobRenderer implements SpecialModelRenderer<CompoundTag> {
	private static final String ENTITY_TAG = "CapturedEntity";
	private static final CameraRenderState CAMERA = new CameraRenderState();
	private static final AtomicInteger FAKE_ID = new AtomicInteger(-100_000);

	// Bounded cache of reconstructed client-side entities, keyed by their NBT
	// string, so we don't rebuild an entity every frame. Cleared on level change.
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
	public CompoundTag extractArgument(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag root = data.copyTag();
		return root.contains(ENTITY_TAG) ? root.getCompoundOrEmpty(ENTITY_TAG) : null;
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
				// Normalise facing so every mob looks forward regardless of the
				// yaw it was captured at. This must happen before the render state
				// is built, since the state bakes in the entity's rotation.
				living.setYRot(0.0F);
				living.setXRot(0.0F);
				living.setYHeadRot(0.0F);
				living.setYBodyRot(0.0F);
				living.yHeadRotO = 0.0F;
				living.yBodyRotO = 0.0F;
				living.setOldPosAndRot();
				living.setId(FAKE_ID.getAndDecrement());
				CACHE.put(key, living);
				return living;
			}
		} catch (Exception ignored) {
			// A mob we can't rebuild client-side just shows no face; the bucket still works.
		}
		return null;
	}

	@Override
	public void submit(CompoundTag tag, PoseStack pose, SubmitNodeCollector collector,
						int light, int overlay, boolean hasFoil, int outlineColor) {
		if (tag == null) {
			return;
		}
		LivingEntity entity = getOrCreate(tag);
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

			// Auto-fit: scale every mob to the same on-screen height ("scale" is
			// the target height) and center it vertically, so a chicken and a cow
			// frame the same way instead of one being a blob and one being legs.
			float bbHeight = Math.max(state.boundingBoxHeight, 0.1F);
			float fit = scale / bbHeight;

			pose.pushPose();
			pose.translate(0.5F, yOffset, 0.5F);
			pose.mulPose(new Matrix4f().rotationY((float) Math.toRadians(rotationDegrees)));
			pose.scale(fit, fit, fit);
			pose.translate(0.0F, -bbHeight * 0.5F, 0.0F);
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
			implements SpecialModelRenderer.Unbaked<CompoundTag> {

		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.FLOAT.optionalFieldOf("scale", 0.22F).forGetter(Unbaked::scale),
				Codec.FLOAT.optionalFieldOf("y_offset", 0.3F).forGetter(Unbaked::yOffset),
				Codec.FLOAT.optionalFieldOf("rotation", 0.0F).forGetter(Unbaked::rotationDegrees)
		).apply(i, Unbaked::new));

		@Override
		public SpecialModelRenderer<CompoundTag> bake(SpecialModelRenderer.BakingContext context) {
			return new CapturedMobRenderer(scale, yOffset, rotationDegrees);
		}

		@Override
		public MapCodec<? extends SpecialModelRenderer.Unbaked<CompoundTag>> type() {
			return MAP_CODEC;
		}
	}
}
