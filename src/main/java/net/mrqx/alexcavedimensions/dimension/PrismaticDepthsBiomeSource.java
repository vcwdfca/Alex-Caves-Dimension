package net.mrqx.alexcavedimensions.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.mrqx.alexcavedimensions.config.PrismaticDepthsConfig;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class PrismaticDepthsBiomeSource extends BiomeSource {

    public static final MapCodec<PrismaticDepthsBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Biome.CODEC.fieldOf("abyssal_chasm").forGetter(source -> source.abyssalChasm),
        Biome.CODEC.fieldOf("candy_cavity").forGetter(source -> source.candyCavity),
        Biome.CODEC.fieldOf("forlorn_hollows").forGetter(source -> source.forlornHollows),
        Biome.CODEC.fieldOf("magnetic_caves").forGetter(source -> source.magneticCaves),
        Biome.CODEC.fieldOf("primordial_caves").forGetter(source -> source.primordialCaves),
        Biome.CODEC.fieldOf("toxic_caves").forGetter(source -> source.toxicCaves)
    ).apply(instance, PrismaticDepthsBiomeSource::new));

    private final Holder<Biome> abyssalChasm;
    private final Holder<Biome> candyCavity;
    private final Holder<Biome> forlornHollows;
    private final Holder<Biome> magneticCaves;
    private final Holder<Biome> primordialCaves;
    private final Holder<Biome> toxicCaves;
    private final List<HorizontalBiome> horizontalBiomes;
    private final List<HorizontalBiomeGroup> individualHorizontalBiomeGroups;
    private final double horizontalSampleScale;

    public PrismaticDepthsBiomeSource(
        Holder<Biome> abyssalChasm,
        Holder<Biome> candyCavity,
        Holder<Biome> forlornHollows,
        Holder<Biome> magneticCaves,
        Holder<Biome> primordialCaves,
        Holder<Biome> toxicCaves
    ) {
        this.abyssalChasm = abyssalChasm;
        this.candyCavity = candyCavity;
        this.forlornHollows = forlornHollows;
        this.magneticCaves = magneticCaves;
        this.primordialCaves = primordialCaves;
        this.toxicCaves = toxicCaves;
        this.horizontalBiomes = this.createHorizontalBiomes();
        this.individualHorizontalBiomeGroups = this.createIndividualHorizontalBiomeGroups();
        this.horizontalSampleScale = PrismaticDepthsConfig.horizontalBiomeSampleMultiplier();
    }

    @Override
    protected @NotNull MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected @NotNull Stream<Holder<Biome>> collectPossibleBiomes() {
        List<String> order = PrismaticDepthsConfig.verticalOrder();
        if (!PrismaticDepthsConfig.usesVerticalLayers()
            && PrismaticDepthsConfig.horizontalDensityMode() == PrismaticDepthsConfig.HorizontalDensityMode.INDIVIDUAL
            && order.stream().anyMatch(biome -> PrismaticDepthsConfig.individualBiomeSampleMultiplier(biome) > 0)) {
            return order.stream()
                .filter(biome -> PrismaticDepthsConfig.individualBiomeSampleMultiplier(biome) > 0)
                .map(this::holderForBiome);
        }
        return order.stream().map(this::holderForBiome);
    }

    @Override
    public @NotNull Holder<Biome> getNoiseBiome(int x, int y, int z, @NotNull Climate.Sampler sampler) {
        if (PrismaticDepthsConfig.usesVerticalLayers()) {
            return biomeForVerticalY(QuartPos.toBlock(y));
        }
        if (PrismaticDepthsConfig.horizontalDensityMode() == PrismaticDepthsConfig.HorizontalDensityMode.INDIVIDUAL) {
            return biomeForIndividualDensity(x, z, sampler);
        }
        // Multi-noise mode is a horizontal tiling mode. Sampling a fixed climate
        // elevation keeps each cave biome consistent throughout its vertical column.
        return biomeForDensity(x, z, this.horizontalSampleScale, this.horizontalBiomes, sampler);
    }

    private @NotNull Holder<Biome> biomeForVerticalY(int y) {
        List<Integer> boundaries = PrismaticDepthsConfig.verticalBoundaries();
        int index = 0;
        while (index < boundaries.size() - 1 && y >= boundaries.get(index + 1)) {
            index++;
        }
        List<String> order = PrismaticDepthsConfig.verticalOrder();
        String biome = order.get(Math.min(index, order.size() - 1));
        return holderForBiome(biome);
    }

    private List<HorizontalBiome> createHorizontalBiomes() {
        List<Integer> centers = PrismaticDepthsConfig.multiNoiseCenters();
        List<String> order = PrismaticDepthsConfig.verticalOrder();
        List<HorizontalBiome> biomes = new ArrayList<>(order.size());
        for (int index = 0; index < order.size(); index++) {
            biomes.add(new HorizontalBiome(
                this.holderForBiome(order.get(index)),
                centers.get(index * 2),
                centers.get(index * 2 + 1)
            ));
        }
        return List.copyOf(biomes);
    }

    private List<HorizontalBiomeGroup> createIndividualHorizontalBiomeGroups() {
        List<Integer> centers = PrismaticDepthsConfig.multiNoiseCenters();
        List<String> order = PrismaticDepthsConfig.verticalOrder();
        boolean equalMultiplierFallback = order.stream().noneMatch(biome -> PrismaticDepthsConfig.individualBiomeSampleMultiplier(biome) > 0);
        Map<Double, List<HorizontalBiome>> biomesByMultiplier = new LinkedHashMap<>();
        for (int index = 0; index < order.size(); index++) {
            String biome = order.get(index);
            double multiplier = equalMultiplierFallback ? 1.0 : PrismaticDepthsConfig.individualBiomeSampleMultiplier(biome);
            if (multiplier == 0) {
                continue;
            }
            biomesByMultiplier.computeIfAbsent(multiplier, ignored -> new ArrayList<>()).add(new HorizontalBiome(
                this.holderForBiome(biome),
                centers.get(index * 2),
                centers.get(index * 2 + 1)
            ));
        }
        return biomesByMultiplier.entrySet().stream()
            .map(entry -> new HorizontalBiomeGroup(entry.getKey(), List.copyOf(entry.getValue())))
            .toList();
    }

    private @NotNull Holder<Biome> biomeForIndividualDensity(int x, int z, Climate.Sampler sampler) {
        HorizontalBiome selected = this.individualHorizontalBiomeGroups.getFirst().biomes().getFirst();
        long nearestDistance = Long.MAX_VALUE;
        for (HorizontalBiomeGroup group : this.individualHorizontalBiomeGroups) {
            ClimateSample sample = sampleClimate(x, z, group.sampleScale(), sampler);
            for (HorizontalBiome biome : group.biomes()) {
                long distance = biome.distanceTo(sample);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    selected = biome;
                }
            }
        }
        return selected.biome();
    }

    private static @NotNull Holder<Biome> biomeForDensity(
        int x,
        int z,
        double scale,
        List<HorizontalBiome> biomes,
        Climate.Sampler sampler
    ) {
        ClimateSample sample = sampleClimate(x, z, scale, sampler);
        HorizontalBiome selected = biomes.getFirst();
        long nearestDistance = Long.MAX_VALUE;
        for (HorizontalBiome biome : biomes) {
            long distance = biome.distanceTo(sample);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                selected = biome;
            }
        }
        return selected.biome();
    }

    private static ClimateSample sampleClimate(int x, int z, double scale, Climate.Sampler sampler) {
        int sampleX = QuartPos.toBlock(Mth.floor((double)x * scale));
        int sampleZ = QuartPos.toBlock(Mth.floor((double)z * scale));
        DensityFunction.SinglePointContext context = new DensityFunction.SinglePointContext(sampleX, 0, sampleZ);
        return new ClimateSample(
            Climate.quantizeCoord((float)sampler.temperature().compute(context)),
            Climate.quantizeCoord((float)sampler.humidity().compute(context))
        );
    }

    private @NotNull Holder<Biome> holderForBiome(String biome) {
        return switch (biome) {
            case "abyssal_chasm" -> abyssalChasm;
            case "candy_cavity" -> candyCavity;
            case "forlorn_hollows" -> forlornHollows;
            case "magnetic_caves" -> magneticCaves;
            case "toxic_caves" -> toxicCaves;
            default -> primordialCaves;
        };
    }

    private record HorizontalBiome(Holder<Biome> biome, long temperatureCenter, long humidityCenter) {
        private long distanceTo(ClimateSample sample) {
            long temperatureDistance = sample.temperature() - this.temperatureCenter;
            long humidityDistance = sample.humidity() - this.humidityCenter;
            return temperatureDistance * temperatureDistance + humidityDistance * humidityDistance;
        }
    }

    private record HorizontalBiomeGroup(double sampleScale, List<HorizontalBiome> biomes) {}

    private record ClimateSample(long temperature, long humidity) {}
}
