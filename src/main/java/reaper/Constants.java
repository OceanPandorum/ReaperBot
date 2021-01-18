package reaper;

import arc.files.Fi;
import com.google.gson.*;
import discord4j.common.util.Snowflake;
import reaper.event.ReactionListener;
import reaper.json.SnowflakeSerializer;

public final class Constants{
    public static final Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
            .registerTypeAdapter(Snowflake.class, new SnowflakeSerializer())
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    public static Snowflake ownerId;

    public static Fi configFile = Fi.get("prefs.json");
    public static Fi cacheDir = new Fi("cache/");
    public static Fi schemeDir = cacheDir.child("schem/");
    public static Fi mapDir = cacheDir.child("map/");

    public static ContentHandler contentHandler;
    public static LContentHandler lcontentHandler;
    public static ReactionListener reactionListener;
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
