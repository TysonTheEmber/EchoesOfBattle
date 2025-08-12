package net.tysontheember.echoesofbattle.mixin;

import com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity;
import com.github.L_Ender.lionfishapi.server.animation.Animation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.tysontheember.echoesofbattle.sound.ModSounds;
import net.tysontheember.echoesofbattle.sound.SoundUtils;
import net.tysontheember.echoesofbattle.util.CommandUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Ignis_Entity.class)
public class IgnisPhaseChangeCommandMixin {

    @Unique
    private boolean echoesofbattle$phaseAnnounced2 = false;
    @Unique
    private boolean echoesofbattle$phaseAnnounced3 = false;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void echoesofbattle$onPhaseChangeStart(CallbackInfo ci) {
        Ignis_Entity self = (Ignis_Entity)(Object)this;
        Level level = self.level();
        if (level instanceof ServerLevel serverLevel) {
            Animation anim = self.getAnimation();
            int tick = self.getAnimationTick();

            if (anim == Ignis_Entity.PHASE_2 && tick == 30 && !echoesofbattle$phaseAnnounced2) {
                echoesofbattle$phaseAnnounced2 = true;
                CommandUtils.runCommandNear(serverLevel, self.position(),
                        "immersivemessages sendcustom @s {font:'immersivemessages:norse', color:aqua, size:5, bold:1, y:150, typewriter:0, shake:0} 4 Die!", 32.0);
                SoundUtils.playLoudSound(serverLevel, self.position(), ModSounds.IGNIS_PHASE1.getId(), 3.0F, 1.0F);
            }

            if (anim == Ignis_Entity.PHASE_3 && tick == 40 && !echoesofbattle$phaseAnnounced3) {
                echoesofbattle$phaseAnnounced3 = true;
                CommandUtils.runCommandNear(serverLevel, self.position(),
                        "immersivemessages sendcustom @s {font:'immersivemessages:norse', color:aqua, size:5, bold:1, y:150, typewriter:0, shake:0} 5 I will not fall!", 32.0);
                SoundUtils.playLoudSound(serverLevel, self.position(), ModSounds.IGNIS_PHASE2.getId(), 3.0F, 1.0F);
            }
        }
    }
}
