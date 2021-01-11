package reaper.util;

import discord4j.common.util.Snowflake;
import reactor.util.annotation.NonNull;

import java.util.Objects;

public abstract class MessageUtil{

    private MessageUtil(){}

    public static String trimTo(String text, int maxLength){
        Objects.requireNonNull(text, "text");
        return text.length() >= maxLength ? (text.substring(0, maxLength - 4) + "...") : text;
    }

    public static boolean canParseId(String message){
        try{
            Snowflake.of(message);
            return true;
        }catch(Exception e){
            return false;
        }
    }

    public static Snowflake parseUserId(@NonNull String message){
        message = message.replaceAll("[<>@!]", "");
        return canParseId(message) ? Snowflake.of(message) : null;
    }
}
