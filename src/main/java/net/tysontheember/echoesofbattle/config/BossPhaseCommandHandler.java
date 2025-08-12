package net.tysontheember.echoesofbattle.config;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public class BossPhaseCommandHandler {
    public static void runPhaseCommands(LivingEntity entity, int phase) {
        ServerLevel level = (ServerLevel) entity.level();
        String cmd = switch (phase) {
            case 1 -> "say Phase 1 has begun!";
            case 2 -> "say Phase 2 has begun!";
            default -> null;
        };

        if (cmd != null) {
            level.getServer().getCommands().performPrefixedCommand(
                    entity.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                    cmd
            );
        }
    }
}
