package reaperbot;

import arc.files.Fi;
import arc.util.Log;
import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;

public class ReaperBot {
    /*
     * id каналов
     */
    public static final long serverChannelID = 746000026269909002L;
    public static final long commandChannelID = 744874986073751584L;
    public static final long mapsChannelID = 744906782370955274L;
    public static final long schematicsChannelID = 744906867183976569L;
    public static final long moderationChannelID = 746007302900809768L;

    /*
     * id ролей
     */
    public static final long muteRoleID = 745156292989026334L;
    public static final long ownerRoleID = 744837465310887996L;
    public static final long adminRoleID = 746448599692345405L;

    public static final long messageDeleteTime = 20000; // 20 секунд
    public static final long guildID = 744814929701240882L; // id сервера

    public static Fi configFile = new Fi("prefs.json");

    public static ContentHandler contentHandler;
    public static Listener listener;
    public static Commands commands;
    public static Net net;
    public static Config config;
    public static Database data;

    public static void main(String[] args) throws InterruptedException, LoginException {
        init();

        String token;
        if(System.getProperty("token") != null){
            token = System.getProperty("token");
        }else{
            token = config.get("token");
        }

        listener.jda = JDABuilder.createDefault(token)
                .addEventListeners(listener)
                .build();
        listener.jda.awaitReady();

        listener.guild = listener.jda.getGuildById(guildID);

        Log.info("Discord bot up.");

        if(args.length > 0 && args[0].equalsIgnoreCase("-info")) listener.sendInfo();
    }

    public static void init(){
        config = new Config();
        contentHandler = new ContentHandler();
        listener = new Listener();
        commands = new Commands();
        net = new Net();
        data = new Database();
    }
}
