package reaper;

import arc.files.Fi;
import com.google.gson.*;
import discord4j.common.util.Snowflake;
import reactor.core.publisher.Mono;

public final class Constants{
    public static Snowflake ownerId;

    public static Fi configFile;
    public static Fi cacheDir;
    public static Fi schemeDir;
    public static Fi mapDir;

    public static ContentHandler contentHandler;
    public static Listener listener;
    public static Net net;
    public static Config config;

    public static final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
            .registerTypeAdapter(Snowflake.class, new SnowflakeSerializer())
            .setPrettyPrinting()
            .serializeNulls()
            .create();
}
