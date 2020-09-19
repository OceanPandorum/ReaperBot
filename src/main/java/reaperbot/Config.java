package reaperbot;

import arc.struct.Array;
import org.hjson.JsonArray;
import org.hjson.JsonValue;

import static reaperbot.ReaperBot.*;

public class Config {

    public Array<String> getArray(String name){
        try{
            JsonArray array = JsonValue.readJSON(configFile.readString()).asObject().get(name).asArray();
            Array<String> strings = new Array<>();
            array.forEach(s -> strings.add(s.asString()));
            return strings;
        }catch (Exception e){
            return new Array<>();
        }
    }

    public JsonArray getJArray(String name){
        try{
            return JsonValue.readJSON(configFile.readString()).asObject().get(name).asArray();
        }catch (Exception e){
            return new JsonArray();
        }
    }

    public String get(String name){
        return JsonValue.readJSON(configFile.readString()).asObject().getString(name, "");
    }
}
