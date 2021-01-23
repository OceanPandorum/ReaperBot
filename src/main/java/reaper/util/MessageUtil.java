package reaper.util;

import arc.util.Strings;

import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

public abstract class MessageUtil{

    private MessageUtil(){}

    public static String trimTo(String text, int maxLength){
        Objects.requireNonNull(text, "text");
        return text.length() >= maxLength ? (text.substring(0, maxLength - 4) + "...") : text;
    }

    public static boolean canParseInt(String message){
        return Strings.canParseInt(message) && Strings.parseInt(message) > 0;
    }

    public static InputStream download(String url){
        try{
            return new URL(url).openConnection().getInputStream();
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
}
