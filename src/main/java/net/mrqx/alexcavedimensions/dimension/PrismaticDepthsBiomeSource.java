package net.mrqx.alexcavedimensions.dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.mrqx.alexcavedimensions.config.PrismaticDepthsConfig;
import org.jetbrains.annotations.NotNull;

import java.util.List;
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
    }

    @Override
    protected @NotNull MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected @NotNull Stream<Holder<Biome>> collectPossibleBiomes() {
        return PrismaticDepthsConfig.verticalOrder().stream().map(this::holderForBiome);
    }

    @Override
    public @NotNull Holder<Biome> getNoiseBiome(int x, int y, int z, @NotNull Climate.Sampler sampler) {
        if (PrismaticDepthsConfig.usesVerticalLayers()) {
            return biomeForVerticalY(QuartPos.toBlock(y));
        }
        return biomeForClimate(sampler.sample(x, y, z));
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

    private @NotNull Holder<Biome> biomeForClimate(Climate.TargetPoint point) {
        List<Integer> centers = PrismaticDepthsConfig.multiNoiseCenters();
        List<String> order = PrismaticDepthsConfig.verticalOrder();
        int winner = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int index = 0; index < order.size(); index++) {
            long temperatureDistance = point.temperature() - centers.get(index * 2);
            long humidityDistance = point.humidity() - centers.get(index * 2 + 1);
            long distance = temperatureDistance * temperatureDistance + humidityDistance * humidityDistance;
            if (distance < bestDistance) {
                bestDistance = distance;
                winner = index;
            }
        }
        return holderForBiome(order.get(winner));
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
}
