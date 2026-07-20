package net.mrqx.alexcavedimensions.compat.emi;

import com.github.alexmodguy.alexscaves.server.item.ACItemRegistry;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.mrqx.alexcavedimensions.AlexCavesDimensions;
import net.mrqx.alexcavedimensions.compat.CaveKeyRecipeDisplays;

import java.util.List;

@EmiEntrypoint
public class CaveKeyEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.setDefaultComparison(EmiStack.of(ACItemRegistry.CAVE_CODEX.get()), Comparison.compareComponents());
        CaveKeyRecipeDisplays.hiddenRealRecipeIds().forEach(registry::removeRecipes);
        CaveKeyRecipeDisplays.variants().stream()
            .map(CaveKeyEmiPlugin::createRecipe)
            .forEach(registry::addRecipe);
        CaveKeyRecipeDisplays.prismaticDepthsRecipes().stream()
            .map(CaveKeyEmiPlugin::createPrismaticRecipe)
            .forEach(registry::addRecipe);
    }

    private static EmiCraftingRecipe createRecipe(CaveKeyRecipeDisplays.Variant variant) {
        List<EmiIngredient> inputs = variant.ingredients().stream()
            .map(ingredient -> {
                ItemStack[] stacks = ingredient.getItems();
                if (stacks.length == 1 && stacks[0].is(ACItemRegistry.CAVE_CODEX.get())) {
                    return EmiStack.of(stacks[0]).comparison(Comparison.compareComponents());
                }
                return EmiIngredient.of(ingredient);
            })
            .toList();
        return new EmiCraftingRecipe(inputs, EmiStack.of(variant.keyStack()), variant.emiRecipeId(), false);
    }

    private static EmiCraftingRecipe createPrismaticRecipe(RecipeHolder<CraftingRecipe> recipe) {
        List<EmiIngredient> inputs = recipe.value().getIngredients().stream().map(EmiIngredient::of).toList();
        return new EmiCraftingRecipe(inputs, EmiStack.of(AlexCavesDimensions.PRISMATIC_DEPTHS_KEY.get()), recipe.id(), false);
    }
}
