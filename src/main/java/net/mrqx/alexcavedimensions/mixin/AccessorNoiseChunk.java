package net.mrqx.alexcavedimensions.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NoiseChunk.class)
public interface AccessorNoiseChunk {

    @Invoker("getInterpolatedState")
    BlockState alex_caves_dimensions$getInterpolatedState();

    @Invoker("cellWidth")
    int alex_caves_dimensions$getCellWidth();

    @Invoker("cellHeight")
    int alex_caves_dimensions$getCellHeight();
}
