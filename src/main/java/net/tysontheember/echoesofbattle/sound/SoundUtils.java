package net.tysontheember.echoesofbattle.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.tysontheember.echoesofbattle.EchoesOfBattle;
import net.tysontheember.echoesofbattle.sound.LoudSoundPacket;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

public class SoundUtils {

    /**
     * Sends a loud sound packet to all players within 64 blocks of the position.
     *
     * @param level the server level
     * @param pos the position to play the sound at
     * @param soundId the ResourceLocation of the sound event
     * @param volume volume multiplier (e.g., 10.0f for very loud)
     * @param pitch pitch of the sound
     */
    public static void playLoudSound(ServerLevel level, Vec3 pos, ResourceLocation soundId, float volume, float pitch) {
        double radius = 64.0D;
        AABB area = new AABB(pos.subtract(radius, radius, radius), pos.add(radius, radius, radius));
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, area);

        LoudSoundPacket packet = new LoudSoundPacket(soundId, pos, pitch, volume);

        for (ServerPlayer player : players) {
            EchoesOfBattle.CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }

    public static void playLoudSound(ServerLevel level, BlockPos pos, ResourceLocation soundId, float volume, float pitch) {
        playLoudSound(level, Vec3.atCenterOf(pos), soundId, volume, pitch);
    }
}
