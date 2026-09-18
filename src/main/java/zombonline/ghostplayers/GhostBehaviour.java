package zombonline.ghostplayers;

import com.mojang.serialization.Codec;

public enum GhostBehaviour {
    RUN_PAST,
    RUN_TOWARDS,
    HIDE;

    public static final Codec<GhostBehaviour> CODEC =
            Codec.STRING.xmap(GhostBehaviour::valueOf, Enum::name);
}


