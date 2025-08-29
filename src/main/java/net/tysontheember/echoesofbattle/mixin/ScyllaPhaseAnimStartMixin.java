package net.tysontheember.echoesofbattle.mixin;

import com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.Scylla.Scylla_Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.tysontheember.echoesofbattle.sound.ModSounds;
import net.tysontheember.echoesofbattle.util.CommandUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.Scylla.Scylla_Entity$Scylla_EntityPhaseChangeGoal")
public abstract class ScyllaPhaseAnimStartMixin {

    @Shadow @Final protected Scylla_Entity entity;
    @Shadow @Final private int phasestate;

    @Inject(
            method = "start",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/L_Ender/cataclysm/entity/InternalAnimationMonster/IABossMonsters/Scylla/Scylla_Entity;setAttackState(I)V",
                    shift = At.Shift.AFTER
            )
    )
    private void echoesofbattle$afterPhaseChangeAnimationStarts(CallbackInfo ci) {
        Level lvl = entity.level();
        if (!(lvl instanceof ServerLevel serverLevel)) return;

        if (phasestate == 1) {
            CommandUtils.runCommandNear(
                    serverLevel,
                    entity.position(),
                    "immersivemessages sendcustom @s {font:'immersivemessages:norse', color:aqua, size:5, bold:1, y:150, typewriter:0, shake:0} 4 Be washed from my sight!",
                    32.0
            );
            serverLevel.playSound(
                    null,
                    entity,
                    ModSounds.SCYLLA_PHASE1.get(),
                    SoundSource.HOSTILE,
                    3.0F,
                    1.0F
            );
        } else if (phasestate == 2) {
            CommandUtils.runCommandNear(
                    serverLevel,
                    entity.position(),
                    "immersivemessages sendcustom @s {font:'immersivemessages:norse', color:aqua, size:5, bold:1, y:150, typewriter:0, shake:0} 5 Drown in lightning!",
                    32.0
            );
            serverLevel.playSound(
                    null,
                    entity,
                    ModSounds.SCYLLA_PHASE2.get(),
                    SoundSource.HOSTILE,
                    3.0F,
                    1.0F
            );
        }
    }
}
