package net.mrqx.alexcavedimensions.compat.rei;

import com.github.alexmodguy.alexscaves.server.item.ACItemRegistry;
import com.github.alexmodguy.alexscaves.server.item.CaveInfoItem;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.mrqx.alexcavedimensions.compat.CaveKeyRecipeDisplays;
import org.jetbrains.annotations.NotNull;

/**
 * REI transfer handler for cave key displays that require a biome-tagged Cave Codex.
 */
final class CaveKeyReiTransferHandler implements TransferHandler {

    static final CaveKeyReiTransferHandler INSTANCE = new CaveKeyReiTransferHandler();

    private static final Component TOO_SMALL = Component.translatable("error.rei.transfer.too_small", 3, 3);
    private static final Component NOT_ENOUGH_MATERIALS = Component.translatable("error.rei.not.enough.materials");
    private static final int FIRST_INPUT_SLOT = 1;
    private static final int SLOT_END_EXCLUSIVE = 46;

    private CaveKeyReiTransferHandler() {
    }

    @Override
    public double getPriority() {
        return 10_000.0D;
    }

    @Override
    public @NotNull ApplicabilityResult checkApplicable(@NotNull Context context) {
        if (variant(context) == null) {
            return ApplicabilityResult.createNotApplicable();
        }
        if (!(context.getMenu() instanceof CraftingMenu)) {
            return ApplicabilityResult.createApplicableWithError(
                TransferHandler.Result.createFailed(TOO_SMALL).blocksFurtherHandling()
            );
        }
        return ApplicabilityResult.createApplicable();
    }

    @Override
    public @NotNull Result handle(@NotNull Context context) {
        CaveKeyRecipeDisplays.Variant variant = variant(context);
        if (variant == null) {
            return Result.createNotApplicable();
        }
        if (!(context.getMenu() instanceof CraftingMenu)) {
            return Result.createFailed(TOO_SMALL).blocksFurtherHandling();
        }
        if (!hasMaterials(context, variant)) {
            return Result.createFailed(NOT_ENOUGH_MATERIALS).blocksFurtherHandling();
        }
        if (!context.isActuallyCrafting()) {
            return Result.createSuccessful().blocksFurtherHandling();
        }

        PacketDistributor.sendToServer(new CaveKeyRecipeTransferPayload(variant.recipeId(), context.isStackedCrafting()));
        return Result.createSuccessful().blocksFurtherHandling();
    }

    private static CaveKeyRecipeDisplays.Variant variant(@NotNull Context context) {
        return context.getDisplay().getDisplayLocation()
            .flatMap(CaveKeyRecipeDisplays::variantByRecipeId)
            .orElse(null);
    }

    private static boolean hasMaterials(@NotNull Context context, @NotNull CaveKeyRecipeDisplays.Variant variant) {
        AbstractContainerMenu menu = context.getMenu();
        if (!(menu instanceof CraftingMenu)) {
            return false;
        }

        int blazePowder = 0;
        int enderPearls = 0;
        boolean matchingCodex = false;

        for (int slotIndex = FIRST_INPUT_SLOT; slotIndex < SLOT_END_EXCLUSIVE; slotIndex++) {
            ItemStack stack = menu.slots.get(slotIndex).getItem();
            if (stack.is(Items.BLAZE_POWDER)) {
                blazePowder += stack.getCount();
            } else if (stack.is(Items.ENDER_PEARL)) {
                enderPearls += stack.getCount();
            } else if (stack.is(ACItemRegistry.CAVE_CODEX.get()) && variant.biome().equals(CaveInfoItem.getCaveBiome(stack))) {
                matchingCodex = true;
            }
        }

        return blazePowder >= 4 && enderPearls >= 4 && matchingCodex;
    }
}
