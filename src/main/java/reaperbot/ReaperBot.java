package reaperbot;

import arc.files.Fi;
import arc.util.Log;
import net.dv8tion.jda.api.*;

import javax.security.auth.login.LoginException;

public class ReaperBot {
    public static final long guildID = 741011186916524213L; // id бота
    public static final long muteRoleID = 745156292989026334L; // id мьют роли
    public static final Fi prefsFile = new Fi("prefs.json");
    public static final long serverChannelID = 746000026269909002L; // наши-сервера
    public static final long commandChannelID = 744874986073751584L; // using-bots--использование-ботов
    public static final long mapsChannelID = 744906782370955274L; // maps--карты
    public static final long moderationChannelID = 0L; //
    public static final long schematicsChannelID = 744906867183976569L; // schematics--схемы
    public static final boolean sendWelcomeMessages = false;
    public static final long messageDeleteTime = 20000; // 20 секунд

    public static JDA jda;

    public static ContentHandler contentHandler;
    public static Messages messages;
    public static Commands commands;
    public static Net net;
    public static Prefs prefs;

    public static void main(String[] args) throws InterruptedException, LoginException {
        init();

        String token = args.length > 0 ? args[0] : System.getProperty("token");
        Log.info("Found token: {0}", token);

        jda = JDABuilder.createDefault(token)
                .addEventListeners(messages)
                .build();

        jda.awaitReady();
        messages.guild = jda.getGuildById(guildID);

        Log.info("Discord bot up.");
    }

    public static void init(){
        contentHandler = new ContentHandler();
        messages = new Messages();
        commands = new Commands();
        net = new Net();
        prefs = new Prefs(prefsFile);
    }
}
