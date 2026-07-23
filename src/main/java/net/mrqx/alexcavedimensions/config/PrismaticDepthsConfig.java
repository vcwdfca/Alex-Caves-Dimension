package net.mrqx.alexcavedimensions.config;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

public final class PrismaticDepthsConfig {

    private static final String TRANSLATION_PREFIX = "alex_caves_dimensions.configuration.prismatic_depths.";
    private static final List<String> SUPPORTED_BIOMES = List.of(
        "primordial_caves", "toxic_caves", "magnetic_caves", "forlorn_hollows", "candy_cavity", "abyssal_chasm"
    );
    private static final List<String> DEFAULT_VERTICAL_ORDER = SUPPORTED_BIOMES;
    private static final List<Integer> DEFAULT_VERTICAL_BOUNDARIES = List.of(-64, 0, 64, 128, 192, 256, 320);
    private static final List<Integer> LEGACY_MULTI_NOISE_CENTERS = List.of(
        -7500, -5000, -2500, -5000, 2500, -5000, 7500, -5000, -5000, 5000, 5000, 5000
    );
    private static final List<Integer> DEFAULT_MULTI_NOISE_CENTERS = List.of(
        -6000, -5000, 0, -5000, 6000, -5000, -6000, 5000, 0, 5000, 6000, 5000
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
    public final ModConfigSpec.EnumValue<HorizontalDensityMode> horizontalDensityMode;
    public final ModConfigSpec.DoubleValue horizontalBiomeSampleMultiplier;
    public final Map<String, ModConfigSpec.DoubleValue> individualBiomeSampleMultipliers;
    public final ModConfigSpec.ConfigValue<List<? extends String>> verticalLayerOrder;
    public final ModConfigSpec.ConfigValue<List<? extends Integer>> verticalLayerBoundaries;
    public final ModConfigSpec.ConfigValue<String> verticalSpawnBiome;
    public final ModConfigSpec.ConfigValue<List<? extends Integer>> multiNoiseCenters;

    private PrismaticDepthsConfig(ModConfigSpec.Builder builder) {
        builder.comment("World generation settings for Prismatic Depths. Changes apply to newly generated chunks after restarting the world.")
            .translation(TRANSLATION_PREFIX.substring(0, TRANSLATION_PREFIX.length() - 1))
            .push("prismatic_depths");
        generationMode = builder.comment("Choose either climate-based mixing or stacked vertical layers.")
                                .translation(TRANSLATION_PREFIX + "generation_mode")
                                .worldRestart()
                                .defineEnum("generation_mode", GenerationMode.MULTI_NOISE);
        mixedDimensionCount = builder.comment("Number of Alex's Caves biomes mixed into Prismatic Depths, from 2 to 6.")
                                  .translation(TRANSLATION_PREFIX + "mixed_dimension_count")
                                  .worldRestart()
                                  .defineInRange("mixed_dimension_count", 6, 2, 6);
        builder.comment("Settings used only by horizontal generation mode.")
            .translation(TRANSLATION_PREFIX + "horizontal")
            .push("horizontal");
        horizontalDensityMode = builder.comment("Choose one spatial frequency for all horizontal biomes or configure each biome frequency individually.")
                                       .translation(TRANSLATION_PREFIX + "horizontal_density_mode")
                                       .worldRestart()
                                       .defineEnum("horizontal_density_mode", HorizontalDensityMode.UNIFORM);
        horizontalBiomeSampleMultiplier = builder.comment("Biome sampling-coordinate multiplier for all horizontal biomes. 1.0 keeps the original scale, values below 1.0 create larger regions, and values above 1.0 create smaller regions.")
                                                 .translation(TRANSLATION_PREFIX + "horizontal_biome_sample_multiplier")
                                                 .worldRestart()
                                                 .defineInRange("horizontal_biome_sample_multiplier", 1.0, 0.01, 10.0);
        builder.comment("Biome sampling-coordinate multipliers for individual Alex's Caves biomes. 1.0 keeps the original scale and zero disables that biome. If every active biome is zero, all active biomes use 1.0 at runtime.")
            .translation(TRANSLATION_PREFIX + "individual_biome_sample_multiplier")
            .push("individual_biome_sample_multiplier");
        Map<String, ModConfigSpec.DoubleValue> sampleMultipliers = new LinkedHashMap<>();
        for (String biome : SUPPORTED_BIOMES) {
            sampleMultipliers.put(
                biome,
                builder.comment("Biome sampling-coordinate multiplier for alexscaves:" + biome + ". 1.0 keeps the original scale, values below 1.0 create larger regions, values above 1.0 create smaller regions, and zero disables this biome.")
                    .translation(biomeTranslationKey(biome))
                    .worldRestart()
                    .defineInRange(biome, 1.0, 0.0, 10.0)
            );
        }
        individualBiomeSampleMultipliers = Map.copyOf(sampleMultipliers);
        builder.pop();
        multiNoiseCenters = builder.comment("Temperature and humidity centers for active biomes, paired in vertical_layer_order order.")
                                   .translation(TRANSLATION_PREFIX + "multi_noise_centers")
                                   .worldRestart()
                                   .defineList(
                                       List.of("multi_noise_centers"),
                                       () -> DEFAULT_MULTI_NOISE_CENTERS,
                                       () -> 0,
                                       value -> value instanceof Integer center && center >= -10000 && center <= 10000,
                                       ModConfigSpec.Range.of(4, DEFAULT_MULTI_NOISE_CENTERS.size())
                                   );
        builder.pop();
        builder.comment("Settings used only by vertical generation mode.")
            .translation(TRANSLATION_PREFIX + "vertical")
            .push("vertical");
        verticalLayerOrder = builder.comment("Unique Alex's Caves biome paths from bottom to top. Only the first mixed_dimension_count entries are used.")
                                     .translation(TRANSLATION_PREFIX + "vertical_layer_order")
                                     .worldRestart()
                                     .defineList(
                                        List.of("vertical_layer_order"),
                                        () -> DEFAULT_VERTICAL_ORDER,
                                        () -> "",
                                        value -> value instanceof String biome && (biome.isEmpty() || SUPPORTED_BIOMES.contains(biome)),
                                        ModConfigSpec.Range.of(2, 6)
                                    );
        verticalLayerBoundaries = builder.comment("Absolute block Y boundaries for the vertical layers. Use mixed_dimension_count + 1 ascending values from -64 to 320.")
                                          .translation(TRANSLATION_PREFIX + "vertical_layer_boundaries")
                                          .worldRestart()
                                          .defineList(
                                              "vertical_layer_boundaries",
                                              () -> DEFAULT_VERTICAL_BOUNDARIES,
                                              () -> 0,
                                              value -> value instanceof Integer boundary && boundary >= -64 && boundary <= 320
                                          );
        verticalSpawnBiome = builder.comment("Vertical-mode entry biome path. Leave empty to use the normal top-down search.")
                                    .translation(TRANSLATION_PREFIX + "vertical_spawn_biome")
                                    .worldRestart()
                                    .define(
                                        "vertical_spawn_biome",
                                        "",
                                        value -> value instanceof String biome && (biome.isEmpty() || SUPPORTED_BIOMES.contains(biome))
                                    );
        builder.pop();
        builder.pop();
    }

    public static boolean usesVerticalLayers() {
        return INSTANCE.generationMode.get() == GenerationMode.VERTICAL;
    }

    public static int mixedDimensionCount() {
        return INSTANCE.mixedDimensionCount.get();
    }

    public static HorizontalDensityMode horizontalDensityMode() {
        return INSTANCE.horizontalDensityMode.get();
    }

    public static double horizontalBiomeSampleMultiplier() {
        return INSTANCE.horizontalBiomeSampleMultiplier.get();
    }

    public static double individualBiomeSampleMultiplier(String biome) {
        ModConfigSpec.DoubleValue multiplier = INSTANCE.individualBiomeSampleMultipliers.get(biome);
        return multiplier == null ? 1.0 : multiplier.get();
    }

    public static boolean isSupportedBiome(String biome) {
        return SUPPORTED_BIOMES.contains(biome);
    }

    public static List<String> verticalOrder() {
        List<String> configured = configuredVerticalOrder();
        int count = mixedDimensionCount();
        if (configured.size() >= count) {
            return List.copyOf(configured.subList(0, count));
        }
        return List.copyOf(DEFAULT_VERTICAL_ORDER.subList(0, count));
    }

    public static List<String> configuredVerticalOrder() {
        List<? extends String> configured = INSTANCE.verticalLayerOrder.get();
        if (configured.size() >= 2 && configured.size() <= 6
            && configured.stream().distinct().count() == configured.size()
            && new HashSet<>(SUPPORTED_BIOMES).containsAll(configured)) {
            return List.copyOf(configured);
        }
        return DEFAULT_VERTICAL_ORDER;
    }

    public static List<Integer> verticalBoundaries() {
        int count = mixedDimensionCount();
        return hasValidVerticalBoundaries() ? List.copyOf(INSTANCE.verticalLayerBoundaries.get()) : defaultVerticalBoundaries(count);
    }

    public static boolean hasValidVerticalBoundaries() {
        List<? extends Integer> configured = INSTANCE.verticalLayerBoundaries.get();
        int count = mixedDimensionCount();
        if (configured.size() != count + 1 || configured.getFirst() != -64 || configured.getLast() != 320) {
            return false;
        }
        for (int index = 1; index < configured.size(); index++) {
            if (configured.get(index - 1) >= configured.get(index)) {
                return false;
            }
        }
        return true;
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
        if (configured.size() == required) {
            if (configured.equals(LEGACY_MULTI_NOISE_CENTERS.subList(0, required))) {
                return List.copyOf(DEFAULT_MULTI_NOISE_CENTERS.subList(0, required));
            }
            return List.copyOf(configured);
        }
        return List.copyOf(DEFAULT_MULTI_NOISE_CENTERS.subList(0, required));
    }

    public static String biomeTranslationKey(String biome) {
        return ResourceLocation.fromNamespaceAndPath("alexscaves", biome).toLanguageKey("biome");
    }

    public enum GenerationMode implements TranslatableEnum {
        MULTI_NOISE,
        VERTICAL;

        @Override
        public @NotNull Component getTranslatedName() {
            return Component.translatable(TRANSLATION_PREFIX + "generation_mode." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public enum HorizontalDensityMode implements TranslatableEnum {
        UNIFORM,
        INDIVIDUAL;

        @Override
        public @NotNull Component getTranslatedName() {
            return Component.translatable(TRANSLATION_PREFIX + "horizontal_density_mode." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }
}
