package reaperbot.service;

public interface MessageService{

    String get(String key);

    String format(String key, Object... args);
}
