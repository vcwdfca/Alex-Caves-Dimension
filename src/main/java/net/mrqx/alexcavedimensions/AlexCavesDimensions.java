package net.mrqx.alexcavedimensions;

import com.github.alexmodguy.alexscaves.server.misc.ACCreativeTabRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(AlexCavesDimensions.MODID)
@EventBusSubscriber
public class AlexCavesDimensions {

    public static final String MODID = "alex_caves_dimensions";
    @SuppressWarnings("unused")
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);

    public static final DeferredHolder<Item, ItemCaveKey> ABYSSAL_CHASM_KEY = ITEMS.register("abyssal_chasm_key", () -> new ItemCaveKey(new Item.Properties()));
    public static final DeferredHolder<Item, ItemCaveKey> CANDY_CAVITY_KEY = ITEMS.register("candy_cavity_key", () -> new ItemCaveKey(new Item.Properties()));
    public static final DeferredHolder<Item, ItemCaveKey> FORLORN_HOLLOWS_KEY = ITEMS.register("forlorn_hollows_key", () -> new ItemCaveKey(new Item.Properties()));
    public static final DeferredHolder<Item, ItemCaveKey> TOXIC_CAVES_KEY = ITEMS.register("toxic_caves_key", () -> new ItemCaveKey(new Item.Properties()));
    public static final DeferredHolder<Item, ItemCaveKey> PRIMORDIAL_CAVES_KEY = ITEMS.register("primordial_caves_key", () -> new ItemCaveKey(new Item.Properties()));
    public static final DeferredHolder<Item, ItemCaveKey> MAGNETIC_CAVES_KEY = ITEMS.register("magnetic_caves_key", () -> new ItemCaveKey(new Item.Properties()));

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<RecipeCaveKey>> CAVE_KEY = RECIPE_SERIALIZERS.register("cave_key", () -> new SimpleCraftingRecipeSerializer<>(RecipeCaveKey::new));
    
    public static final ResourceKey<Level> TOXIC_CAVES_RESOURCE_KEY = ResourceKey.create(Registries.DIMENSION,
        id("toxic_caves"));
    public static final ResourceKey<Level> PRIMORDIAL_CAVES_RESOURCE_KEY = ResourceKey.create(Registries.DIMENSION,
        id("primordial_caves"));
    public static final ResourceKey<Level> MAGNETIC_CAVES_RESOURCE_KEY = ResourceKey.create(Registries.DIMENSION,
        id("magnetic_caves"));
    public static final ResourceKey<Level> FORLORN_HOLLOWS_RESOURCE_KEY = ResourceKey.create(Registries.DIMENSION,
        id("forlorn_hollows"));
    public static final ResourceKey<Level> CANDY_CAVITY_RESOURCE_KEY = ResourceKey.create(Registries.DIMENSION,
        id("candy_cavity"));
    public static final ResourceKey<Level> ABYSSAL_CHASM_RESOURCE_KEY = ResourceKey.create(Registries.DIMENSION,
        id("abyssal_chasm"));
    
    public AlexCavesDimensions(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
    
    @SubscribeEvent
    public static void onBuildCreativeModeTabContentsEvent(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ACCreativeTabRegistry.ABYSSAL_CHASM.getKey())) {
            event.accept(ABYSSAL_CHASM_KEY.get());
        }
        if (event.getTabKey().equals(ACCreativeTabRegistry.CANDY_CAVITY.getKey())) {
            event.accept(CANDY_CAVITY_KEY.get());
        }
        if (event.getTabKey().equals(ACCreativeTabRegistry.FORLORN_HOLLOWS.getKey())) {
            event.accept(FORLORN_HOLLOWS_KEY.get());
        }
        if (event.getTabKey().equals(ACCreativeTabRegistry.TOXIC_CAVES.getKey())) {
            event.accept(TOXIC_CAVES_KEY.get());
        }
        if (event.getTabKey().equals(ACCreativeTabRegistry.PRIMORDIAL_CAVES.getKey())) {
            event.accept(PRIMORDIAL_CAVES_KEY.get());
        }
        if (event.getTabKey().equals(ACCreativeTabRegistry.MAGNETIC_CAVES.getKey())) {
            event.accept(MAGNETIC_CAVES_KEY.get());
        }
    }
    
    public static boolean shouldDimensionHasNightVision(ResourceKey<Level> dimension) {
        return dimension.location().getNamespace().equals(AlexCavesDimensions.MODID)
            && (dimension.equals(CANDY_CAVITY_RESOURCE_KEY) || dimension.equals(PRIMORDIAL_CAVES_RESOURCE_KEY));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
