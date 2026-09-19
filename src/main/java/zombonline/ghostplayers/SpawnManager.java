package zombonline.ghostplayers;

import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.Vec3;
import zombonline.ghostplayers.data.ActiveGhostsData;

import java.util.ArrayList;
import java.util.Random;

import static zombonline.ghostplayers.GhostPlayers.*;

public class SpawnManager {
    private int nextSpawnTick = 0;
    private Random random;
    public void init(MinecraftServer server)
    {
        random = new Random();
        nextSpawnTick = server.getTickCount() + random.nextInt(config.spawnIntervalMinTicks, config.spawnIntervalMaxTicks);
    }
    public void onTick(MinecraftServer server)
    {
        if(server.getTickCount() != nextSpawnTick)
            return;

        if(activeGhostsData.getActiveGhosts().size() >= config.maxActiveGhosts)
            return;

        var playerTarget = server.overworld().getRandomPlayer();
        if(playerTarget == null)
            return;

        var ghostProfiles = new ArrayList<>(ghostProfilesData.getProfiles());
        if(!config.ghostCanMatchTargetProfile)
            ghostProfiles.removeIf(ghostProfile -> {
                return ghostProfile.playerId() == playerTarget.getUUID();
            });
        var randomProfile = ghostProfiles.get(random.nextInt(0,ghostProfiles.size()));

        Mannequin mannequin = EntityTypes.MANNEQUIN.create(server.overworld(), EntitySpawnReason.MOB_SUMMONED);
        var pos = GhostPlayers.getPositionNearby(server.overworld(), playerTarget);
        if(pos == null)
            return;
        mannequin.setPos(Vec3.atCenterOf(pos));

        var profileAccessor = EntityDataSerializers.RESOLVABLE_PROFILE.createAccessor(17);
        mannequin.getEntityData().set(profileAccessor, ghostProfilesData.getProfiles().get(new Random().nextInt(0,ghostProfilesData.getProfiles().size())).playerProfile());
        AttributeInstance stepup = mannequin.getAttribute(Attributes.STEP_HEIGHT);
        if (stepup != null) {
            stepup.setBaseValue(3);
        }
        server.overworld().addFreshEntity(mannequin);
        ActiveGhostsData.get(server).add(mannequin.getUUID(), playerTarget.getUUID(), 500, new Random().nextFloat() > 0.5f ? GhostBehaviour.RUN_PAST : GhostBehaviour.RUN_TOWARDS);
    }
}
