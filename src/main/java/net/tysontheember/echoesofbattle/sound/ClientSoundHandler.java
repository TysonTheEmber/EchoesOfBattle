package net.tysontheember.echoesofbattle.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.phys.Vec3;

public class ClientSoundHandler {

    public static void playSound(ResourceLocation soundId, Vec3 pos, float pitch, float volume) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
        if (sound != null && Minecraft.getInstance().level != null) {
            Minecraft.getInstance().getSoundManager().play(
                    new SimpleSoundInstance(
                            sound.getLocation(),
                            SoundSource.VOICE,
                            volume,
                            pitch,
                            Minecraft.getInstance().level.random,
                            false,
                            0,
                            SoundInstance.Attenuation.NONE,
                            (float) pos.x,
                            (float) pos.y,
                            (float) pos.z,
                            true
                    )
            );
        }
    }
}
