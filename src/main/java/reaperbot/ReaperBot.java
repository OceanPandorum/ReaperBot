package reaperbot;

import arc.Files;
import arc.files.Fi;
import arc.util.*;
import discord4j.common.util.Snowflake;
import discord4j.core.*;
import discord4j.discordjson.json.ApplicationInfoData;
import discord4j.gateway.intent.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class ReaperBot{
    public static final Snowflake
    serverChannelID = Snowflake.of(746000026269909002L),
    commandChannelID = Snowflake.of(744874986073751584L),
    mapsChannelID = Snowflake.of(744906782370955274L),
    schematicsChannelID = Snowflake.of(744906867183976569L);

    public static final Snowflake
    adminRoleId = Snowflake.of(747906993259282565L),
    memberRoleId = Snowflake.of(747908856604262469L);

    public static Snowflake ownerId;

    public static final Snowflake guildID = Snowflake.of(744814929701240882L);

    public static Fi configFile;
    public static Fi cacheDir;
    public static Fi schemDir;
    public static Fi mapDir;

    public static ContentHandler contentHandler;
    public static Listener listener;
    public static Commands commands;
    public static Net net;
    public static Config config;
    public static I18NBundle bundle;

    public static final String prefix = "$";

    public static ScheduledExecutorService service;

    public static void main(String[] args){
        init();

        GatewayDiscordClient client = DiscordClient.create(config.get("token"))
                     .gateway()
                     .setEnabledIntents(IntentSet.of(
                             Intent.GUILD_MESSAGES,
                             Intent.GUILD_MESSAGE_REACTIONS
                     ))
                     .login()
                     .block();

        Objects.requireNonNull(client, "???");

        client.on(listener).subscribe();

        listener.guild = client.getGuildById(guildID).block();

        ownerId = client.rest().getApplicationInfo()
                        .map(ApplicationInfoData::owner)
                        .map(o -> Snowflake.of(o.id()))
                        .block();

        if(args.length > 0 && args[0].equalsIgnoreCase("-info")){
            listener.sendInfo();
        }

        client.onDisconnect().block();
    }

    private static void init(){
        String[] tags = {"&lc&fb[D]", "&lg&fb[I]", "&ly&fb[W]", "&lr&fb[E]", ""};
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");
        Log.logger = (level, text) -> System.out.printf("[%s] [%s] %s%n",
                                                        dateTime.format(LocalDateTime.now()),
                                                        Log.format("&lc&fb@&fr", Thread.currentThread().getName()),
                                                        Log.format("@ @&fr", tags[level.ordinal()], text));

        configFile = new Fi("prefs.json");
        cacheDir = new Fi("cache/");
        schemDir = cacheDir.child("schem/");
        mapDir = cacheDir.child("map/");

        // Создаём схем и мод папку, кеш папка тоже создаться
        schemDir.mkdirs();
        mapDir.mkdirs();

        service = new ScheduledThreadPoolExecutor(2);
        config = new Config();
        bundle = I18NBundle.createBundle(new Fi("bundle", Files.FileType.classpath), Locale.ROOT, "Windows-1251");
        contentHandler = new ContentHandler();
        listener = new Listener();
        commands = new Commands();
        net = new Net();
    }
}
