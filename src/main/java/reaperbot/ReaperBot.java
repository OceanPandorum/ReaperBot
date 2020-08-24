package reaperbot;

import arc.files.Fi;
import arc.util.Log;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;

public class ReaperBot {
    /*
     * id каналов
     */
    public static final long serverChannelID = 746000026269909002L; // наши-сервера
    public static final long commandChannelID = 744874986073751584L; // using-bots--использование-ботов
    public static final long mapsChannelID = 744906782370955274L; // maps--карты
    public static final long schematicsChannelID = 744906867183976569L; // schematics--схемы
    public static final long moderationChannelID = 746007302900809768L; // логи

    /*
     * id ролей
     */
    public static final long muteRoleID = 745156292989026334L;
    public static final long ownerRoleID = 744837465310887996L;
    public static final long adminRoleID = 746448599692345405L;

    public static final long messageDeleteTime = 20000; // 20 секунд
    public static final long guildID = 741011186916524213L; // id бота
    public static final Fi prefsFile = new Fi("prefs.json");

    public static JDA jda;

    public static ContentHandler contentHandler;
    public static Messages messages;
    public static Commands commands;
    public static Net net;
    public static Config config;
    public static Database data;

    public static void main(String[] args) throws InterruptedException, LoginException {
        init();
        data.connect();
        data.init();

        String token;
        if(System.getProperty("token") != null){
            token = System.getProperty("token");
        }else{
            token = config.get("token");
        }

        jda = JDABuilder.createDefault(token)
                .addEventListeners(messages)
                .build();
        jda.awaitReady();

        messages.guild = jda.getGuildById(guildID);

        Log.info("Discord bot up.");

        new MuteDaemon();
    }

    public static void init(){
        contentHandler = new ContentHandler();
        messages = new Messages();
        commands = new Commands();
        net = new Net();
        config = new Config(prefsFile);
        data = new Database();
    }
}
