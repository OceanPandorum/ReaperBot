package reaper.util.json;

import com.google.gson.*;
import com.google.gson.stream.*;
import discord4j.common.util.Snowflake;

import java.io.IOException;
import java.lang.reflect.Type;

public final class SnowflakeTypeAdapter extends TypeAdapter<Snowflake>{
    @Override
    public void write(JsonWriter out, Snowflake value) throws IOException{
        if(value != null){
            out.value(value.asString());
        }else{
            out.nullValue();
        }
    }

    @Override
    public Snowflake read(JsonReader in) throws IOException{
        return Snowflake.of(in.nextString());
    }
}
