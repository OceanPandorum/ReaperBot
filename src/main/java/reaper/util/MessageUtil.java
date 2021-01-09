package reaper.util;

import java.util.Objects;

public abstract class MessageUtil{

    private MessageUtil(){}

    public static String trimTo(String text, int maxLength){
        Objects.requireNonNull(text, "text");
        return text.length() >= maxLength ? (text.substring(0, maxLength - 4) + "...") : text;
    }
}
