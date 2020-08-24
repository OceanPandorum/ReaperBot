package reaperbot;

import arc.files.Fi;
import arc.struct.Array;
import org.hjson.*;

public class Config {
    private JsonObject object;
    private JsonArray servers;

    public Config(Fi file){
        try{
            object = JsonValue.readJSON(file.readString()).asObject();
            servers = object.get("servers").asArray();
        }catch(Exception e){
            object = new JsonObject();
            servers = new JsonArray();

            object.add("servers", servers);
            file.writeString(object.toString());
        }
    }

    public Array<String> getServerArray(){
        try {
            Array<String> strings = new Array<>();
            servers.forEach(s -> strings.add(s.asString()));
            return strings;
        } catch (Exception e) {
            return Array.with("");
        }
    }

    public String get(String name, String def){
        try {
            return object.get(name).asString();
        }catch (Exception e){
            return def;
        }
    }

    public String get(String name){
        return get(name, "");
    }
}
