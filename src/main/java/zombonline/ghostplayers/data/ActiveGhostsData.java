package zombonline.ghostplayers.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import zombonline.ghostplayers.GhostBehaviour;
import zombonline.ghostplayers.GhostPlayers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ActiveGhostsData extends SavedData {

    public record ActiveGhost(
            UUID mannequinId,
            UUID targetID,
            long lifetimeTicksRemaining,
            GhostBehaviour behaviour,
            BlockPos destination,
            long ticksInPlayerView
    ) {

        public static final Codec<ActiveGhost> CODEC =
                RecordCodecBuilder.create(instance -> {
                    return instance.group(
                            UUIDUtil.STRING_CODEC.
                                    fieldOf("mannequin_ID")
                                    .forGetter(ActiveGhost::mannequinId),

                            UUIDUtil.STRING_CODEC.
                                    fieldOf("targetId").
                                    forGetter(ActiveGhost::targetID),

                            Codec.LONG.
                                    fieldOf("lifetime_ticks_remaining")
                                    .forGetter(ActiveGhost::lifetimeTicksRemaining),

                            GhostBehaviour.CODEC
                                    .fieldOf("behaviour")
                                    .forGetter(ActiveGhost::behaviour),

                            BlockPos.CODEC
                                    .fieldOf("destination")
                                    .forGetter(ActiveGhost::destination),

                            Codec.LONG.
                                    fieldOf("ticks_in_player_view")
                                    .forGetter(ActiveGhost::ticksInPlayerView)



                            ).apply(instance, ActiveGhost::new);
                });

    }

    private final Set<ActiveGhost> activeGhosts = new HashSet<>();
    public static final Codec<ActiveGhostsData> CODEC =
            ActiveGhost.CODEC.listOf()
                    .xmap(
                            list -> {
                                ActiveGhostsData data =
                                        new ActiveGhostsData();

                                data.activeGhosts.addAll(list);

                                return data;
                            },
                            data -> data.activeGhosts.stream().toList()
                    );

    private static final SavedDataType<ActiveGhostsData> TYPE =
            new SavedDataType<>(
                    GhostPlayers.id("active_ghosts"),
                    ActiveGhostsData::new,
                    CODEC,
                    null
            );

    public void add(UUID mannequin, UUID target,long lifetime, GhostBehaviour behaviour) {
        activeGhosts.add(new ActiveGhost(mannequin, target, lifetime, behaviour, BlockPos.ZERO, 0l));
        GhostPlayers.LOGGER.info("Active ghost stored in data, current count {}", activeGhosts.size());
    }
    public void remove(UUID mannequin) {
        activeGhosts.removeIf(ghost -> ghost.mannequinId.equals(mannequin));
    }

    public Set<ActiveGhost> getActiveGhosts() {
        return activeGhosts;
    }

    public static ActiveGhostsData get(MinecraftServer server) {
        ServerLevel level = server.getLevel(ServerLevel.OVERWORLD);

        if (level == null) {
            throw new IllegalStateException(
                    "Overworld is not available"
            );
        }

        return level
                .getDataStorage()
                .computeIfAbsent(TYPE);
    }

    public void setDestination(ActiveGhost ghostToUpdate, BlockPos newDestination) {
        var newGhost = new ActiveGhost(ghostToUpdate.mannequinId,
                ghostToUpdate.targetID,
                ghostToUpdate.lifetimeTicksRemaining,
                ghostToUpdate.behaviour,
                newDestination,
                ghostToUpdate.ticksInPlayerView);
        activeGhosts.remove(ghostToUpdate);
        activeGhosts.add(newGhost);
    }
    public void incrementTicksInPlayerView(ActiveGhost ghostToUpdate) {
        var newGhost = new ActiveGhost(ghostToUpdate.mannequinId,
                ghostToUpdate.targetID,
                ghostToUpdate.lifetimeTicksRemaining,
                ghostToUpdate.behaviour,
                ghostToUpdate.destination,
                ghostToUpdate.ticksInPlayerView+1);
        activeGhosts.remove(ghostToUpdate);
        activeGhosts.add(newGhost);
    }

}
