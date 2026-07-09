package net.mrqx.alexcavedimensions.compat.rei;

import com.github.alexmodguy.alexscaves.server.item.ACItemRegistry;
import com.github.alexmodguy.alexscaves.server.item.CaveInfoItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.mrqx.alexcavedimensions.AlexCavesDimensions;
import net.mrqx.alexcavedimensions.compat.CaveKeyRecipeDisplays;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * Server-bound request to transfer a synthetic cave key recipe from REI into a crafting table.
 */
public record CaveKeyRecipeTransferPayload(@NotNull ResourceLocation recipeId,
                                           boolean stackedCrafting) implements CustomPacketPayload {

    public static final Type<CaveKeyRecipeTransferPayload> TYPE = new Type<>(AlexCavesDimensions.id("cave_key_recipe_transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CaveKeyRecipeTransferPayload> STREAM_CODEC =
        StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            CaveKeyRecipeTransferPayload::recipeId,
            ByteBufCodecs.BOOL,
            CaveKeyRecipeTransferPayload::stackedCrafting,
            CaveKeyRecipeTransferPayload::new
        );

    private static final int FIRST_INPUT_SLOT = 1;
    private static final int INPUT_SLOT_COUNT = 9;
    private static final int FIRST_PLAYER_SLOT = FIRST_INPUT_SLOT + INPUT_SLOT_COUNT;
    private static final int PLAYER_SLOT_END_EXCLUSIVE = 46;

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(@NotNull CaveKeyRecipeTransferPayload payload, @NotNull IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            AlexCavesDimensions.LOGGER.warn("Ignoring cave key recipe transfer from non-server player context");
            return;
        }

        CaveKeyRecipeDisplays.variantByRecipeId(payload.recipeId()).ifPresentOrElse(
            variant -> transfer(player, variant, payload.stackedCrafting()),
            () -> AlexCavesDimensions.LOGGER.warn("Ignoring cave key recipe transfer with unknown recipe id {}", payload.recipeId())
        );
    }

    private static void transfer(@NotNull ServerPlayer player, @NotNull CaveKeyRecipeDisplays.Variant variant, boolean stackedCrafting) {
        if (!(player.containerMenu instanceof CraftingMenu menu)) {
            AlexCavesDimensions.LOGGER.warn("Ignoring cave key recipe transfer for {}: current menu is not a crafting table", player.getGameProfile().getName());
            return;
        }
        MaterialCounts materialCounts = countMaterials(menu, variant, FIRST_INPUT_SLOT);
        if (!hasMaterials(materialCounts)) {
            AlexCavesDimensions.LOGGER.warn("Ignoring cave key recipe transfer for {}: missing materials for {}", player.getGameProfile().getName(), variant.recipeId());
            return;
        }
        if (!canReturnCraftingGridToInventory(player, menu)) {
            AlexCavesDimensions.LOGGER.warn("Ignoring cave key recipe transfer for {}: crafting grid cannot be returned to inventory", player.getGameProfile().getName());
            return;
        }

        returnCraftingGridToInventory(player, menu);
        materialCounts = countMaterials(menu, variant, FIRST_PLAYER_SLOT);
        RecipePlan recipePlan = recipePlan(menu, variant, materialCounts, stackedCrafting);
        if (recipePlan.count() < 1) {
            AlexCavesDimensions.LOGGER.warn("Ignoring cave key recipe transfer for {}: no transferable recipe count for {}", player.getGameProfile().getName(), variant.recipeId());
            return;
        }
        NonNullList<ItemStack> recipeStacks = recipeStacks(player, menu, variant, recipePlan);

        placeRecipe(menu, recipeStacks);
        menu.slotsChanged(menu.slots.get(FIRST_INPUT_SLOT).container);
        menu.broadcastChanges();
    }

    private static boolean hasMaterials(@NotNull MaterialCounts counts) {
        return counts.blazePowder() >= 4 && counts.enderPearls() >= 4 && counts.matchingCodex() >= 1;
    }

    private static @NotNull MaterialCounts countMaterials(@NotNull CraftingMenu menu, @NotNull CaveKeyRecipeDisplays.Variant variant, int firstSlot) {
        int blazePowder = 0;
        int enderPearls = 0;
        int matchingCodex = 0;

        for (int slotIndex = firstSlot; slotIndex < PLAYER_SLOT_END_EXCLUSIVE; slotIndex++) {
            ItemStack stack = menu.slots.get(slotIndex).getItem();
            if (stack.is(Items.BLAZE_POWDER)) {
                blazePowder += stack.getCount();
            } else if (stack.is(Items.ENDER_PEARL)) {
                enderPearls += stack.getCount();
            } else if (isMatchingCodex(stack, variant)) {
                matchingCodex += stack.getCount();
            }
        }

        return new MaterialCounts(blazePowder, enderPearls, matchingCodex);
    }

    private static @NotNull RecipePlan recipePlan(
        @NotNull CraftingMenu menu,
        @NotNull CaveKeyRecipeDisplays.Variant variant,
        @NotNull MaterialCounts counts,
        boolean stackedCrafting
    ) {
        ItemStack codexSample = matchingCodexSample(menu, variant);
        if (codexSample.isEmpty()) {
            return new RecipePlan(0, ItemStack.EMPTY);
        }
        int codexCount = matchingCodexCount(menu, variant, codexSample);
        int count = Math.min(maxRecipeSlotStackSize(menu, codexSample),
            Math.min(counts.blazePowder() / 4, Math.min(counts.enderPearls() / 4, codexCount)));
        if (!stackedCrafting) {
            count = Math.min(count, 1);
        }

        return new RecipePlan(count, codexSample);
    }

    private static @NotNull ItemStack matchingCodexSample(@NotNull CraftingMenu menu, @NotNull CaveKeyRecipeDisplays.Variant variant) {
        for (int slotIndex = FIRST_PLAYER_SLOT; slotIndex < PLAYER_SLOT_END_EXCLUSIVE; slotIndex++) {
            ItemStack stack = menu.slots.get(slotIndex).getItem();
            if (isMatchingCodex(stack, variant)) {
                return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    private static int matchingCodexCount(
        @NotNull CraftingMenu menu,
        @NotNull CaveKeyRecipeDisplays.Variant variant,
        @NotNull ItemStack codexSample
    ) {
        if (codexSample.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (int slotIndex = FIRST_PLAYER_SLOT; slotIndex < PLAYER_SLOT_END_EXCLUSIVE; slotIndex++) {
            ItemStack stack = menu.slots.get(slotIndex).getItem();
            if (isMatchingCodex(stack, variant) && ItemStack.isSameItemSameComponents(stack, codexSample)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int maxRecipeSlotStackSize(@NotNull CraftingMenu menu, @NotNull ItemStack codexSample) {
        return Math.min(
            recipeSlotMaxStackSize(menu, 0, Items.BLAZE_POWDER.getDefaultInstance()),
            Math.min(
                recipeSlotMaxStackSize(menu, 1, Items.ENDER_PEARL.getDefaultInstance()),
                recipeSlotMaxStackSize(menu, 4, codexSample)
            )
        );
    }

    private static int recipeSlotMaxStackSize(@NotNull CraftingMenu menu, int recipeSlot, @NotNull ItemStack stack) {
        return menu.slots.get(FIRST_INPUT_SLOT + recipeSlot).getMaxStackSize(stack);
    }

    private static boolean canReturnCraftingGridToInventory(@NotNull ServerPlayer player, @NotNull CraftingMenu menu) {
        Inventory inventory = player.getInventory();
        Inventory simulatedInventory = new Inventory(player);
        for (int slotIndex = 0; slotIndex < inventory.getContainerSize(); slotIndex++) {
            simulatedInventory.setItem(slotIndex, inventory.getItem(slotIndex).copy());
        }

        for (int slotIndex = FIRST_INPUT_SLOT; slotIndex < FIRST_PLAYER_SLOT; slotIndex++) {
            ItemStack stack = menu.slots.get(slotIndex).getItem().copy();
            if (!stack.isEmpty()) {
                simulatedInventory.add(stack);
                if (!stack.isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }

    private static void returnCraftingGridToInventory(@NotNull ServerPlayer player, @NotNull CraftingMenu menu) {
        Inventory inventory = player.getInventory();
        for (int slotIndex = FIRST_INPUT_SLOT; slotIndex < FIRST_PLAYER_SLOT; slotIndex++) {
            Slot slot = menu.slots.get(slotIndex);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
                if (!inventory.add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
    }

    private static NonNullList<ItemStack> recipeStacks(
        @NotNull ServerPlayer player,
        @NotNull CraftingMenu menu,
        @NotNull CaveKeyRecipeDisplays.Variant variant,
        @NotNull RecipePlan recipePlan
    ) {
        NonNullList<ItemStack> stacks = NonNullList.withSize(INPUT_SLOT_COUNT, ItemStack.EMPTY);
        for (int recipeSlot = 0; recipeSlot < INPUT_SLOT_COUNT; recipeSlot++) {
            ItemStack stack = take(player, menu, matcher(recipeSlot, variant, recipePlan.codexSample()), recipePlan.count());
            if (!hasCount(stack, recipePlan.count())) {
                throw new IllegalStateException("Failed to take cave key recipe slot " + recipeSlot
                    + " stack: expected " + recipePlan.count() + ", got " + stack.getCount());
            }
            stacks.set(recipeSlot, stack);
        }
        return stacks;
    }

    private static @NotNull Predicate<ItemStack> matcher(
        int recipeSlot,
        @NotNull CaveKeyRecipeDisplays.Variant variant,
        @NotNull ItemStack codexStack
    ) {
        return switch (recipeSlot) {
            case 0, 2, 6, 8 -> stack -> stack.is(Items.BLAZE_POWDER);
            case 1, 3, 5, 7 -> stack -> stack.is(Items.ENDER_PEARL);
            case 4 -> stack -> isMatchingCodex(stack, variant) && ItemStack.isSameItemSameComponents(stack, codexStack);
            default -> throw new IllegalArgumentException("Unexpected cave key recipe slot: " + recipeSlot);
        };
    }

    private static boolean hasCount(@NotNull ItemStack stack, int count) {
        return !stack.isEmpty() && stack.getCount() == count;
    }

    private static void placeRecipe(@NotNull CraftingMenu menu, @NotNull NonNullList<ItemStack> recipeStacks) {
        if (recipeStacks.size() != INPUT_SLOT_COUNT) {
            throw new IllegalArgumentException("Expected " + INPUT_SLOT_COUNT + " cave key recipe stacks, got " + recipeStacks.size());
        }
        for (int recipeSlot = 0; recipeSlot < INPUT_SLOT_COUNT; recipeSlot++) {
            setSlot(menu, recipeSlot, recipeStacks.get(recipeSlot));
        }
    }

    private static void setSlot(@NotNull CraftingMenu menu, int recipeSlot, @NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Cannot place empty stack into cave key recipe slot " + recipeSlot);
        }
        menu.slots.get(FIRST_INPUT_SLOT + recipeSlot).set(stack);
    }

    private static @NotNull ItemStack take(
        @NotNull ServerPlayer player,
        @NotNull CraftingMenu menu,
        @NotNull Predicate<ItemStack> matches,
        int count
    ) {
        ItemStack taken = ItemStack.EMPTY;
        for (int slotIndex = FIRST_PLAYER_SLOT; slotIndex < PLAYER_SLOT_END_EXCLUSIVE; slotIndex++) {
            Slot slot = menu.slots.get(slotIndex);
            ItemStack stack = slot.getItem();
            if (matches.test(stack) && canMergeTakenStack(taken, stack)) {
                int remaining = count - taken.getCount();
                ItemStack partial = slot.safeTake(remaining, remaining, player).copy();
                if (taken.isEmpty()) {
                    taken = partial;
                } else {
                    taken.grow(partial.getCount());
                }
                if (taken.getCount() == count) {
                    return taken;
                }
            }
        }
        return taken;
    }

    private static boolean canMergeTakenStack(@NotNull ItemStack taken, @NotNull ItemStack stack) {
        return taken.isEmpty() || ItemStack.isSameItemSameComponents(taken, stack);
    }

    private static boolean isMatchingCodex(@NotNull ItemStack stack, @NotNull CaveKeyRecipeDisplays.Variant variant) {
        return stack.is(ACItemRegistry.CAVE_CODEX.get()) && variant.biome().equals(CaveInfoItem.getCaveBiome(stack));
    }

    private record MaterialCounts(int blazePowder, int enderPearls, int matchingCodex) {
    }

    private record RecipePlan(int count, @NotNull ItemStack codexSample) {
    }

}
