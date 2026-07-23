package net.mrqx.alexcavedimensions.compat;

import com.github.alexmodguy.alexscaves.server.item.ACItemRegistry;
import com.github.alexmodguy.alexscaves.server.item.CaveInfoItem;
import com.github.alexmodguy.alexscaves.server.level.biome.ACBiomeRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.biome.Biome;
import net.mrqx.alexcavedimensions.AlexCavesDimensions;
import net.mrqx.alexcavedimensions.item.ItemCaveKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Shared cave key recipe display definitions for guidebook and optional recipe viewers.
 */
public final class CaveKeyRecipeDisplays {

    private static final Map<ResourceLocation, Variant> VARIANTS_BY_RECIPE_ID = Map.ofEntries(
        variant("abyssal_chasm", ACBiomeRegistry.ABYSSAL_CHASM, AlexCavesDimensions.ABYSSAL_CHASM_KEY),
        variant("candy_cavity", ACBiomeRegistry.CANDY_CAVITY, AlexCavesDimensions.CANDY_CAVITY_KEY),
        variant("forlorn_hollows", ACBiomeRegistry.FORLORN_HOLLOWS, AlexCavesDimensions.FORLORN_HOLLOWS_KEY),
        variant("magnetic_caves", ACBiomeRegistry.MAGNETIC_CAVES, AlexCavesDimensions.MAGNETIC_CAVES_KEY),
        variant("primordial_caves", ACBiomeRegistry.PRIMORDIAL_CAVES, AlexCavesDimensions.PRIMORDIAL_CAVES_KEY),
        variant("toxic_caves", ACBiomeRegistry.TOXIC_CAVES, AlexCavesDimensions.TOXIC_CAVES_KEY)
    );
    private static final ResourceLocation REAL_CAVE_KEY_RECIPE_ID = AlexCavesDimensions.id("cave_keys");

    private CaveKeyRecipeDisplays() {
    }

    public static @NotNull NonNullList<Ingredient> realIngredients() {
        return ingredients(Ingredient.of(ACItemRegistry.CAVE_CODEX.get()));
    }

    public static @NotNull NonNullList<Ingredient> guidebookIngredients() {
        return ingredients(Ingredient.of(variants().stream().map(Variant::codexStack)));
    }

    public static @NotNull Collection<Variant> variants() {
        return VARIANTS_BY_RECIPE_ID.values();
    }

    public static @NotNull ItemStack keyForBiome(@NotNull ResourceKey<Biome> biome) {
        return variantByBiome(biome).map(Variant::keyStack).orElse(ItemStack.EMPTY);
    }

    public static @NotNull List<RecipeHolder<CraftingRecipe>> syntheticRecipes() {
        return VARIANTS_BY_RECIPE_ID.entrySet().stream()
            .map(entry -> entry.getValue().syntheticRecipe(entry.getKey()))
            .toList();
    }

    public static @NotNull Set<ResourceLocation> hiddenRealRecipeIds() {
        return Set.of(REAL_CAVE_KEY_RECIPE_ID);
    }

    public static @NotNull Optional<Variant> variantByRecipeId(@NotNull ResourceLocation id) {
        return Optional.ofNullable(VARIANTS_BY_RECIPE_ID.get(id));
    }

    public static @NotNull Optional<Variant> variantByBiome(@NotNull ResourceKey<Biome> biome) {
        return variants().stream().filter(variant -> variant.biome().equals(biome)).findFirst();
    }

    private static @NotNull NonNullList<Ingredient> ingredients(@NotNull Ingredient center) {
        return NonNullList.of(Ingredient.EMPTY,
            Ingredient.of(Items.BLAZE_POWDER), Ingredient.of(Items.ENDER_PEARL), Ingredient.of(Items.BLAZE_POWDER),
            Ingredient.of(Items.ENDER_PEARL), center, Ingredient.of(Items.ENDER_PEARL),
            Ingredient.of(Items.BLAZE_POWDER), Ingredient.of(Items.ENDER_PEARL), Ingredient.of(Items.BLAZE_POWDER));
    }

    private static @NotNull Map.Entry<ResourceLocation, Variant> variant(
        @NotNull String path,
        @NotNull ResourceKey<Biome> biome,
        @NotNull Supplier<? extends ItemCaveKey> key
    ) {
        Variant variant = new Variant(path, biome, key);
        return Map.entry(variant.recipeId(), variant);
    }

    public record Variant(
        @NotNull String path,
        @NotNull ResourceKey<Biome> biome,
        @NotNull Supplier<? extends ItemCaveKey> key
    ) {
        public @NotNull ResourceLocation recipeId() {
            return AlexCavesDimensions.id("cave_key/" + path);
        }

        public @NotNull ResourceLocation emiRecipeId() {
            return AlexCavesDimensions.id("/cave_key/" + path);
        }

        public @NotNull ItemStack codexStack() {
            return CaveInfoItem.create(ACItemRegistry.CAVE_CODEX.get(), biome);
        }

        public @NotNull ItemStack keyStack() {
            return key.get().getDefaultInstance();
        }

        public @NotNull NonNullList<Ingredient> ingredients() {
            return CaveKeyRecipeDisplays.ingredients(Ingredient.of(codexStack()));
        }

        private @NotNull RecipeHolder<CraftingRecipe> syntheticRecipe(@NotNull ResourceLocation recipeId) {
            ShapedRecipe recipe = new ShapedRecipe(
                "",
                CraftingBookCategory.MISC,
                new ShapedRecipePattern(3, 3, ingredients(), Optional.empty()),
                keyStack()
            );
            return new RecipeHolder<>(recipeId, recipe);
        }
    }
}
