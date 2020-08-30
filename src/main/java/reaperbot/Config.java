package reaperbot;

import arc.files.Fi;
import arc.struct.Array;
import org.hjson.*;

public class Config {
    private final Fi prefsFile = new Fi("prefs.json");

    public Array<String> getArray(String name){
        try{
            JsonArray array = JsonValue.readJSON(prefsFile.readString()).asObject().get(name).asArray();
            Array<String> strings = new Array<>();
            array.forEach(s -> strings.add(s.asString()));
            return strings;
        }catch (Exception e){
            return Array.with("");
        }
    }

    public JsonArray getJArray(String name){
        try{
            return JsonValue.readJSON(prefsFile.readString()).asObject().get(name).asArray();
        }catch (Exception e){
            return new JsonArray();
        }
    }

    public String get(String name){
        try{
            return JsonValue.readJSON(prefsFile.readString()).asObject().get(name).asString();
        }catch(Exception e){
            return "";
        }
    }
}
