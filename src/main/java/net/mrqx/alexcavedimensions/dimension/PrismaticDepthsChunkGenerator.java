package net.mrqx.alexcavedimensions.dimension;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.level.biome.BiomeGenerationSettings;
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
import net.mrqx.alexcavedimensions.mixin.AccessorNoiseChunk;
import org.jetbrains.annotations.NotNull;

public class PrismaticDepthsChunkGenerator extends ChunkGenerator {
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
    private final List<Holder<NoiseGeneratorSettings>> settings;
    private final List<Supplier<Aquifer.FluidPicker>> fluidPickers;
    private final List<NoiseBasedChunkGenerator> generators;
    private final List<SurfaceRules.RuleSource> surfaceRules;
    private final SurfaceRules.RuleSource surfaceRule;

    public PrismaticDepthsChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> abyssalChasm, Holder<NoiseGeneratorSettings> candyCavity,
                                         Holder<NoiseGeneratorSettings> forlornHollows, Holder<NoiseGeneratorSettings> magneticCaves,
                                         Holder<NoiseGeneratorSettings> primordialCaves, Holder<NoiseGeneratorSettings> toxicCaves) {
        super(biomeSource);
        this.settings = List.of(abyssalChasm, candyCavity, forlornHollows, magneticCaves, primordialCaves, toxicCaves);
        this.fluidPickers = this.settings.stream().<Supplier<Aquifer.FluidPicker>>map(setting -> Suppliers.memoize(() -> createFluidPicker(setting.value()))).toList();
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

    private static ResourceKey<Biome> biomeKey(String path) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("alexscaves", path));
    }

    private static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        Aquifer.FluidStatus aquifer$fluidstatus = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int i = settings.seaLevel();
        Aquifer.FluidStatus aquifer$fluidstatus1 = new Aquifer.FluidStatus(i, settings.defaultFluid());
        return (p_224274_, p_224275_, p_224276_) -> p_224275_ < Math.min(-54, i) ? aquifer$fluidstatus : aquifer$fluidstatus1;
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
            this.fluidPickers.get(4).get(),
            blender
        );
    }

    @Override
    protected @NotNull MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.@NotNull Types type, @NotNull LevelHeightAccessor level, @NotNull RandomState random) {
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
        NoiseSettings noiseSettings = this.settings.get(4).value().noiseSettings().clampToHeightAccessor(height);
        BlockState[] states = new BlockState[noiseSettings.height()];
        List<NoiseColumn> columns = this.generators.stream().map(generator -> generator.getBaseColumn(x, z, height, random)).toList();
        for (int y = noiseSettings.minY(); y < noiseSettings.minY() + noiseSettings.height(); y++) {
            states[y - noiseSettings.minY()] = columns.get(this.settingsIndexAt(x, y, z, random)).getBlock(y);
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
    public void buildSurface(@NotNull WorldGenRegion level, @NotNull StructureManager structureManager, @NotNull RandomState random, ChunkAccess chunk) {
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

    @VisibleForTesting
    public void buildSurface(
        ChunkAccess chunk,
        WorldGenerationContext context,
        RandomState random,
        StructureManager structureManager,
        BiomeManager biomeManager,
        Registry<Biome> biomes,
        Blender blender
    ) {
        NoiseChunk primary = this.primaryNoiseChunk(chunk, structureManager, blender, random);
        List<NoiseChunk> noiseChunks = new ArrayList<>(this.settings.size());
        for (int index = 0; index < this.settings.size(); index++) {
            noiseChunks.add(index == 4 ? primary : NoiseChunk.forChunk(chunk, random, Beardifier.forStructuresInChunk(structureManager, chunk.getPos()), this.settings.get(index).value(), this.fluidPickers.get(index).get(), blender));
        }
        for (int index = 0; index < this.settings.size(); index++) {
            random.surfaceSystem().buildSurface(
                random,
                biomeManager,
                biomes,
                this.settings.get(index).value().useLegacyRandomSource(),
                context,
                chunk,
                noiseChunks.get(index),
                this.surfaceRules.get(index)
            );
        }
    }

    @Override
    public void applyCarvers(
        @NotNull WorldGenRegion level,
        long seed,
        @NotNull RandomState random,
        BiomeManager biomeManager,
        @NotNull StructureManager structureManager,
        ChunkAccess chunk,
        GenerationStep.@NotNull Carving step
    ) {
        BiomeManager biomemanager = biomeManager.withDifferentSource(
            (p_255581_, p_255582_, p_255583_) -> this.biomeSource.getNoiseBiome(p_255581_, p_255582_, p_255583_, random.sampler())
        );
        WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        ChunkPos chunkpos = chunk.getPos();
        Blender blender = Blender.of(level);
        NoiseChunk primary = this.primaryNoiseChunk(chunk, structureManager, blender, random);
        List<NoiseChunk> noiseChunks = new ArrayList<>(this.settings.size());
        for (int index = 0; index < this.settings.size(); index++) {
            NoiseChunk noiseChunk = index == 4 ? primary : NoiseChunk.forChunk(chunk, random, Beardifier.forStructuresInChunk(structureManager, chunk.getPos()), this.settings.get(index).value(), this.fluidPickers.get(index).get(), blender);
            noiseChunks.add(noiseChunk);
        }
        Aquifer mixedAquifer = new Aquifer() {
            @Override
            public BlockState computeSubstance(DensityFunction.FunctionContext context, double substance) {
                return noiseChunks.get(settingsIndexAt(context.blockX(), context.blockY(), context.blockZ(), random)).aquifer().computeSubstance(context, substance);
            }

            @Override
            public boolean shouldScheduleFluidUpdate() {
                return noiseChunks.stream().anyMatch(noiseChunk -> noiseChunk.aquifer().shouldScheduleFluidUpdate());
            }
        };
        CarvingContext carvingContext = new CarvingContext(this.generators.get(4), level.registryAccess(), chunk.getHeightAccessorForGeneration(), primary, random, this.surfaceRule);
        CarvingMask carvingmask = ((ProtoChunk)chunk).getOrCreateCarvingMask(step);

        for (int j = -8; j <= 8; j++) {
            for (int k = -8; k <= 8; k++) {
                ChunkPos chunkpos1 = new ChunkPos(chunkpos.x + j, chunkpos.z + k);
                Holder<Biome> sourceBiome = this.biomeSource.getNoiseBiome(
                    QuartPos.fromBlock(chunkpos1.getMinBlockX()), 0, QuartPos.fromBlock(chunkpos1.getMinBlockZ()), random.sampler()
                );
                BiomeGenerationSettings biomegenerationsettings = sourceBiome.value().getGenerationSettings();
                Iterable<Holder<ConfiguredWorldCarver<?>>> iterable = biomegenerationsettings.getCarvers(step);
                int l = 0;

                for (Holder<ConfiguredWorldCarver<?>> holder : iterable) {
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
        NoiseChunk primary = this.primaryNoiseChunk(chunk, structureManager, blender, random);
        List<NoiseChunk> noiseChunks = new ArrayList<>(this.settings.size());
        for (int index = 0; index < this.settings.size(); index++) {
            noiseChunks.add(index == 4 ? primary : NoiseChunk.forChunk(chunk, random, Beardifier.forStructuresInChunk(structureManager, chunk.getPos()), this.settings.get(index).value(), this.fluidPickers.get(index).get(), blender));
        }
        Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap heightmap1 = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        ChunkPos chunkpos = chunk.getPos();
        int i = chunkpos.getMinBlockX();
        int j = chunkpos.getMinBlockZ();
        noiseChunks.forEach(NoiseChunk::initializeForFirstCellX);
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        int k = ((AccessorNoiseChunk) primary).alex_caves_dimensions$getCellWidth();
        int l = ((AccessorNoiseChunk) primary).alex_caves_dimensions$getCellHeight();
        int i1 = 16 / k;
        int j1 = 16 / k;

        for (int k1 = 0; k1 < i1; k1++) {
            for (NoiseChunk noiseChunk : noiseChunks) noiseChunk.advanceCellX(k1);

            for (int l1 = 0; l1 < j1; l1++) {
                int i2 = chunk.getSectionsCount() - 1;
                LevelChunkSection levelchunksection = chunk.getSection(i2);

                for (int j2 = cellCountY - 1; j2 >= 0; j2--) {
                    for (NoiseChunk noiseChunk : noiseChunks) noiseChunk.selectCellYZ(j2, l1);

                    for (int k2 = l - 1; k2 >= 0; k2--) {
                        int l2 = (minCellY + j2) * l + k2;
                        int i3 = l2 & 15;
                        int j3 = chunk.getSectionIndex(l2);
                        if (i2 != j3) {
                            i2 = j3;
                            levelchunksection = chunk.getSection(j3);
                        }

                        double d0 = (double)k2 / (double)l;
                        for (NoiseChunk noiseChunk : noiseChunks) noiseChunk.updateForY(l2, d0);

                        for (int k3 = 0; k3 < k; k3++) {
                            int l3 = i + k1 * k + k3;
                            int i4 = l3 & 15;
                            double d1 = (double)k3 / (double)k;
                            for (NoiseChunk noiseChunk : noiseChunks) noiseChunk.updateForX(l3, d1);

                            for (int j4 = 0; j4 < k; j4++) {
                                int k4 = j + l1 * k + j4;
                                int l4 = k4 & 15;
                                double d2 = (double)j4 / (double)k;
                                for (NoiseChunk noiseChunk : noiseChunks) noiseChunk.updateForZ(k4, d2);
                                int settingsIndex = this.settingsIndexAt(l3, l2, k4, random);
                                NoiseChunk noisechunk = noiseChunks.get(settingsIndex);
                                BlockState blockstate = ((AccessorNoiseChunk) noisechunk).alex_caves_dimensions$getInterpolatedState();
                                if (blockstate == null) {
                                    blockstate = this.settings.get(settingsIndex).value().defaultBlock();
                                }

                                if (blockstate != AIR && !SharedConstants.debugVoidTerrain(chunk.getPos())) {
                                    levelchunksection.setBlockState(i4, i3, l4, blockstate, false);
                                    heightmap.update(i4, l2, l4, blockstate);
                                    heightmap1.update(i4, l2, l4, blockstate);
                                    if (noisechunk.aquifer().shouldScheduleFluidUpdate() && !blockstate.getFluidState().isEmpty()) {
                                        blockpos$mutableblockpos.set(l3, l2, k4);
                                        chunk.markPosForPostprocessing(blockpos$mutableblockpos);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            noiseChunks.forEach(NoiseChunk::swapSlices);
        }

        noiseChunks.forEach(NoiseChunk::stopInterpolation);
        return chunk;
    }

    private NoiseChunk primaryNoiseChunk(ChunkAccess chunk, StructureManager structureManager, Blender blender, RandomState random) {
        return chunk.getOrCreateNoiseChunk(ignored -> this.createNoiseChunk(chunk, structureManager, blender, random));
    }

    private int settingsIndexAt(int x, int y, int z, RandomState random) {
        return this.settingsIndexForBiome(this.biomeSource.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), random.sampler()));
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
        ChunkPos chunkpos = level.getCenter();
        Holder<Biome> holder = level.getBiome(chunkpos.getWorldPosition().atY(level.getMaxBuildHeight() - 1));
        this.generators.get(this.settingsIndexForBiome(holder)).spawnOriginalMobs(level);
    }
}
