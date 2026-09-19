package zombonline.ghostplayers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GhostPlayersConfig {

    private static final Path PATH =
            FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("ghost-players.json");

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    public boolean spawnWithNametags = false;
    public int spawnIntervalMinTicks = 24_000;
    public int spawnIntervalMaxTicks = 72_000;
    public int maxActiveGhosts = 3;

    public int minimumDistance = 10;
    public int maximumDistance = 30;
    public boolean ghostCanMatchTargetProfile;
    public boolean addNewJoinedPlayersToProfilePool;


    public static GhostPlayersConfig load() {

        if (!Files.exists(PATH)) {
            GhostPlayersConfig config = new GhostPlayersConfig();
            config.save();
            return config;
        }

        try {
            String json = Files.readString(PATH);
            return GSON.fromJson(json, GhostPlayersConfig.class);

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to load Ghost Players config",
                    e
            );
        }
    }


    public void save() {

        try {
            Files.createDirectories(PATH.getParent());

            String json = GSON.toJson(this);
            Files.writeString(PATH, json);

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to save Ghost Players config",
                    e
            );
        }
    }
}
