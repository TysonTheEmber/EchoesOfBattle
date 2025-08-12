package net.tysontheember.echoesofbattle.util;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class CommandUtils {

    public static void runCommandNear(ServerLevel level, Vec3 center, String command, double radius) {
        AABB area = new AABB(center.subtract(radius, radius, radius), center.add(radius, radius, radius));
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, area);
        for (ServerPlayer player : players) {
            CommandSourceStack source = player.createCommandSourceStack()
                    .withPermission(4)
                    .withSuppressedOutput()
                    .withPosition(player.position());
            level.getServer().getCommands().performPrefixedCommand(source, command);
        }
    }
}
