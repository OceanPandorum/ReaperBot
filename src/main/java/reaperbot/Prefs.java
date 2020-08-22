package reaperbot;

import arc.files.Fi;
import arc.struct.Array;
import com.eclipsesource.json.*;

public class Prefs{
    private JsonObject object;
    private JsonArray servers;
    private Fi file;

    public Prefs(Fi file){
        this.file = file;

        try{
            object = JsonObject.readFrom(file.readString());
            servers = object.get("servers").asArray();
            servers.get(0);
        }catch(Exception e){
            object = new JsonObject();
            servers = new JsonArray();

            object.add("servers", servers);
            file.writeString(object.toString());
        }
    }

    public Array<String> getArray(String name){
        try {
            Array<String> strings = new Array<>();
            JsonArray array = object.get(name).asArray();
            array.forEach(s -> strings.add(s.asString()));
            return strings;
        } catch (Exception e) {
            return Array.with("[]");
        }
    }

    public void putArray(String name, Array<String> arr){
        JsonArray jsonArray = new JsonArray();
        arr.forEach(jsonArray::add);
        object.add(name, jsonArray);
        save();
    }

    public String get(String name, String def){
        try {
            return object.get(name).asString();
        }catch (Exception e){
            return def;
        }
    }

    public void put(String name, String value){
        object.add(name, value);
        save();
    }

    public void save(){
        file.writeString(object.toString(WriterConfig.PRETTY_PRINT));
    }
}
