package reaper.json;

import com.google.gson.*;
import discord4j.common.util.Snowflake;

import java.lang.reflect.Type;

public final class SnowflakeSerializer implements JsonSerializer<Snowflake>, JsonDeserializer<Snowflake>{
    @Override
    public JsonElement serialize(Snowflake src, Type typeOfSrc, JsonSerializationContext context){
        return new JsonPrimitive(src.asString());
    }

    @Override
    public Snowflake deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException{
        return Snowflake.of(json.getAsString());
    }
}
