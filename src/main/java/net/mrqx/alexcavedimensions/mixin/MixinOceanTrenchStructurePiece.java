package net.mrqx.alexcavedimensions.mixin;

import com.github.alexmodguy.alexscaves.server.level.structure.piece.OceanTrenchStructurePiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.mrqx.alexcavedimensions.AlexCavesDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OceanTrenchStructurePiece.class)
public class MixinOceanTrenchStructurePiece {

    @Unique
    private WorldGenLevel alex_caves_dimensions$level = null;

    @Inject(method = "getTrenchRadius", at = @At("RETURN"), cancellable = true, remap = false)
    private void injectGetRadiusSq(int x, int z, CallbackInfoReturnable<Double> cir) {
        if (alex_caves_dimensions$level instanceof WorldGenRegion worldGenRegion) {
            ResourceLocation dimensionId = worldGenRegion.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).getKey(worldGenRegion.dimensionType());
            if (dimensionId != null && dimensionId.getNamespace().equals(AlexCavesDimensions.MODID)) {
                cir.setReturnValue(cir.getReturnValue() * 10);
            }
        }
    }

    @Inject(
        method = "postProcess(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/core/BlockPos;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void injectPostProcess(
        WorldGenLevel level,
        StructureManager featureManager,
        ChunkGenerator chunkGen,
        RandomSource random,
        BoundingBox boundingBox,
        ChunkPos chunkPos,
        BlockPos blockPos,
        CallbackInfo ci
    ) {
        this.alex_caves_dimensions$level = level;
    }
}
