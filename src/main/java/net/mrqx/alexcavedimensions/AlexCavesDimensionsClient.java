package net.mrqx.alexcavedimensions;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = AlexCavesDimensions.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(Dist.CLIENT)
public class AlexCavesDimensionsClient {

    public AlexCavesDimensionsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
