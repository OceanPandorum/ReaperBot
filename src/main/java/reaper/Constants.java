package reaper;

import arc.files.Fi;
import com.google.gson.*;
import discord4j.common.util.Snowflake;
import reactor.core.publisher.Mono;
import reaper.util.json.SnowflakeTypeAdapter;

public final class Constants{
    public static final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
            .registerTypeAdapter(Snowflake.class, new SnowflakeTypeAdapter())
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    public static Mono<Snowflake> ownerId;

    public static Fi configFile = Fi.get("prefs.json");
    public static Fi cacheDir = new Fi("cache/");
    public static Fi schemeDir = cacheDir.child("schem/");
    public static Fi mapDir = cacheDir.child("map/");

    public static ContentHandler contentHandler;
    public static Config config;

    static{
        if(!configFile.exists()){
            configFile.writeString(gson.toJson(config = new Config()));
        }else{
            config = gson.fromJson(configFile.reader(), Config.class);
        }

        schemeDir.mkdirs();
        mapDir.mkdirs();
    }
}
