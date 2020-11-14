package reaperbot;

import arc.struct.Seq;
import org.hjson.JsonArray;
import org.hjson.JsonValue;

import static reaperbot.ReaperBot.configFile;

public class Config{

    // TODO надо сериализовать
    public Seq<String> getArray(String name){
        try{
            JsonArray array = JsonValue.readJSON(configFile.readString()).asObject().get(name).asArray();
            Seq<String> strings = new Seq<>();
            array.values().stream().map(JsonValue::asString).forEach(strings::add);
            return strings;
        }catch(Exception e){
            return new Seq<>();
        }
    }

    public JsonArray getJArray(String name){
        try{
            return JsonValue.readJSON(configFile.readString()).asObject().get(name).asArray();
        }catch(Exception e){
            return new JsonArray();
        }
    }

    public String get(String name){
        return JsonValue.readJSON(configFile.readString()).asObject().getString(name, "");
    }
}
