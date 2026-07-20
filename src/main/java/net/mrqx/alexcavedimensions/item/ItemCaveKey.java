package net.mrqx.alexcavedimensions.item;

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
import net.mrqx.alexcavedimensions.AlexCavesDimensions;
import net.mrqx.alexcavedimensions.config.PrismaticDepthsConfig;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
        if (targetLevel.dimension().equals(AlexCavesDimensions.PRISMATIC_DEPTHS_RESOURCE_KEY) && PrismaticDepthsConfig.usesVerticalLayers()) {
            Vec3 configuredPosition = findConfiguredVerticalPosition(livingEntity, targetLevel);
            if (configuredPosition != null) {
                return configuredPosition;
            }
        }
        return findTeleportPosition(livingEntity, targetLevel, targetLevel.getMaxBuildHeight() - 1, targetLevel.getMinBuildHeight());
    }

    private static Vec3 findConfiguredVerticalPosition(@NotNull LivingEntity livingEntity, @NotNull ServerLevel targetLevel) {
        String spawnBiome = PrismaticDepthsConfig.verticalSpawnBiome();
        if (spawnBiome.isEmpty()) {
            return null;
        }
        List<String> order = PrismaticDepthsConfig.verticalOrder();
        int index = order.indexOf(spawnBiome);
        if (index < 0) {
            return null;
        }
        List<Integer> boundaries = PrismaticDepthsConfig.verticalBoundaries();
        int minY = boundaries.get(index);
        int maxY = boundaries.get(index + 1) - 1;
        int middleY = minY + (maxY - minY) / 2;
        for (int offset = 0; offset <= maxY - minY; offset++) {
            int upperY = middleY + offset;
            if (upperY <= maxY) {
                Vec3 upperPosition = findTeleportPosition(livingEntity, targetLevel, upperY, upperY);
                if (upperPosition != null) {
                    return upperPosition;
                }
            }
            int lowerY = middleY - offset;
            if (lowerY >= minY && lowerY != upperY) {
                Vec3 lowerPosition = findTeleportPosition(livingEntity, targetLevel, lowerY, lowerY);
                if (lowerPosition != null) {
                    return lowerPosition;
                }
            }
        }
        return null;
    }

    private static Vec3 findTeleportPosition(@NotNull LivingEntity livingEntity, @NotNull ServerLevel targetLevel, int maxY, int minY) {
        for (int i = maxY; i >= minY; i--) {
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
