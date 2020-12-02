package reaperbot;

import arc.Files;
import arc.files.Fi;
import arc.util.I18NBundle;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import javax.security.auth.login.LoginException;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class ReaperBot{
    public static final long
    serverChannelID = 746000026269909002L,
    commandChannelID = 744874986073751584L,
    logChannelID = 746007302900809768L,
    mapsChannelID = 744906782370955274L,
    schematicsChannelID = 744906867183976569L;

    public static final int messageDeleteTime = 20000; // 20 секунд
    public static final long guildID = 744814929701240882L; // id сервера

    public static Fi configFile;
    public static Fi cacheDir;
    public static Fi schemDir;
    public static Fi mapDir;

    public static ContentHandler contentHandler;
    public static Listener listener;
    public static Logger logger;
    public static Commands commands;
    public static Net net;
    public static Config config;
    public static I18NBundle bundle;

    public static boolean logging;

    public static ScheduledExecutorService service;

    public static void main(String[] args) throws InterruptedException, LoginException{
        init();

        listener.jda = JDABuilder.createDefault(config.get("token"))
                .addEventListeners(listener)
                .disableCache(CacheFlag.VOICE_STATE)
                .build();
        listener.jda.awaitReady();
        if(logging){
            listener.jda.addEventListener(logger);
        }

        listener.guild = listener.jda.getGuildById(guildID);

        if(args.length > 0 && args[0].equalsIgnoreCase("-info")) listener.sendInfo();
    }

    private static void init(){
        configFile = new Fi("prefs.json");
        cacheDir = new Fi("cache/");
        schemDir = cacheDir.child("schem/");
        mapDir = cacheDir.child("map/");

        // Создаём схем и мод папку, кеш папка тоже создаться
        schemDir.mkdirs();
        mapDir.mkdirs();

        service = new ScheduledThreadPoolExecutor(2); // Todo надо бы на него побольше тасков наложить
        config = new Config();
        bundle = I18NBundle.createBundle(new Fi("bundle", Files.FileType.classpath), Locale.ROOT, "Windows-1251");
        contentHandler = new ContentHandler();
        listener = new Listener();
        logging = config.get("logging").equals("on");
        if(logging){
            logger = new Logger();
        }
        commands = new Commands();
        net = new Net();
    }
}
