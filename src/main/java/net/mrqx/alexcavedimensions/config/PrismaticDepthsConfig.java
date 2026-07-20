package net.mrqx.alexcavedimensions.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public final class PrismaticDepthsConfig {

    private static final List<String> DEFAULT_VERTICAL_ORDER = List.of(
        "primordial_caves", "toxic_caves", "magnetic_caves", "forlorn_hollows", "candy_cavity", "abyssal_chasm"
    );
    private static final List<Integer> DEFAULT_VERTICAL_BOUNDARIES = List.of(-64, 0, 64, 128, 192, 256, 320);
    private static final List<Integer> DEFAULT_MULTI_NOISE_CENTERS = List.of(
        -7500, -5000, -2500, -5000, 2500, -5000, 7500, -5000, -5000, 5000, 5000, 5000
    );

    public static final PrismaticDepthsConfig INSTANCE;
    public static final ModConfigSpec SPEC;

    static {
        Pair<PrismaticDepthsConfig, ModConfigSpec> configured = new ModConfigSpec.Builder().configure(PrismaticDepthsConfig::new);
        INSTANCE = configured.getLeft();
        SPEC = configured.getRight();
    }

    public final ModConfigSpec.EnumValue<GenerationMode> generationMode;
    public final ModConfigSpec.IntValue mixedDimensionCount;
    public final ModConfigSpec.ConfigValue<List<? extends String>> verticalLayerOrder;
    public final ModConfigSpec.ConfigValue<List<? extends Integer>> verticalLayerBoundaries;
    public final ModConfigSpec.ConfigValue<String> verticalSpawnBiome;
    public final ModConfigSpec.ConfigValue<List<? extends Integer>> multiNoiseCenters;

    private PrismaticDepthsConfig(ModConfigSpec.Builder builder) {
        builder.push("prismatic_depths");
        generationMode = builder.comment("Generation mode for Prismatic Depths.")
                                .defineEnum("generation_mode", GenerationMode.MULTI_NOISE);
        mixedDimensionCount = builder.comment("Number of Alex's Caves biomes mixed into Prismatic Depths (2-6).")
                                  .defineInRange("mixed_dimension_count", 6, 2, 6);
        verticalLayerOrder = builder.comment("Unique biome paths ordered from bottom to top. The first mixed_dimension_count entries are used.")
                                    .defineList(
                                        List.of("vertical_layer_order"),
                                        () -> DEFAULT_VERTICAL_ORDER,
                                        () -> "",
                                        value -> value instanceof String biome && (biome.isEmpty() || isSupportedBiome(biome)),
                                        ModConfigSpec.Range.of(2, 6)
                                    );
        verticalLayerBoundaries = builder.comment("Absolute Y boundaries for mixed_dimension_count half-open intervals in [-64, 320).")
                                          .defineList(
                                              "vertical_layer_boundaries",
                                              () -> DEFAULT_VERTICAL_BOUNDARIES,
                                              () -> 0,
                                              value -> value instanceof Integer boundary && boundary >= -64 && boundary <= 320
                                          );
        verticalSpawnBiome = builder.comment("Target biome path for vertical-mode entry. Empty keeps the normal top-down search.")
                                    .define(
                                        "vertical_spawn_biome",
                                        "",
                                        value -> value instanceof String biome && (biome.isEmpty() || isSupportedBiome(biome))
                                    );
         multiNoiseCenters = builder.comment("Temperature and humidity centers for the active biomes, in vertical_layer_order order.")
                                    .defineList(
                                        "multi_noise_centers",
                                        () -> DEFAULT_MULTI_NOISE_CENTERS,
                                        () -> 0,
                                        value -> value instanceof Integer center && center >= -10000 && center <= 10000);
        builder.pop();
    }

    public static boolean usesVerticalLayers() {
        return INSTANCE.generationMode.get() == GenerationMode.VERTICAL;
    }

    public static int mixedDimensionCount() {
        return INSTANCE.mixedDimensionCount.get();
    }

    public static List<String> verticalOrder() {
        List<? extends String> configured = INSTANCE.verticalLayerOrder.get();
        int count = mixedDimensionCount();
        if (configured.size() >= count && configured.size() <= 6
            && configured.stream().distinct().count() == configured.size()
            && configured.stream().allMatch(PrismaticDepthsConfig::isSupportedBiome)) {
            return List.copyOf(configured.subList(0, count));
        }
        return List.copyOf(DEFAULT_VERTICAL_ORDER.subList(0, count));
    }

    public static List<Integer> verticalBoundaries() {
        List<? extends Integer> configured = INSTANCE.verticalLayerBoundaries.get();
        int count = mixedDimensionCount();
        if (configured.size() != count + 1 || configured.getFirst() != -64 || configured.getLast() != 320) {
            return defaultVerticalBoundaries(count);
        }
        for (int index = 1; index < configured.size(); index++) {
            if (configured.get(index - 1) >= configured.get(index)) {
                return defaultVerticalBoundaries(count);
            }
        }
        return List.copyOf(configured);
    }

    private static List<Integer> defaultVerticalBoundaries(int count) {
        List<Integer> boundaries = new java.util.ArrayList<>(count + 1);
        for (int index = 0; index <= count; index++) {
            boundaries.add(-64 + (384 * index / count));
        }
        return List.copyOf(boundaries);
    }

    public static String verticalSpawnBiome() {
        String biome = INSTANCE.verticalSpawnBiome.get();
        return verticalOrder().contains(biome) ? biome : "";
    }

    public static List<Integer> multiNoiseCenters() {
        List<? extends Integer> configured = INSTANCE.multiNoiseCenters.get();
        int required = mixedDimensionCount() * 2;
        if (configured.size() >= required && configured.size() <= DEFAULT_MULTI_NOISE_CENTERS.size()) {
            return List.copyOf(configured.subList(0, required));
        }
        return List.copyOf(DEFAULT_MULTI_NOISE_CENTERS.subList(0, required));
    }

    public static boolean isSupportedBiome(String biome) {
        return DEFAULT_VERTICAL_ORDER.contains(biome);
    }

    public enum GenerationMode {
        MULTI_NOISE,
        VERTICAL
    }
}
