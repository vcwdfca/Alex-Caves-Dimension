package net.mrqx.alexcavedimensions.dimension;

import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.mrqx.alexcavedimensions.config.PrismaticDepthsConfig;
import net.mrqx.alexcavedimensions.mixin.AccessorNoiseChunk;
import org.jetbrains.annotations.NotNull;

public class PrismaticDepthsChunkGenerator extends NoiseBasedChunkGenerator {
    public static final MapCodec<PrismaticDepthsChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
        p_255585_ -> p_255585_.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(p_255584_ -> p_255584_.biomeSource),
                    NoiseGeneratorSettings.CODEC.fieldOf("abyssal_chasm").forGetter(generator -> generator.settings.getFirst()),
                    NoiseGeneratorSettings.CODEC.fieldOf("candy_cavity").forGetter(generator -> generator.settings.get(1)),
                    NoiseGeneratorSettings.CODEC.fieldOf("forlorn_hollows").forGetter(generator -> generator.settings.get(2)),
                    NoiseGeneratorSettings.CODEC.fieldOf("magnetic_caves").forGetter(generator -> generator.settings.get(3)),
                    NoiseGeneratorSettings.CODEC.fieldOf("primordial_caves").forGetter(generator -> generator.settings.get(4)),
                    NoiseGeneratorSettings.CODEC.fieldOf("toxic_caves").forGetter(generator -> generator.settings.get(5))
                )
                .apply(p_255585_, p_255585_.stable(PrismaticDepthsChunkGenerator::new))
    );
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final int HORIZONTAL_ABYSSAL_SEA_LEVEL = 63;
    private static final int HORIZONTAL_ABYSSAL_TERRAIN_DROP = 64;
    private static final int HORIZONTAL_ABYSSAL_BLEND_RADIUS = 3;
    private final List<Holder<NoiseGeneratorSettings>> settings;
    private final List<Supplier<Aquifer.FluidPicker>> fluidPickers;
    private final List<NoiseBasedChunkGenerator> generators;
    private final List<SurfaceRules.RuleSource> surfaceRules;
    private final SurfaceRules.RuleSource surfaceRule;
    private final NoiseSettings noiseSettings;

    public PrismaticDepthsChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> abyssalChasm, Holder<NoiseGeneratorSettings> candyCavity,
                                         Holder<NoiseGeneratorSettings> forlornHollows, Holder<NoiseGeneratorSettings> magneticCaves,
                                         Holder<NoiseGeneratorSettings> primordialCaves, Holder<NoiseGeneratorSettings> toxicCaves) {
        // Horizontal mode uses this base generator as one continuous noise pipeline.
        // Vertical mode still dispatches each layer to its matching cave settings.
        super(biomeSource, primordialCaves);
        this.settings = List.of(abyssalChasm, candyCavity, forlornHollows, magneticCaves, primordialCaves, toxicCaves);
        this.noiseSettings = primordialCaves.value().noiseSettings();
        this.validateCompatibleNoiseSettings();
        List<Supplier<Aquifer.FluidPicker>> fluidPickers = new ArrayList<>(this.settings.size());
        for (Holder<NoiseGeneratorSettings> settingsHolder : this.settings) {
            NoiseGeneratorSettings setting = settingsHolder.value();
            fluidPickers.add(Suppliers.memoize(() -> createFluidPicker(setting, setting.seaLevel())));
        }
        this.fluidPickers = List.copyOf(fluidPickers);
        this.generators = this.settings.stream().map(setting -> new NoiseBasedChunkGenerator(biomeSource, setting)).toList();
        this.surfaceRules = List.of(
            SurfaceRules.ifTrue(SurfaceRules.isBiome(biomeKey("abyssal_chasm")), abyssalChasm.value().surfaceRule()),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(biomeKey("candy_cavity")), candyCavity.value().surfaceRule()),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(biomeKey("forlorn_hollows")), forlornHollows.value().surfaceRule()),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(biomeKey("magnetic_caves")), magneticCaves.value().surfaceRule()),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(biomeKey("primordial_caves")), primordialCaves.value().surfaceRule()),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(biomeKey("toxic_caves")), toxicCaves.value().surfaceRule())
        );
        this.surfaceRule = SurfaceRules.sequence(
            this.surfaceRules.toArray(SurfaceRules.RuleSource[]::new)
        );
    }

    private void validateCompatibleNoiseSettings() {
        for (Holder<NoiseGeneratorSettings> setting : this.settings) {
            if (!this.noiseSettings.equals(setting.value().noiseSettings())) {
                throw new IllegalArgumentException("All Prismatic Depths noise settings must use identical min_y, height, and cell sizes.");
            }
        }
    }

    private static ResourceKey<Biome> biomeKey(String path) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("alexscaves", path));
    }

    private static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings, int seaLevel) {
        Aquifer.FluidStatus aquifer$fluidstatus = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        Aquifer.FluidStatus aquifer$fluidstatus1 = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        return (p_224274_, p_224275_, p_224276_) -> p_224275_ < Math.min(-54, seaLevel) ? aquifer$fluidstatus : aquifer$fluidstatus1;
    }

    @Override
    public @NotNull CompletableFuture<ChunkAccess> createBiomes(@NotNull RandomState randomState,
                                                                @NotNull Blender blender,
                                                                @NotNull StructureManager structureManager,
                                                                @NotNull ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("init_biomes", () -> {
            this.doCreateBiomes(blender, randomState, chunk);
            return chunk;
        }), Util.backgroundExecutor());
    }

    private void doCreateBiomes(Blender blender, RandomState random, ChunkAccess chunk) {
        BiomeResolver biomeresolver = BelowZeroRetrogen.getBiomeResolver(blender.getBiomeResolver(this.biomeSource), chunk);
        chunk.fillBiomesFromNoise(biomeresolver, random.sampler());
    }

    private NoiseChunk createNoiseChunk(ChunkAccess chunk, StructureManager structureManager, Blender blender, RandomState random) {
        return NoiseChunk.forChunk(
            chunk,
            random,
            Beardifier.forStructuresInChunk(structureManager, chunk.getPos()),
            this.settings.get(4).value(),
            PrismaticDepthsConfig.usesVerticalLayers() ? this.fluidPickers.get(4).get() : this.createHorizontalFluidPicker(random),
            blender
        );
    }

    private Aquifer.FluidPicker createHorizontalFluidPicker(RandomState random) {
        Map<Long, Integer> settingsIndexCache = new HashMap<>();
        Aquifer.FluidPicker abyssalFluidPicker = createFluidPicker(this.settings.getFirst().value(), HORIZONTAL_ABYSSAL_SEA_LEVEL);
        return (x, y, z) -> {
            int settingsIndex = this.settingsIndexAt(x, y, z, random, settingsIndexCache);
            Aquifer.FluidPicker fluidPicker = settingsIndex == 0 ? abyssalFluidPicker : this.fluidPickers.get(settingsIndex).get();
            return fluidPicker.computeFluid(x, y, z);
        };
    }

    @Override
    protected @NotNull MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.@NotNull Types type, @NotNull LevelHeightAccessor level, @NotNull RandomState random) {
        if (!PrismaticDepthsConfig.usesVerticalLayers()) {
            return super.getBaseHeight(x, z, type, level, random);
        }
        NoiseColumn column = this.getBaseColumn(x, z, level, random);
        for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
            if (type.isOpaque().test(column.getBlock(y))) {
                return y + 1;
            }
        }
        return level.getMinBuildHeight();
    }

    @Override
    public @NotNull NoiseColumn getBaseColumn(int x, int z, @NotNull LevelHeightAccessor height, @NotNull RandomState random) {
        if (!PrismaticDepthsConfig.usesVerticalLayers()) {
            return super.getBaseColumn(x, z, height, random);
        }
        NoiseSettings noiseSettings = this.settings.get(4).value().noiseSettings().clampToHeightAccessor(height);
        BlockState[] states = new BlockState[noiseSettings.height()];
        NoiseColumn[] columns = new NoiseColumn[this.settings.size()];
        Map<Long, Integer> settingsIndexCache = new HashMap<>();
        for (int y = noiseSettings.minY(); y < noiseSettings.minY() + noiseSettings.height(); y++) {
            int settingsIndex = this.settingsIndexAt(x, y, z, random, settingsIndexCache);
            if (columns[settingsIndex] == null) {
                columns[settingsIndex] = this.generators.get(settingsIndex).getBaseColumn(x, z, height, random);
            }
            states[y - noiseSettings.minY()] = columns[settingsIndex].getBlock(this.verticalDensityY(y));
        }
        return new NoiseColumn(noiseSettings.minY(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        DecimalFormat decimalformat = new DecimalFormat("0.000");
        NoiseRouter noiserouter = random.router();
        DensityFunction.SinglePointContext densityfunction$singlepointcontext = new DensityFunction.SinglePointContext(
            pos.getX(), pos.getY(), pos.getZ()
        );
        double d0 = noiserouter.ridges().compute(densityfunction$singlepointcontext);
        info.add(
            "NoiseRouter T: "
                + decimalformat.format(noiserouter.temperature().compute(densityfunction$singlepointcontext))
                + " V: "
                + decimalformat.format(noiserouter.vegetation().compute(densityfunction$singlepointcontext))
                + " C: "
                + decimalformat.format(noiserouter.continents().compute(densityfunction$singlepointcontext))
                + " E: "
                + decimalformat.format(noiserouter.erosion().compute(densityfunction$singlepointcontext))
                + " D: "
                + decimalformat.format(noiserouter.depth().compute(densityfunction$singlepointcontext))
                + " W: "
                + decimalformat.format(d0)
                + " PV: "
                + decimalformat.format(NoiseRouterData.peaksAndValleys((float)d0))
                + " AS: "
                + decimalformat.format(noiserouter.initialDensityWithoutJaggedness().compute(densityfunction$singlepointcontext))
                + " N: "
                + decimalformat.format(noiserouter.finalDensity().compute(densityfunction$singlepointcontext))
        );
    }

    @Override
    public void buildSurface(@NotNull WorldGenRegion level, @NotNull StructureManager structureManager, @NotNull RandomState random, @NotNull ChunkAccess chunk) {
        if (!SharedConstants.debugVoidTerrain(chunk.getPos())) {
            WorldGenerationContext worldgenerationcontext = new WorldGenerationContext(this, level);
            this.buildSurface(
                chunk,
                worldgenerationcontext,
                random,
                structureManager,
                level.getBiomeManager(),
                level.registryAccess().registryOrThrow(Registries.BIOME),
                Blender.of(level)
            );
        }
    }

    public void buildSurface(
        @NotNull ChunkAccess chunk,
        @NotNull WorldGenerationContext context,
        @NotNull RandomState random,
        @NotNull StructureManager structureManager,
        @NotNull BiomeManager biomeManager,
        @NotNull Registry<Biome> biomes,
        @NotNull Blender blender
    ) {
        if (!PrismaticDepthsConfig.usesVerticalLayers()) {
            random.surfaceSystem().buildSurface(
                random,
                biomeManager,
                biomes,
                this.settings.get(4).value().useLegacyRandomSource(),
                context,
                chunk,
                this.primaryNoiseChunk(chunk, structureManager, blender, random),
                this.surfaceRule
            );
            return;
        }
        Map<Long, Integer> settingsIndexCache = new HashMap<>();
        boolean[] activeSettings = this.activeSettingsInChunk(chunk.getPos(), random, settingsIndexCache);
        for (int index = 0; index < this.settings.size(); index++) {
            if (!activeSettings[index]) {
                continue;
            }
            NoiseChunk noiseChunk = index == 4
                ? this.primaryNoiseChunk(chunk, structureManager, blender, random)
                : NoiseChunk.forChunk(
                    chunk,
                    random,
                    Beardifier.forStructuresInChunk(structureManager, chunk.getPos()),
                    this.settings.get(index).value(),
                    this.fluidPickers.get(index).get(),
                    blender
                );
            random.surfaceSystem().buildSurface(
                random,
                biomeManager,
                biomes,
                this.settings.get(index).value().useLegacyRandomSource(),
                context,
                chunk,
                noiseChunk,
                this.surfaceRules.get(index)
            );
        }
    }

    @Override
    public void applyCarvers(
        @NotNull WorldGenRegion level,
        long seed,
        @NotNull RandomState random,
        @NotNull BiomeManager biomeManager,
        @NotNull StructureManager structureManager,
        @NotNull ChunkAccess chunk,
        GenerationStep.@NotNull Carving step
    ) {
        if (!PrismaticDepthsConfig.usesVerticalLayers()) {
            super.applyCarvers(level, seed, random, biomeManager, structureManager, chunk, step);
            return;
        }
        BiomeManager biomemanager = biomeManager.withDifferentSource(
            (p_255581_, p_255582_, p_255583_) -> this.biomeSource.getNoiseBiome(p_255581_, p_255582_, p_255583_, random.sampler())
        );
        WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        ChunkPos chunkpos = chunk.getPos();
        Blender blender = Blender.of(level);
        Map<Long, Integer> settingsIndexCache = new HashMap<>();
        boolean[] activeSettings = this.activeSettingsInChunk(chunkpos, random, settingsIndexCache);
        NoiseChunk[] noiseChunks = new NoiseChunk[this.settings.size()];
        int contextIndex = -1;
        for (int index = 0; index < this.settings.size(); index++) {
            if (!activeSettings[index]) {
                continue;
            }
            noiseChunks[index] = index == 4
                ? this.primaryNoiseChunk(chunk, structureManager, blender, random)
                : NoiseChunk.forChunk(
                    chunk,
                    random,
                    Beardifier.forStructuresInChunk(structureManager, chunk.getPos()),
                    this.settings.get(index).value(),
                    this.fluidPickers.get(index).get(),
                    blender
                );
            if (contextIndex < 0) {
                contextIndex = index;
            }
        }
        int carvingContextIndex = contextIndex;
        Aquifer mixedAquifer = new Aquifer() {
            @Override
            public BlockState computeSubstance(@NotNull DensityFunction.FunctionContext context, double substance) {
                int settingsIndex = settingsIndexAt(context.blockX(), context.blockY(), context.blockZ(), random, settingsIndexCache);
                return noiseChunks[settingsIndex]
                    .aquifer()
                    .computeSubstance(context, substance);
            }

            @Override
            public boolean shouldScheduleFluidUpdate() {
                for (NoiseChunk noiseChunk : noiseChunks) {
                    if (noiseChunk != null && noiseChunk.aquifer().shouldScheduleFluidUpdate()) {
                        return true;
                    }
                }
                return false;
            }
        };
        CarvingContext carvingContext = new CarvingContext(
            this.generators.get(carvingContextIndex),
            level.registryAccess(),
            chunk.getHeightAccessorForGeneration(),
            noiseChunks[carvingContextIndex],
            random,
            this.surfaceRule
        );
        CarvingMask carvingmask = ((ProtoChunk)chunk).getOrCreateCarvingMask(step);
        List<Holder<ConfiguredWorldCarver<?>>> verticalCarvers = this.activeCarvers(step);

        for (int j = -8; j <= 8; j++) {
            for (int k = -8; k <= 8; k++) {
                ChunkPos chunkpos1 = new ChunkPos(chunkpos.x + j, chunkpos.z + k);
                int l = 0;

                for (Holder<ConfiguredWorldCarver<?>> holder : verticalCarvers) {
                    ConfiguredWorldCarver<?> configuredworldcarver = holder.value();
                    worldgenrandom.setLargeFeatureSeed(seed + (long)l, chunkpos1.x, chunkpos1.z);
                    if (configuredworldcarver.isStartChunk(worldgenrandom)) {
                        configuredworldcarver.carve(carvingContext, chunk, biomemanager::getBiome, worldgenrandom, mixedAquifer, chunkpos1, carvingmask);
                    }

                    l++;
                }
            }
        }
    }

    private List<Holder<ConfiguredWorldCarver<?>>> activeCarvers(GenerationStep.Carving step) {
        Set<Holder<ConfiguredWorldCarver<?>>> carvers = new LinkedHashSet<>();
        for (Holder<Biome> biome : this.biomeSource.possibleBiomes()) {
            for (Holder<ConfiguredWorldCarver<?>> carver : biome.value().getGenerationSettings().getCarvers(step)) {
                carvers.add(carver);
            }
        }
        return List.copyOf(carvers);
    }

    @Override
    public @NotNull CompletableFuture<ChunkAccess> fillFromNoise(@NotNull Blender blender,
                                                                 @NotNull RandomState randomState,
                                                                 @NotNull StructureManager structureManager,
                                                                 @NotNull ChunkAccess chunk) {
        NoiseSettings noisesettings = this.settings.get(4).value().noiseSettings().clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
        int i = noisesettings.minY();
        int j = Mth.floorDiv(i, noisesettings.getCellHeight());
        int k = Mth.floorDiv(noisesettings.height(), noisesettings.getCellHeight());
        return k <= 0 ? CompletableFuture.completedFuture(chunk) : CompletableFuture.supplyAsync(Util.wrapThreadWithTaskName("wgen_fill_noise", () -> {
            int l = chunk.getSectionIndex(k * noisesettings.getCellHeight() - 1 + i);
            int i1 = chunk.getSectionIndex(i);
            Set<LevelChunkSection> set = Sets.newHashSet();

            for (int j1 = l; j1 >= i1; j1--) {
                LevelChunkSection levelchunksection = chunk.getSection(j1);
                levelchunksection.acquire();
                set.add(levelchunksection);
            }

            ChunkAccess chunkaccess;
            try {
                chunkaccess = this.doFill(blender, structureManager, randomState, chunk, j, k);
            } finally {
                for (LevelChunkSection levelchunksection1 : set) {
                    levelchunksection1.release();
                }
            }

            return chunkaccess;
        }), Util.backgroundExecutor());
    }

    private ChunkAccess doFill(Blender blender, StructureManager structureManager, RandomState random, ChunkAccess chunk, int minCellY, int cellCountY) {
        boolean vertical = PrismaticDepthsConfig.usesVerticalLayers();
        Map<Long, Integer> settingsIndexCache = new HashMap<>();
        HorizontalAbyssalTerrain abyssalTerrain = this.horizontalAbyssalTerrain(chunk.getPos(), random, settingsIndexCache, vertical);
        boolean[] activeSettings = new boolean[this.settings.size()];
        if (vertical) {
            activeSettings = this.activeSettingsInChunk(chunk.getPos(), random, settingsIndexCache);
        } else {
            activeSettings[4] = true;
        }
        NoiseChunk[] noiseChunks = new NoiseChunk[this.settings.size()];
        List<NoiseChunk> interpolatingNoiseChunks = new ArrayList<>(this.settings.size());
        for (int index = 0; index < this.settings.size(); index++) {
            if (activeSettings[index]) {
                NoiseChunk noiseChunk = index == 4
                    ? this.primaryNoiseChunk(chunk, structureManager, blender, random)
                    : NoiseChunk.forChunk(
                        chunk,
                        random,
                        Beardifier.forStructuresInChunk(structureManager, chunk.getPos()),
                        this.settings.get(index).value(),
                        this.fluidPickers.get(index).get(),
                        blender
                    );
                noiseChunks[index] = noiseChunk;
                interpolatingNoiseChunks.add(noiseChunk);
            }
        }
        Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap heightmap1 = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        ChunkPos chunkpos = chunk.getPos();
        int i = chunkpos.getMinBlockX();
        int j = chunkpos.getMinBlockZ();
        interpolatingNoiseChunks.forEach(NoiseChunk::initializeForFirstCellX);
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        int k = this.noiseSettings.getCellWidth();
        int l = this.noiseSettings.getCellHeight();
        int i1 = 16 / k;
        int j1 = 16 / k;

        for (int k1 = 0; k1 < i1; k1++) {
            for (NoiseChunk noiseChunk : interpolatingNoiseChunks) noiseChunk.advanceCellX(k1);

            for (int l1 = 0; l1 < j1; l1++) {
                int i2 = chunk.getSectionsCount() - 1;
                LevelChunkSection levelchunksection = chunk.getSection(i2);
                int[] selectedDensityCells = new int[this.settings.size()];
                java.util.Arrays.fill(selectedDensityCells, Integer.MIN_VALUE);

                for (int j2 = cellCountY - 1; j2 >= 0; j2--) {
                    for (int k2 = l - 1; k2 >= 0; k2--) {
                        int l2 = (minCellY + j2) * l + k2;
                        int i3 = l2 & 15;
                        int j3 = chunk.getSectionIndex(l2);
                        if (i2 != j3) {
                            i2 = j3;
                            levelchunksection = chunk.getSection(j3);
                        }

                        for (int k3 = 0; k3 < k; k3++) {
                            int l3 = i + k1 * k + k3;
                            int i4 = l3 & 15;
                            double d1 = (double)k3 / (double)k;

                            for (int j4 = 0; j4 < k; j4++) {
                                int k4 = j + l1 * k + j4;
                                int l4 = k4 & 15;
                                double d2 = (double)j4 / (double)k;
                                int settingsIndex = vertical ? this.settingsIndexAt(l3, l2, k4, random, settingsIndexCache) : 4;
                                boolean abyssalBiome = vertical
                                    ? settingsIndex == 0
                                    : abyssalTerrain.biomeColumns()[(i4 >> 2) + (l4 >> 2) * 4];
                                NoiseChunk noisechunk = noiseChunks[settingsIndex];
                                int densityY = vertical
                                    ? this.verticalDensityY(l2)
                                    : Mth.clamp(
                                        l2 + abyssalTerrain.terrainDrops()[(i4 >> 2) + (l4 >> 2) * 4],
                                        this.noiseSettings.minY(),
                                        this.noiseSettings.minY() + this.noiseSettings.height() - 1
                                    );
                                int densityCell = Mth.floorDiv(densityY, l) - minCellY;
                                if (selectedDensityCells[settingsIndex] != densityCell) {
                                    noisechunk.selectCellYZ(densityCell, l1);
                                    selectedDensityCells[settingsIndex] = densityCell;
                                }
                                noisechunk.updateForY(densityY, (double)Math.floorMod(densityY, l) / (double)l);
                                noisechunk.updateForX(l3, d1);
                                noisechunk.updateForZ(k4, d2);
                                BlockState blockstate = ((AccessorNoiseChunk) noisechunk).alex_caves_dimensions$getInterpolatedState();
                                if (blockstate == null) {
                                    blockstate = this.settings.get(settingsIndex).value().defaultBlock();
                                }
                                if (!vertical && abyssalBiome && blockstate.isAir() && l2 < HORIZONTAL_ABYSSAL_SEA_LEVEL) {
                                    blockstate = this.settings.getFirst().value().defaultFluid();
                                }

                                if (blockstate != AIR && !SharedConstants.debugVoidTerrain(chunk.getPos())) {
                                    levelchunksection.setBlockState(i4, i3, l4, blockstate, false);
                                    heightmap.update(i4, l2, l4, blockstate);
                                    heightmap1.update(i4, l2, l4, blockstate);
                                    if (!blockstate.getFluidState().isEmpty()
                                        && (noisechunk.aquifer().shouldScheduleFluidUpdate() || !vertical && abyssalBiome)) {
                                        blockpos$mutableblockpos.set(l3, l2, k4);
                                        chunk.markPosForPostprocessing(blockpos$mutableblockpos);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            interpolatingNoiseChunks.forEach(NoiseChunk::swapSlices);
        }

        interpolatingNoiseChunks.forEach(NoiseChunk::stopInterpolation);
        return chunk;
    }

    private HorizontalAbyssalTerrain horizontalAbyssalTerrain(
        ChunkPos chunkPos,
        RandomState random,
        Map<Long, Integer> settingsIndexCache,
        boolean vertical
    ) {
        if (vertical) {
            return HorizontalAbyssalTerrain.EMPTY;
        }
        boolean[] biomeColumns = new boolean[16];
        int[] terrainDrops = new int[16];
        int minQuartX = QuartPos.fromBlock(chunkPos.getMinBlockX());
        int minQuartZ = QuartPos.fromBlock(chunkPos.getMinBlockZ());
        int diameter = HORIZONTAL_ABYSSAL_BLEND_RADIUS * 2 + 1;
        int sampleCount = diameter * diameter;
        for (int localQuartX = 0; localQuartX < 4; localQuartX++) {
            for (int localQuartZ = 0; localQuartZ < 4; localQuartZ++) {
                int index = localQuartX + localQuartZ * 4;
                int quartX = minQuartX + localQuartX;
                int quartZ = minQuartZ + localQuartZ;
                biomeColumns[index] = this.settingsIndexAtQuart(quartX, 0, quartZ, random, settingsIndexCache) == 0;
                int abyssalSamples = 0;
                for (int offsetX = -HORIZONTAL_ABYSSAL_BLEND_RADIUS; offsetX <= HORIZONTAL_ABYSSAL_BLEND_RADIUS; offsetX++) {
                    for (int offsetZ = -HORIZONTAL_ABYSSAL_BLEND_RADIUS; offsetZ <= HORIZONTAL_ABYSSAL_BLEND_RADIUS; offsetZ++) {
                        if (this.settingsIndexAtQuart(quartX + offsetX, 0, quartZ + offsetZ, random, settingsIndexCache) == 0) {
                            abyssalSamples++;
                        }
                    }
                }
                terrainDrops[index] = Mth.floor((double)HORIZONTAL_ABYSSAL_TERRAIN_DROP * abyssalSamples / sampleCount);
            }
        }
        return new HorizontalAbyssalTerrain(biomeColumns, terrainDrops);
    }

    private NoiseChunk primaryNoiseChunk(ChunkAccess chunk, StructureManager structureManager, Blender blender, RandomState random) {
        return chunk.getOrCreateNoiseChunk(ignored -> this.createNoiseChunk(chunk, structureManager, blender, random));
    }

    private int settingsIndexAt(int x, int y, int z, RandomState random, Map<Long, Integer> cache) {
        return this.settingsIndexAtQuart(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), random, cache);
    }

    private int settingsIndexAtQuart(int quartX, int quartY, int quartZ, RandomState random, Map<Long, Integer> cache) {
        long position = BlockPos.asLong(quartX, quartY, quartZ);
        return cache.computeIfAbsent(
            position,
            ignored -> this.settingsIndexForBiome(this.biomeSource.getNoiseBiome(quartX, quartY, quartZ, random.sampler()))
        );
    }

    private boolean[] activeSettingsInChunk(
        ChunkPos chunkPos,
        RandomState random,
        Map<Long, Integer> settingsIndexCache
    ) {
        boolean[] active = new boolean[this.settings.size()];
        int minQuartX = QuartPos.fromBlock(chunkPos.getMinBlockX());
        int maxQuartX = QuartPos.fromBlock(chunkPos.getMaxBlockX());
        int minQuartZ = QuartPos.fromBlock(chunkPos.getMinBlockZ());
        int maxQuartZ = QuartPos.fromBlock(chunkPos.getMaxBlockZ());
        int minQuartY = QuartPos.fromBlock(this.noiseSettings.minY());
        int maxQuartY = QuartPos.fromBlock(this.noiseSettings.minY() + this.noiseSettings.height() - 1);
        for (int quartX = minQuartX; quartX <= maxQuartX; quartX++) {
            for (int quartZ = minQuartZ; quartZ <= maxQuartZ; quartZ++) {
                for (int quartY = minQuartY; quartY <= maxQuartY; quartY++) {
                    active[this.settingsIndexAtQuart(quartX, quartY, quartZ, random, settingsIndexCache)] = true;
                }
            }
        }
        return active;
    }

    private int verticalDensityY(int worldY) {
        List<Integer> boundaries = PrismaticDepthsConfig.verticalBoundaries();
        int layer = 0;
        while (layer < boundaries.size() - 1 && worldY >= boundaries.get(layer + 1)) {
            layer++;
        }
        int layerStart = boundaries.get(layer);
        int layerHeight = boundaries.get(layer + 1) - layerStart;
        return this.noiseSettings.minY() + Math.floorDiv((worldY - layerStart) * this.noiseSettings.height(), layerHeight);
    }

    private int settingsIndexForBiome(Holder<Biome> biome) {
        if (biome.is(biomeKey("abyssal_chasm"))) return 0;
        if (biome.is(biomeKey("candy_cavity"))) return 1;
        if (biome.is(biomeKey("forlorn_hollows"))) return 2;
        if (biome.is(biomeKey("magnetic_caves"))) return 3;
        if (biome.is(biomeKey("toxic_caves"))) return 5;
        return 4;
    }

    @Override
    public int getGenDepth() {
        return this.settings.get(4).value().noiseSettings().height();
    }

    @Override
    public int getSeaLevel() {
        return this.settings.get(4).value().seaLevel();
    }

    @Override
    public int getMinY() {
        return this.settings.get(4).value().noiseSettings().minY();
    }

    @Override
    public void spawnOriginalMobs(@NotNull WorldGenRegion level) {
        if (!PrismaticDepthsConfig.usesVerticalLayers()) {
            super.spawnOriginalMobs(level);
            return;
        }
        ChunkPos chunkpos = level.getCenter();
        Holder<Biome> holder = level.getBiome(chunkpos.getWorldPosition().atY(level.getMaxBuildHeight() - 1));
        this.generators.get(this.settingsIndexForBiome(holder)).spawnOriginalMobs(level);
    }

    private record HorizontalAbyssalTerrain(boolean[] biomeColumns, int[] terrainDrops) {
        private static final HorizontalAbyssalTerrain EMPTY = new HorizontalAbyssalTerrain(new boolean[0], new int[0]);
    }
}
