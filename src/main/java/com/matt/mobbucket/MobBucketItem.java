package com.matt.mobbucket;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A bucket that can scoop up (almost) any living entity - passive or hostile -
 * and release it elsewhere.
 *
 * <p>Capture and release state live entirely in the stack's {@code CUSTOM_DATA}
 * component: an empty bucket has no {@code CapturedEntity} tag, a full one stores
 * the captured entity's complete save data (including its {@code id}) so that the
 * exact same creature - health, age, variant, custom name and all - comes back out.
 */
public class MobBucketItem extends Item {
	private static final String ENTITY_TAG = "CapturedEntity";
	private static final String NAME_TAG = "CapturedName";

	public MobBucketItem(Properties properties) {
		super(properties);
	}

	private static CompoundTag storedData(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag();
	}

	private static boolean isFilled(ItemStack stack) {
		return storedData(stack).contains(ENTITY_TAG);
	}

	// --- Capture: right-click a living entity with an empty bucket ------------

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
		Level level = entity.level();

		// Only non-players can be scooped, and a full bucket is busy already.
		if (entity instanceof Player || isFilled(stack) || !entity.isAlive()) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		// Serialise the whole entity (26.3 uses the ValueOutput API).
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		if (!entity.save(output)) {
			// Some entities (bosses, players, ...) refuse to serialise - leave them be.
			return InteractionResult.PASS;
		}
		CompoundTag entityTag = output.buildResult();

		CompoundTag root = storedData(stack);
		root.put(ENTITY_TAG, entityTag);
		root.putString(NAME_TAG, entity.getDisplayName().getString());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

		entity.discard();

		level.playSound(null, player.blockPosition(), SoundEvents.BUCKET_FILL_FISH, SoundSource.PLAYERS, 1.0F, 1.0F);
		return InteractionResult.SUCCESS;
	}

	// --- Release: right-click a block with a full bucket ----------------------

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		if (!isFilled(stack)) {
			return InteractionResult.PASS;
		}

		Level level = context.getLevel();
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		CompoundTag root = storedData(stack);
		CompoundTag entityTag = root.getCompoundOrEmpty(ENTITY_TAG);

		// Drop the creature on the face of the block that was clicked.
		BlockPos placePos = context.getClickedPos().relative(context.getClickedFace());

		ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), entityTag);
		Optional<Entity> created = EntityType.by(input)
				.flatMap(type -> EntityType.create(type, input, level, EntitySpawnReason.BUCKET));
		if (created.isEmpty()) {
			return InteractionResult.PASS;
		}

		Entity entity = created.get();
		entity.snapTo(
				placePos.getX() + 0.5D,
				placePos.getY(),
				placePos.getZ() + 0.5D,
				entity.getYRot(),
				entity.getXRot());
		level.addFreshEntity(entity);

		// Empty the bucket again.
		root.remove(ENTITY_TAG);
		root.remove(NAME_TAG);
		if (root.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_DATA);
		} else {
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
		}

		level.playSound(null, placePos, SoundEvents.BUCKET_EMPTY_FISH, SoundSource.PLAYERS, 1.0F, 1.0F);
		return InteractionResult.SUCCESS;
	}

	// --- Tooltip --------------------------------------------------------------

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
								Consumer<Component> adder, TooltipFlag flag) {
		CompoundTag root = storedData(stack);
		if (root.contains(ENTITY_TAG)) {
			String name = root.getStringOr(NAME_TAG, "Unknown");
			adder.accept(Component.translatable("item.mobbucket.mob_bucket.contains", name)
					.withStyle(ChatFormatting.GRAY));
		} else {
			adder.accept(Component.translatable("item.mobbucket.mob_bucket.empty_hint")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
