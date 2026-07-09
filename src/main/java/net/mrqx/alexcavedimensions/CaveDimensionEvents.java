package net.mrqx.alexcavedimensions;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

@EventBusSubscriber
public class CaveDimensionEvents {

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getLevel() instanceof Level level && level.dimension().location().getNamespace().equals(AlexCavesDimensions.MODID)) {
            if (EntityType.getKey(event.getEntity().getType()).getNamespace().equals(AlexsCaves.MODID)) {
                event.setResult(MobSpawnEvent.PositionCheck.Result.SUCCEED);
            }
        }
    }
}
