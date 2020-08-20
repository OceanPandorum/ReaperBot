package reaperbot;

import arc.files.Fi;
import arc.util.Log;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;

public class ReaperBot {
    public static final String releasesURL = "https://api.github.com/repos/Anuken/Mindustry/releases";
    public static final long guildID = 741011186916524213L; // id бота
    public static final Fi prefsFile = new Fi("prefs.json");
    public static final long modChannelID = 0L; //
    public static final long pluginChannelID = 744853784038998058L;
    public static final long serverChannelID = 746000026269909002L; // наши-сервера
    public static final long logChannelID = 746007302900809768L; // логи
    public static final long commandChannelID = 744874986073751584L;
    public static final long announcementsChannelID = 0L; //
    public static final long screenshotsChannelID = 0L; //
    public static final long suggestionsChannelID = 0L; //
    public static final long mapsChannelID = 744906782370955274L;
    public static final long moderationChannelID = 0L; //
    public static final long schematicsChannelID = 744906867183976569L;
    public static final boolean sendWelcomeMessages = false;
    public static final long messageDeleteTime = 20000;

    public static JDA jda;

    public static ServerBridge server;
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
        server = new ServerBridge();
        contentHandler = new ContentHandler();
        messages = new Messages();
        commands = new Commands();
        net = new Net();
        prefs = new Prefs(prefsFile);
    }
}
