package net.mrqx.alexcavedimensions;

import java.util.List;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.mrqx.alexcavedimensions.config.PrismaticDepthsConfigurationSectionScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterDimensionTransitionScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = AlexCavesDimensions.MODID, dist = Dist.CLIENT)
public class AlexCavesDimensionsClient {

    public AlexCavesDimensionsClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(
            IConfigScreenFactory.class,
            (minecraft, parent) -> new ConfigurationScreen(container, parent, PrismaticDepthsConfigurationSectionScreen::new)
        );
        modEventBus.addListener(this::registerDimensionTransitionScreens);
    }

    private void registerDimensionTransitionScreens(RegisterDimensionTransitionScreenEvent event) {
        List<ResourceKey<Level>> caveDimensions = List.of(
            AlexCavesDimensions.ABYSSAL_CHASM_RESOURCE_KEY,
            AlexCavesDimensions.CANDY_CAVITY_RESOURCE_KEY,
            AlexCavesDimensions.FORLORN_HOLLOWS_RESOURCE_KEY,
            AlexCavesDimensions.MAGNETIC_CAVES_RESOURCE_KEY,
            AlexCavesDimensions.PRIMORDIAL_CAVES_RESOURCE_KEY,
            AlexCavesDimensions.TOXIC_CAVES_RESOURCE_KEY,
            AlexCavesDimensions.PRISMATIC_DEPTHS_RESOURCE_KEY
        );
        for (ResourceKey<Level> dimension : caveDimensions) {
            event.registerIncomingEffect(
                dimension,
                (received, reason) -> new ReceivingLevelScreen(received, ReceivingLevelScreen.Reason.OTHER)
            );
            event.registerOutgoingEffect(
                dimension,
                (received, reason) -> new ReceivingLevelScreen(received, ReceivingLevelScreen.Reason.OTHER)
            );
        }
    }
}
