package net.mrqx.alexcavedimensions.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.mrqx.alexcavedimensions.AlexCavesDimensions;
import net.mrqx.alexcavedimensions.client.CaveKeyLoadingClientHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record CaveKeyLoadingPayload(boolean loading) implements CustomPacketPayload {
    public static final Type<CaveKeyLoadingPayload> TYPE = new Type<>(AlexCavesDimensions.id("cave_key_loading"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CaveKeyLoadingPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        CaveKeyLoadingPayload::loading,
        CaveKeyLoadingPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(@NotNull CaveKeyLoadingPayload payload, @NotNull IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() -> CaveKeyLoadingClientHandler.handle(payload.loading()));
        }
    }
}
