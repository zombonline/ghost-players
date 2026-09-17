package zombonline.ghostplayers.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import zombonline.ghostplayers.GhostPlayers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ActiveGhostsData extends SavedData {

    public record ActiveGhost(
            UUID mannequinId,
            UUID targetID,
            long lifetimeTicksRemaining
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
                                    .forGetter(ActiveGhost::lifetimeTicksRemaining)
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
                    GhostPlayers.id("ghost_players"),
                    ActiveGhostsData::new,
                    CODEC,
                    null
            );

    public void add(UUID mannequin, UUID target,long lifetime) {
        activeGhosts.add(new ActiveGhost(mannequin, target, lifetime));
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

}
