package zombonline.ghostplayers;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.Identifier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zombonline.ghostplayers.config.GhostPlayersConfig;
import zombonline.ghostplayers.data.ActiveGhostsData;
import zombonline.ghostplayers.data.GhostProfilesData;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class GhostPlayers implements ModInitializer {
	public static final String MOD_ID = "ghost-players";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static GhostProfilesData ghostProfilesData;
	public static ActiveGhostsData activeGhostsData;
	public static SpawnManager spawnManager;
	public static GhostPlayersConfig config;

	@Override
	public void onInitialize() {
		config = GhostPlayersConfig.load();
		spawnManager = new SpawnManager();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ghostProfilesData = GhostProfilesData.get(server);
			activeGhostsData = ActiveGhostsData.get(server);
			spawnManager.init(server);


		});
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
					if(!config.addNewJoinedPlayersToProfilePool)
						return;
					ServerPlayer player = listener.getPlayer();
					ghostProfilesData.addPlayer(player);
				}
		);

		ServerTickEvents.END_SERVER_TICK.register((server) -> {
			spawnManager.onTick(server);

			var activeGhostSnapshot = new HashSet<>(ActiveGhostsData.get(server).getActiveGhosts());
			activeGhostSnapshot.forEach(ghost -> {
				processActiveGhost(server.overworld(), ghost);
			});


		});
		LOGGER.info("Loaded {}", MOD_ID);
	}

	private void processActiveGhost(ServerLevel level, ActiveGhostsData.ActiveGhost activeGhost) {
		var ghostEntity = level.getEntity(activeGhost.mannequinId());
		if(ghostEntity == null) {
			ActiveGhostsData.get(level.getServer()).remove(activeGhost.mannequinId());
			return;
		}

		if(entityCanSee(level.getEntity(activeGhost.targetID()), ghostEntity,0.75f)) {
			ActiveGhostsData.get(level.getServer()).incrementTicksInPlayerView(activeGhost);
			if(activeGhost.ticksInPlayerView() > 5L) {

				destroyGhost(level, activeGhost.mannequinId());
				return;
			}
		}

		switch (activeGhost.behaviour()) {
			case RUN_TOWARDS -> runTowards(level, activeGhost);
			case RUN_PAST -> runPast(level,activeGhost);
		}
	}

	public static boolean entityCanSee(Entity viewer, BlockPos blockPos, float viewRange) {
		return entityCanSee(viewer, Vec3.atCenterOf(blockPos), viewRange);
	}

	public static boolean entityCanSee(Entity viewer, Entity target, float viewRange) {
		return entityCanSee(viewer, target.position(), viewRange) || entityCanSee(viewer, target.getEyePosition(), viewRange);
	}

	public static boolean entityCanSee(Entity viewer, Vec3 targetPos, float viewRange) {
		Vec3 look = viewer.getLookAngle().normalize();

		Vec3 toTarget = targetPos
				.subtract(viewer.getEyePosition())
				.normalize();

		double dot = look.dot(toTarget);

		return dot > 0.7 && viewer.level().clip(
				new ClipContext(
						viewer.getEyePosition(),
						targetPos,
						ClipContext.Block.VISUAL,
						ClipContext.Fluid.NONE,
						viewer
				)
		).getType() == HitResult.Type.MISS;
	}
	private void runPast(ServerLevel level, ActiveGhostsData.ActiveGhost ghost) {

		var ghostEntity = level.getEntity(ghost.mannequinId());
		if(ghost.destination() == BlockPos.ZERO) {
			var playerEntity = level.getEntity(ghost.targetID());
			var dest = getPositionNearby(level,playerEntity);
			ActiveGhostsData.get(level.getServer()).setDestination(ghost, dest);
		}
		if(ghost.destination() == null) {
			destroyGhost(level,ghost.mannequinId());
			return;
		}		var distance = moveGhost(ghostEntity, Vec3.atCenterOf(ghost.destination()), true);
		if(distance <= 2d)
		{
			destroyGhost(level, ghost.mannequinId());
			return;
		}
	}

	private void destroyGhost(ServerLevel level, UUID id) {
		ActiveGhostsData.get(level.getServer()).remove(id);
		level.getEntity(id).discard();
	}

	private void runTowards(ServerLevel level, ActiveGhostsData.ActiveGhost ghost) {

		var entity = level.getEntity(ghost.mannequinId());
		var playerEntity = level.getEntity(ghost.targetID());

		var lookatPos = playerEntity.position().add(0,1.7,0);
		entity.lookAt(EntityAnchorArgument.Anchor.EYES, lookatPos);

		var distanceFromPlayer = moveGhost(entity, playerEntity.position(), false);
		if(distanceFromPlayer <= 2d)
		{
			destroyGhost(level, ghost.mannequinId());
			return;
		}
	}

	private double moveGhost(Entity entity, Vec3 destination, boolean lookatStepDestnation) {
		var distanceFromPlayer = entity.position().vectorTo(destination);
		var distanceSqr = (distanceFromPlayer.x * distanceFromPlayer.x) + (distanceFromPlayer.z * distanceFromPlayer.z);

		var directionVector = distanceFromPlayer.normalize();
		var horizontalStep = directionVector.multiply(0.25, 0.0, 0.25);
		entity.move(MoverType.SELF, horizontalStep);
		entity.resetFallDistance();
		if(lookatStepDestnation)
			entity.lookAt(EntityAnchorArgument.Anchor.EYES, entity.position().add(horizontalStep).add(0,1.7,0));
		return distanceSqr;
	}

	public static BlockPos getPositionNearby(ServerLevel level, Entity player) {
		var from = player.blockPosition();
		int MIN_RANGE = config.minimumDistance;
		int MAX_RANGE = config.maximumDistance;
		int Y_RANGE = 15;
		int MAX_ATTEMPTS = 10;

		int x;
		int z;
		var random = new Random();

		for(int i = 0; i < MAX_ATTEMPTS; i++) {
			LOGGER.info("Attmept to spawn {}", i);
			do {
				x = from.getX() + random.nextInt(-MAX_RANGE, MAX_RANGE);
				z = from.getZ() + random.nextInt(-MAX_RANGE, MAX_RANGE);
			} while (from.distSqr(new BlockPos(x, from.getY(), z)) < MIN_RANGE * MIN_RANGE
					|| from.distSqr(new BlockPos(x, from.getY(), z)) > MAX_RANGE * MAX_RANGE);

			for (int y = from.getY() - Y_RANGE; y <= from.getY() + Y_RANGE; y++) {
				BlockPos feet = new BlockPos(x, y, z);
				BlockPos ground = feet.below();

				if (!level.getBlockState(ground).isSolid()) {
					continue;
				}

				if (!level.getBlockState(feet).isAir()) {
					continue;
				}

				if (!level.getBlockState(feet.above()).isAir()) {
					continue;
				}

				if(entityCanSee(player, feet, 0.6f) || entityCanSee(player, feet.above(),0.6f))
					continue;

				return feet;
			}
		}

		return null;
	}



	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
