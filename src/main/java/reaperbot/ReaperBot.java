package reaperbot;

import arc.Files;
import arc.files.Fi;
import arc.util.I18NBundle;
import arc.util.Log;
import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;
import java.util.Locale;

public class ReaperBot{

    public static final long serverChannelID = 746000026269909002L;
    public static final long commandChannelID = 744874986073751584L;
    public static final long mapsChannelID = 744906782370955274L;
    public static final long schematicsChannelID = 744906867183976569L;

    public static final long messageDeleteTime = 20000; // 20 секунд
    public static final long guildID = 744814929701240882L; // id сервера

    public static Fi configFile = new Fi("prefs.json");

    public static ContentHandler contentHandler;
    public static Listener listener;
    public static Commands commands;
    public static Net net;
    public static Config config;
    public static I18NBundle bundle;

    public static void main(String[] args) throws InterruptedException, LoginException{
        init();

        listener.jda = JDABuilder.createDefault(config.get("token"))
                .addEventListeners(listener)
                .build();
        listener.jda.awaitReady();

        listener.guild = listener.jda.getGuildById(guildID);

        Log.info("Discord bot up.");

        if(args.length > 0 && args[0].equalsIgnoreCase("-info")) listener.sendInfo();
    }

    private static void init(){
        config = new Config();
        bundle = I18NBundle.createBundle(new Fi("bundle", Files.FileType.classpath), new Locale(""), "Windows-1251");
        contentHandler = new ContentHandler();
        listener = new Listener();
        commands = new Commands();
        net = new Net();
    }
}
