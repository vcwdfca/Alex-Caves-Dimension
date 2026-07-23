package net.mrqx.alexcavedimensions;

import com.github.alexmodguy.alexscaves.server.misc.ACCreativeTabRegistry;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.mrqx.alexcavedimensions.compat.rei.CaveKeyRecipeTransferPayload;
import net.mrqx.alexcavedimensions.config.PrismaticDepthsConfig;
import net.mrqx.alexcavedimensions.dimension.PrismaticDepthsBiomeSource;
import net.mrqx.alexcavedimensions.dimension.PrismaticDepthsChunkGenerator;
import net.mrqx.alexcavedimensions.item.ItemCaveKey;
import net.mrqx.alexcavedimensions.item.RecipeCaveKey;
import net.mrqx.alexcavedimensions.network.CaveKeyLoadingPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(AlexCavesDimensions.MODID)
@EventBusSubscriber
@SuppressWarnings("unused")
public class AlexCavesDimensions {

    public static final String MODID = "alex_caves_dimensions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES = DeferredRegister.create(Registries.BIOME_SOURCE, MODID);
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

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
    public static final ResourceKey<Level> PRISMATIC_DEPTHS_RESOURCE_KEY = ResourceKey.create(Registries.DIMENSION,
        id("prismatic_depths"));

    public static final DeferredItem<ItemCaveKey> ABYSSAL_CHASM_KEY = ITEMS.register("abyssal_chasm_key",
        () -> new ItemCaveKey(new Item.Properties(), ABYSSAL_CHASM_RESOURCE_KEY));
    public static final DeferredItem<ItemCaveKey> CANDY_CAVITY_KEY = ITEMS.register("candy_cavity_key",
        () -> new ItemCaveKey(new Item.Properties(), CANDY_CAVITY_RESOURCE_KEY));
    public static final DeferredItem<ItemCaveKey> FORLORN_HOLLOWS_KEY = ITEMS.register("forlorn_hollows_key",
        () -> new ItemCaveKey(new Item.Properties(), FORLORN_HOLLOWS_RESOURCE_KEY));
    public static final DeferredItem<ItemCaveKey> TOXIC_CAVES_KEY = ITEMS.register("toxic_caves_key",
        () -> new ItemCaveKey(new Item.Properties(), TOXIC_CAVES_RESOURCE_KEY));
    public static final DeferredItem<ItemCaveKey> PRIMORDIAL_CAVES_KEY = ITEMS.register("primordial_caves_key",
        () -> new ItemCaveKey(new Item.Properties(), PRIMORDIAL_CAVES_RESOURCE_KEY));
    public static final DeferredItem<ItemCaveKey> MAGNETIC_CAVES_KEY = ITEMS.register("magnetic_caves_key",
        () -> new ItemCaveKey(new Item.Properties(), MAGNETIC_CAVES_RESOURCE_KEY));
    public static final DeferredItem<ItemCaveKey> PRISMATIC_DEPTHS_KEY = ITEMS.register("prismatic_depths_key",
        () -> new ItemCaveKey(new Item.Properties(), PRISMATIC_DEPTHS_RESOURCE_KEY));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PRISMATIC_DEPTHS_TAB = CREATIVE_MODE_TABS.register(
        "prismatic_depths", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.alex_caves_dimensions.prismatic_depths"))
            .icon(() -> PRISMATIC_DEPTHS_KEY.get().getDefaultInstance())
            .displayItems((parameters, output) -> output.accept(PRISMATIC_DEPTHS_KEY.get()))
            .build()
    );

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<RecipeCaveKey>> CAVE_KEY = RECIPE_SERIALIZERS.register("cave_key", () -> new SimpleCraftingRecipeSerializer<>(RecipeCaveKey::new));
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<PrismaticDepthsBiomeSource>> PRISMATIC_DEPTHS_BIOME_SOURCE = BIOME_SOURCES.register(
        "prismatic_depths", () -> PrismaticDepthsBiomeSource.CODEC
    );
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<PrismaticDepthsChunkGenerator>> PRISMATIC_DEPTHS_CHUNK_GENERATOR = CHUNK_GENERATORS.register(
        "prismatic_depths", () -> PrismaticDepthsChunkGenerator.CODEC
    );

    public AlexCavesDimensions(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);
        CHUNK_GENERATORS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, PrismaticDepthsConfig.SPEC);
        modEventBus.addListener(this::registerPayloadHandlers);
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
            .playToClient(
                CaveKeyLoadingPayload.TYPE,
                CaveKeyLoadingPayload.STREAM_CODEC,
                CaveKeyLoadingPayload::handle
            )
            .playToServer(
                CaveKeyRecipeTransferPayload.TYPE,
                CaveKeyRecipeTransferPayload.STREAM_CODEC,
                CaveKeyRecipeTransferPayload::handle
            );
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
            && (dimension.equals(CANDY_CAVITY_RESOURCE_KEY) || dimension.equals(PRIMORDIAL_CAVES_RESOURCE_KEY) || dimension.equals(PRISMATIC_DEPTHS_RESOURCE_KEY));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
