package net.mrqx.alexcavedimensions.item;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.mrqx.alexcavedimensions.network.CaveKeyLoadingPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = AlexCavesDimensions.MODID)
public class ItemCaveKey extends Item {

    private static final int TELEPORT_DELAY_TICKS = 2;
    private static final Map<MinecraftServer, Map<UUID, PendingTeleport>> PENDING_TELEPORTS = new HashMap<>();
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
        requestTeleport(pLivingEntity, server, targetDimension(pLevel, caveDimension));
        return pStack;
    }

    private static @NotNull ResourceKey<Level> targetDimension(@NotNull Level level, @NotNull ResourceKey<Level> caveDimension) {
        return level.dimension().equals(caveDimension) ? Level.OVERWORLD : caveDimension;
    }

    private static void requestTeleport(
        @NotNull LivingEntity livingEntity,
        @NotNull MinecraftServer server,
        @NotNull ResourceKey<Level> targetDimension
    ) {
        ServerLevel targetLevel = server.getLevel(targetDimension);
        if (targetLevel == null) {
            AlexCavesDimensions.LOGGER.error("Cannot begin cave key teleport: target dimension {} is missing", targetDimension.location());
            return;
        }
        setLoadingScreen(livingEntity, true);
        if (livingEntity instanceof ServerPlayer player) {
            PENDING_TELEPORTS.computeIfAbsent(server, ignored -> new HashMap<>()).put(
                player.getUUID(),
                new PendingTeleport(targetDimension, server.getTickCount() + TELEPORT_DELAY_TICKS)
            );
            return;
        }
        teleportToDimension(livingEntity, targetLevel);
    }

    private static void teleportToDimension(@NotNull LivingEntity livingEntity, @NotNull ServerLevel targetLevel) {
        Vec3 teleportPosition = findTeleportPosition(livingEntity, targetLevel);
        if (teleportPosition == null) {
            setLoadingScreen(livingEntity, false);
            AlexCavesDimensions.LOGGER.error("Cannot teleport cave key user: no valid spawn found in target dimension {}", targetLevel.dimension().location());
            return;
        }
        if (livingEntity.changeDimension(new DimensionTransition(
            targetLevel,
            teleportPosition,
            Vec3.ZERO,
            livingEntity.getYRot(),
            livingEntity.getXRot(),
            DimensionTransition.DO_NOTHING
        )) == null) {
            setLoadingScreen(livingEntity, false);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        Map<UUID, PendingTeleport> serverTeleports = PENDING_TELEPORTS.get(server);
        if (serverTeleports == null) {
            return;
        }
        Iterator<Map.Entry<UUID, PendingTeleport>> iterator = serverTeleports.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingTeleport> entry = iterator.next();
            PendingTeleport pending = entry.getValue();
            if (pending.executeTick() > tick) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            ServerLevel targetLevel = server.getLevel(pending.targetDimension());
            if (targetLevel == null) {
                setLoadingScreen(player, false);
                AlexCavesDimensions.LOGGER.error("Cannot complete delayed cave key teleport: target dimension {} is missing", pending.targetDimension().location());
                continue;
            }
            teleportToDimension(player, targetLevel);
        }
        if (serverTeleports.isEmpty()) {
            PENDING_TELEPORTS.remove(server);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_TELEPORTS.remove(event.getServer());
    }

    private static void setLoadingScreen(LivingEntity livingEntity, boolean loading) {
        if (livingEntity instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new CaveKeyLoadingPayload(loading));
        }
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

    private record PendingTeleport(
        ResourceKey<Level> targetDimension,
        int executeTick
    ) {}
}
