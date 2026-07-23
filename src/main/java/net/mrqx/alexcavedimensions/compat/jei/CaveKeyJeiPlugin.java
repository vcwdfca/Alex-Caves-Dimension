package net.mrqx.alexcavedimensions.compat.jei;

import com.github.alexmodguy.alexscaves.server.item.ACItemRegistry;
import com.github.alexmodguy.alexscaves.server.item.CaveInfoItem;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.mrqx.alexcavedimensions.AlexCavesDimensions;
import net.mrqx.alexcavedimensions.compat.CaveKeyRecipeDisplays;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@JeiPlugin
public class CaveKeyJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_ID = AlexCavesDimensions.id("jei");
    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ACItemRegistry.CAVE_CODEX.get(), CaveCodexSubtypeInterpreter.INSTANCE);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(RecipeTypes.CRAFTING, CaveKeyRecipeDisplays.syntheticRecipes());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        List<RecipeHolder<CraftingRecipe>> realRecipes = jeiRuntime.getRecipeManager()
                .createRecipeLookup(RecipeTypes.CRAFTING)
                .includeHidden()
                .get()
                .filter(recipeHolder -> CaveKeyRecipeDisplays.hiddenRealRecipeIds().contains(recipeHolder.id()))
                .toList();

        if (realRecipes.isEmpty()) {
            AlexCavesDimensions.LOGGER.warn("Cannot hide JEI cave key recipes: none of {} were found", CaveKeyRecipeDisplays.hiddenRealRecipeIds());
            return;
        }

        jeiRuntime.getRecipeManager().hideRecipes(RecipeTypes.CRAFTING, realRecipes);
    }

    private enum CaveCodexSubtypeInterpreter implements ISubtypeInterpreter<ItemStack> {
        INSTANCE;

        @Override
        public @Nullable Object getSubtypeData(@NotNull ItemStack ingredient, @NotNull UidContext context) {
            ResourceKey<Biome> caveBiome = CaveInfoItem.getCaveBiome(ingredient);
            return caveBiome == null ? null : caveBiome.location();
        }

        @Override
        public @NotNull String getLegacyStringSubtypeInfo(@NotNull ItemStack ingredient, @NotNull UidContext context) {
            ResourceKey<Biome> caveBiome = CaveInfoItem.getCaveBiome(ingredient);
            return caveBiome == null ? "" : caveBiome.location().toString();
        }
    }
}
