package net.tysontheember.echoesofbattle.sound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class LoudSoundPacket {

    private final ResourceLocation soundId;
    private final Vec3 pos;
    private final float pitch;
    private final float volume;

    public LoudSoundPacket(ResourceLocation soundId, Vec3 pos, float pitch, float volume) {
        this.soundId = soundId;
        this.pos = pos;
        this.pitch = pitch;
        this.volume = volume;
    }

    public static void encode(LoudSoundPacket packet, FriendlyByteBuf buf) {
        buf.writeResourceLocation(packet.soundId);
        buf.writeDouble(packet.pos.x);
        buf.writeDouble(packet.pos.y);
        buf.writeDouble(packet.pos.z);
        buf.writeFloat(packet.pitch);
        buf.writeFloat(packet.volume);
    }

    public static LoudSoundPacket decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float pitch = buf.readFloat();
        float volume = buf.readFloat();
        return new LoudSoundPacket(id, new Vec3(x, y, z), pitch, volume);
    }

    public static void handle(LoudSoundPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () ->
                        () -> ClientSoundHandler.playSound(packet.soundId, packet.pos, packet.pitch, packet.volume)
                )
        );
        ctx.get().setPacketHandled(true);
    }
}
