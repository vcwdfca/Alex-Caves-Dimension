package net.mrqx.alexcavedimensions.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ListValueSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Shows the basic Prismatic Depths settings and horizontal biome densities; advanced lists remain TOML-only. */
public final class PrismaticDepthsConfigurationSectionScreen extends ConfigurationScreen.ConfigurationSectionScreen {

    public PrismaticDepthsConfigurationSectionScreen(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
        super(parent, type, modConfig, title);
    }

    private PrismaticDepthsConfigurationSectionScreen(
        Context parentContext,
        Screen parent,
        java.util.Map<String, Object> valueSpecs,
        String key,
        java.util.Set<? extends UnmodifiableConfig.Entry> entrySet,
        Component title
    ) {
        super(parentContext, parent, valueSpecs, key, entrySet, title);
    }

    @Override
    @Nullable
    protected Element createSection(@NotNull String key, @NotNull UnmodifiableConfig subconfig, @NotNull UnmodifiableConfig subsection) {
        if (!"prismatic_depths".equals(key)
            && !"horizontal".equals(key)
            && !"vertical".equals(key)
            && !"individual_biome_sample_multiplier".equals(key)) {
            return super.createSection(key, subconfig, subsection);
        }
        return new Element(
            Component.translatable("neoforge.configuration.uitext.section", getTranslationComponent(key)),
            getTooltipComponent(key, null),
            Button.builder(
                    Component.translatable(
                        "neoforge.configuration.uitext.section",
                        Component.translatable(getTranslationKey(key) + ".button", "Edit")
                    ),
                    button -> java.util.Objects.requireNonNull(minecraft).setScreen(sectionCache.computeIfAbsent(
                        key,
                        ignored -> new PrismaticDepthsConfigurationSectionScreen(
                            context,
                            this,
                            valueMap(subconfig),
                            key,
                            subsection.entrySet(),
                            Component.translatable(getTranslationKey(key))
                        ).rebuild()
                    ))
                )
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(getTooltipComponent(key, null)))
                .width(Button.DEFAULT_WIDTH)
                .build(),
            false
        );
    }

    @Override
    protected @NotNull Component getTooltipComponent(@NotNull String key, @Nullable ModConfigSpec.Range<?> range) {
        if (PrismaticDepthsConfig.isSupportedBiome(key)) {
            return Component.translatable("alex_caves_dimensions.configuration.prismatic_depths.individual_biome_sample_multiplier.biome.tooltip");
        }
        Component tooltip = super.getTooltipComponent(key, range);
        if (!PrismaticDepthsConfig.hasValidVerticalBoundaries()
            && (key.equals("prismatic_depths") || key.equals("generation_mode") || key.equals("vertical"))) {
            return tooltip.copy()
                .append(Component.literal("\n\n"))
                .append(Component.translatable(
                        "alex_caves_dimensions.configuration.prismatic_depths.vertical_layer_boundaries.fallback_warning",
                        PrismaticDepthsConfig.verticalBoundaries().toString()
                    )
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        return tooltip;
    }

    @Override
    @Nullable
    protected <T> Element createList(@NotNull String key, @NotNull ListValueSpec spec, @NotNull ModConfigSpec.ConfigValue<List<T>> list) {
        if (key.equals("vertical_layer_order") || key.equals("vertical_layer_boundaries") || key.equals("multi_noise_centers")) {
            return null;
        }
        return super.createList(key, spec, list);
    }

    @Override
    @Nullable
    protected Element createStringValue(
        @NotNull String key,
        @NotNull java.util.function.Predicate<String> tester,
        @NotNull java.util.function.Supplier<String> source,
        @NotNull java.util.function.Consumer<String> target
    ) {
        if (!key.equals("vertical_spawn_biome")) {
            return super.createStringValue(key, tester, source, target);
        }

        List<String> choices = new java.util.ArrayList<>();
        choices.add("");
        choices.addAll(PrismaticDepthsConfig.verticalOrder());
        String selected = PrismaticDepthsConfig.verticalSpawnBiome();
        return new Element(
            getTranslationComponent(key),
            getTooltipComponent(key, null),
            new OptionInstance<>(
                getTranslationKey(key),
                getTooltip(key, null),
                (caption, value) -> value.isEmpty()
                    ? Component.translatable(getTranslationKey(key) + ".auto")
                    : Component.translatable(PrismaticDepthsConfig.biomeTranslationKey(value)),
                new ConfigurationScreen.ConfigurationSectionScreen.Custom<>(choices),
                selected,
                value -> {
                    if (!value.equals(source.get())) {
                        undoManager.add(
                            previous -> {
                                target.accept(previous);
                                onChanged(key);
                            },
                            value,
                            previous -> {
                                target.accept(previous);
                                onChanged(key);
                            },
                            source.get()
                        );
                    }
                }
            )
        );
    }

    private static Map<String, Object> valueMap(UnmodifiableConfig config) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            values.put(entry.getKey(), entry.getValue());
        }
        return values;
    }

}
