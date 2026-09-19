package zombonline.ghostplayers.data;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import zombonline.ghostplayers.GhostPlayers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GhostProfilesData extends SavedData {

    public record GhostProfile(
            UUID playerId,
            ResolvableProfile playerProfile
    ) {

        public static final Codec<GhostProfile> CODEC =
                RecordCodecBuilder.create(instance -> {
                    return instance.group(
                            UUIDUtil.STRING_CODEC.
                                    fieldOf("player_id")
                                    .forGetter(GhostProfile::playerId),
                            ResolvableProfile.CODEC.
                                    fieldOf("player_profile")
                                    .forGetter(GhostProfile::playerProfile)


                    ).apply(instance, GhostProfile::new);
                });
    }

    public static final Codec<GhostProfilesData> CODEC =
            GhostProfilesData.GhostProfile.CODEC.listOf()
                    .xmap(
                            list -> {
                                GhostProfilesData data =
                                        new GhostProfilesData();

                                data.profiles.addAll(list);

                                return data;
                            },
                            data -> data.profiles.stream().toList()
                    );

    private static final SavedDataType<GhostProfilesData> TYPE =
            new SavedDataType<>(
                    GhostPlayers.id("ghost_profiles"),
                    GhostProfilesData::new,
                    CODEC,
                    null
            );

    public static GhostProfilesData get(MinecraftServer server) {
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

    private final List<GhostProfile> profiles = new ArrayList<>();

    public void addPlayer(ServerPlayer player) {

    }
    public void addFromUsername(MinecraftServer server, String username) {
        var result = server.services().nameToIdCache().get(username);

        if (result.isEmpty()) {
            GhostPlayers.LOGGER.warn("Could not find player {}", username);
            return;
        }

        var nameAndId = result.get();
        UUID uuid = nameAndId.id();

        if (profiles.stream().anyMatch(profile ->
                profile.playerId().equals(uuid))) {
            return;
        }

        ResolvableProfile profile =
                ResolvableProfile.createUnresolved(uuid);

        profiles.add(new GhostProfile(uuid, profile));

        setDirty();

        GhostPlayers.LOGGER.info(
                "Added {} ({}) to ghost profile pool",
                nameAndId.name(),
                uuid
        );
    }

    public List<GhostProfile> getProfiles() {
        return profiles;
    }

}