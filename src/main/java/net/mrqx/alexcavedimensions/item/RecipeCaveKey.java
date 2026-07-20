package net.mrqx.alexcavedimensions.item;

import com.github.alexmodguy.alexscaves.server.item.ACItemRegistry;
import com.github.alexmodguy.alexscaves.server.item.CaveInfoItem;
import com.github.alexthe666.citadel.recipe.SpecialRecipeInGuideBook;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.biome.Biome;
import net.mrqx.alexcavedimensions.AlexCavesDimensions;
import net.mrqx.alexcavedimensions.compat.CaveKeyRecipeDisplays;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class RecipeCaveKey extends ShapedRecipe implements SpecialRecipeInGuideBook {

    public RecipeCaveKey(CraftingBookCategory category) {
        super("", category, new ShapedRecipePattern(3, 3, CaveKeyRecipeDisplays.realIngredients(), Optional.empty()),
            AlexCavesDimensions.ABYSSAL_CHASM_KEY.get().getDefaultInstance());
    }

    @Override
    public @NotNull ItemStack assemble(CraftingInput input, @NotNull HolderLookup.Provider registries) {
        return createKeyForCodex(input.getItem(4));
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return AlexCavesDimensions.CAVE_KEY.get();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public @NotNull ItemStack getResultItem(@NotNull HolderLookup.Provider registries) {
        return AlexCavesDimensions.ABYSSAL_CHASM_KEY.get().getDefaultInstance();
    }

    @Override
    public NonNullList<Ingredient> getDisplayIngredients() {
        return CaveKeyRecipeDisplays.guidebookIngredients();
    }

    @Override
    public ItemStack getDisplayResultFor(NonNullList<ItemStack> nonNullList) {
        return createKeyForCodex(findCaveCodex(nonNullList));
    }

    private static ItemStack findCaveCodex(NonNullList<ItemStack> items) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && stack.is(ACItemRegistry.CAVE_CODEX.get())) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack createKeyForCodex(ItemStack codex) {
        ResourceKey<Biome> key = CaveInfoItem.getCaveBiome(codex);
        return key == null ? ItemStack.EMPTY : CaveKeyRecipeDisplays.keyForBiome(key);
    }
}
