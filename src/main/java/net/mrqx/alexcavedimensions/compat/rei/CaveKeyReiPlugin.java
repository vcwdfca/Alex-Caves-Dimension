package net.mrqx.alexcavedimensions.compat.rei;

import dev.architectury.event.EventResult;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.forge.REIPluginClient;
import me.shedaniel.rei.plugin.common.displays.crafting.DefaultCraftingDisplay;
import net.mrqx.alexcavedimensions.compat.CaveKeyRecipeDisplays;

@REIPluginClient
public class CaveKeyReiPlugin implements REIClientPlugin {

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        registry.registerVisibilityPredicate((category, display) -> display.getDisplayLocation()
            .filter(CaveKeyRecipeDisplays.hiddenRealRecipeIds()::contains)
            .isPresent() ? EventResult.interruptFalse() : EventResult.pass());

        CaveKeyRecipeDisplays.syntheticRecipes().forEach(recipe -> {
            DefaultCraftingDisplay<?> display = DefaultCraftingDisplay.of(recipe);
            if (display == null) {
                throw new IllegalStateException("Failed to create REI display for synthetic recipe " + recipe.id());
            }
            registry.add(display);
        });
        CaveKeyRecipeDisplays.prismaticDepthsRecipes().forEach(recipe -> {
            DefaultCraftingDisplay<?> display = DefaultCraftingDisplay.of(recipe);
            if (display == null) {
                throw new IllegalStateException("Failed to create REI display for Prismatic Depths recipe " + recipe.id());
            }
            registry.add(display);
        });
    }

    @Override
    public void registerTransferHandlers(TransferHandlerRegistry registry) {
        registry.register(CaveKeyReiTransferHandler.INSTANCE);
    }
}
