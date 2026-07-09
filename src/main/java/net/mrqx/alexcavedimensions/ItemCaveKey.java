package net.mrqx.alexcavedimensions;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class ItemCaveKey extends Item {

    private final ResourceKey<Level> caveDimension;

    public ItemCaveKey(Properties properties, ResourceKey<Level> caveDimension) {
        super(properties);
        this.caveDimension = caveDimension;
    }
    
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level pLevel, Player pPlayer, @NotNull InteractionHand pUsedHand) {
        ItemStack itemStack = pPlayer.getItemInHand(pUsedHand);
        pPlayer.startUsingItem(pUsedHand);
        pPlayer.swing(pUsedHand);
        return InteractionResultHolder.consume(itemStack);
    }
    
    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack pStack, @NotNull Level pLevel, @NotNull LivingEntity pLivingEntity) {
        super.finishUsingItem(pStack, pLevel, pLivingEntity);
        MinecraftServer server = pLivingEntity.getServer();
        if (server == null) {
            return pStack;
        }
        teleportToDimension(pLivingEntity, server, targetDimension(pLevel, caveDimension));
        return pStack;
    }

    private static @NotNull ResourceKey<Level> targetDimension(@NotNull Level level, @NotNull ResourceKey<Level> caveDimension) {
        return level.dimension().equals(caveDimension) ? Level.OVERWORLD : caveDimension;
    }

    private static void teleportToDimension(
        @NotNull LivingEntity livingEntity,
        @NotNull MinecraftServer server,
        @NotNull ResourceKey<Level> targetDimension
    ) {
        ServerLevel targetLevel = server.getLevel(targetDimension);
        if (targetLevel == null) {
            AlexCavesDimensions.LOGGER.error("Cannot teleport cave key user: target dimension {} is missing", targetDimension.location());
            return;
        }
        Vec3 teleportPosition = findTeleportPosition(livingEntity, targetLevel);
        if (teleportPosition == null) {
            AlexCavesDimensions.LOGGER.error("Cannot teleport cave key user: no valid spawn found in target dimension {}", targetDimension.location());
            return;
        }
        livingEntity.changeDimension(new DimensionTransition(
            targetLevel,
            teleportPosition,
            Vec3.ZERO,
            livingEntity.getYRot(),
            livingEntity.getXRot(),
            DimensionTransition.DO_NOTHING
        ));
    }

    private static Vec3 findTeleportPosition(@NotNull LivingEntity livingEntity, @NotNull ServerLevel targetLevel) {
        for (int i = targetLevel.getHeight() + targetLevel.getMinBuildHeight(); i > targetLevel.getMinBuildHeight(); i--) {
            BlockPos pos = new BlockPos((int) livingEntity.getX(), i, (int) livingEntity.getZ());
            if (targetLevel.getBlockState(pos).isValidSpawn(targetLevel, pos, livingEntity.getType())) {
                return livingEntity.position().add(0, i + 1 - livingEntity.position().y, 0);
            }
        }
        return null;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return 1;
    }
}
