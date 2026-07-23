package net.mrqx.alexcavedimensions.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CaveKeyLoadingClientHandler {
    private static ReceivingLevelScreen caveKeyLoadingScreen;

    private CaveKeyLoadingClientHandler() {}

    public static void handle(boolean loading) {
        Minecraft minecraft = Minecraft.getInstance();
        if (loading) {
            caveKeyLoadingScreen = new ReceivingLevelScreen(() -> false, ReceivingLevelScreen.Reason.OTHER);
            minecraft.setScreen(caveKeyLoadingScreen);
        } else if (minecraft.screen == caveKeyLoadingScreen) {
            minecraft.setScreen(null);
            caveKeyLoadingScreen = null;
        }
    }
}
