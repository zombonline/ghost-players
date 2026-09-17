package zombonline.ghostplayers;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.Identifier;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zombonline.ghostplayers.data.ActiveGhostsData;

public class GhostPlayers implements ModInitializer {
	public static final String MOD_ID = "ghost-players";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerTickEvents.END_LEVEL_TICK.register((level) -> {
			if(ActiveGhostsData.get(level.getServer()).getActiveGhosts().stream().count() <= 0) {
				var player = level.getRandomPlayer();
				if(player == null) {

					LOGGER.info("No player found.");
					return;
				}
				LOGGER.info("Creating mann at {}", player.getName().getString());
				Mannequin mannequin = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.MOB_SUMMONED);
				LOGGER.info("mannequin summoned with uuid {} at {}", mannequin.getStringUUID(), (mannequin.getX() +", " + mannequin.getY() + ", " + mannequin.getZ()));
                GameProfile profile = player.getGameProfile();
				var resolve = ResolvableProfile.createResolved(profile);
				var profileAccessor = EntityDataSerializers.RESOLVABLE_PROFILE.createAccessor(17);
				mannequin.getEntityData().set(profileAccessor, resolve);
				level.addFreshEntity(mannequin);
				mannequin.setPos(player.getX(), player.getY(), player.getZ());
				ActiveGhostsData.get(level.getServer()).add(mannequin.getUUID(), player.getUUID(), 500);
			} else {
				ActiveGhostsData.get(level.getServer()).getActiveGhosts().forEach(ghost -> {
					LOGGER.info("Working on ghost {}", ghost.mannequinId());
					var player = level.getRandomPlayer();
					if(player== null)
						return;
					var ghostEntity = level.getEntity(ghost.mannequinId());
					LOGGER.info("ghost entity exists {}", ghostEntity);
					if(ghostEntity == null) {
						ActiveGhostsData.get(level.getServer()).remove(ghost.mannequinId());
						return;
					}
					ghostEntity.setPos(player.getX()+3,player.getY(), player.getZ());
				});
			}

		});
		LOGGER.info("Loaded {}", MOD_ID);
	}



	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
