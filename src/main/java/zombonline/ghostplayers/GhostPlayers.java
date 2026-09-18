package zombonline.ghostplayers;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.Identifier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
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
import zombonline.ghostplayers.data.ActiveGhostsData;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class GhostPlayers implements ModInitializer {
	public static final String MOD_ID = "ghost-players";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register((server) -> {
			if(ActiveGhostsData.get(server).getActiveGhosts().stream().count() <= 0) {
				var player = server.overworld().getRandomPlayer();
				if(player == null) {

					LOGGER.info("No player found.");
					return;
				}
				LOGGER.info("Creating mann at {}", player.getName().getString());
				Mannequin mannequin = EntityTypes.MANNEQUIN.create(server.overworld(), EntitySpawnReason.MOB_SUMMONED);
				var pos = getPositionNearby(server.overworld(), player);
				if(pos == null)
					return;
				mannequin.setPos(Vec3.atCenterOf(pos));
				LOGGER.info("mannequin summoned with uuid {} at {}", mannequin.getStringUUID(), (mannequin.getX() +", " + mannequin.getY() + ", " + mannequin.getZ()));
                GameProfile profile = player.getGameProfile();
				var resolve = ResolvableProfile.createResolved(profile);
				var profileAccessor = EntityDataSerializers.RESOLVABLE_PROFILE.createAccessor(17);
				mannequin.getEntityData().set(profileAccessor, resolve);
//				mannequin.setNoGravity(true);
				AttributeInstance stepup = mannequin.getAttribute(Attributes.STEP_HEIGHT);
				if (stepup != null) {
					stepup.setBaseValue(3);
				}
				server.overworld().addFreshEntity(mannequin);
				ActiveGhostsData.get(server).add(mannequin.getUUID(), player.getUUID(), 500, GhostBehaviour.RUN_PAST);

			} else {
				var activeGhostSnapshot = new HashSet<>(ActiveGhostsData.get(server).getActiveGhosts());
				activeGhostSnapshot.forEach(ghost -> {
					processActiveGhost(server.overworld(), ghost);
				});
			}

		});
		LOGGER.info("Loaded {}", MOD_ID);
	}

	private void processActiveGhost(ServerLevel level, ActiveGhostsData.ActiveGhost activeGhost) {
		var ghostEntity = level.getEntity(activeGhost.mannequinId());
		if(ghostEntity == null) {
			ActiveGhostsData.get(level.getServer()).remove(activeGhost.mannequinId());
			return;
		}
		switch (activeGhost.behaviour()) {
			case RUN_TOWARDS -> runTowards(level, activeGhost);
			case RUN_PAST -> runPast(level,activeGhost);
		}
	}

	private void runPast(ServerLevel level, ActiveGhostsData.ActiveGhost ghost) {

		var ghostEntity = level.getEntity(ghost.mannequinId());
		if(ghost.destination() == BlockPos.ZERO) {
			var playerEntity = level.getEntity(ghost.targetID());
			var dest = getPositionNearby(level,playerEntity);
			if(dest== null) {
				destroyGhost(level,ghost.mannequinId());
			}
			ActiveGhostsData.get(level.getServer()).setDestination(ghost, getPositionNearby(level, playerEntity));
		}
		var distance = moveGhost(ghostEntity, Vec3.atCenterOf(ghost.destination()), true);
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
			ActiveGhostsData.get(level.getServer()).remove(entity.getUUID());
			entity.discard();
			return;
		}
	}

	private double moveGhost(Entity entity, Vec3 destination, boolean lookatStepDestnation) {
		var distanceFromPlayer = entity.position().vectorTo(destination);
		var distanceSqr = (distanceFromPlayer.x * distanceFromPlayer.x) + (distanceFromPlayer.z * distanceFromPlayer.z);

		var directionVector = distanceFromPlayer.normalize();
		var horizontalStep = directionVector.multiply(0.45, 0.0, 0.45);
		entity.move(MoverType.SELF, horizontalStep);
		entity.resetFallDistance();
		if(lookatStepDestnation)
			entity.lookAt(EntityAnchorArgument.Anchor.EYES, horizontalStep.add(0,1.7,0));
		return distanceSqr;
	}

	private BlockPos getPositionNearby(ServerLevel level, Entity player) {
		var from = player.blockPosition();
		int MIN_RANGE = 10;
		int MAX_RANGE = 30;
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

				Vec3 start = player.getEyePosition();
				Vec3 end = Vec3.atCenterOf(feet.above());

				ClipContext context = new ClipContext(
						start,
						end,
						ClipContext.Block.VISUAL,
						ClipContext.Fluid.NONE,
						player
				);

				BlockHitResult hit = level.clip(context);

				if (hit.getType() != HitResult.Type.MISS) {
					continue;
				}

				return feet;
			}
		}

		return null;
	}



	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
