package reaperbot;

import arc.files.Fi;
import arc.struct.Seq;
import arc.util.*;
import discord4j.common.util.Snowflake;
import discord4j.core.*;
import discord4j.discordjson.json.ApplicationInfoData;
import discord4j.gateway.intent.*;
import discord4j.rest.response.ResponseFunction;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.scheduling.annotation.*;

import java.util.*;

import static arc.Files.FileType.classpath;
import static reaperbot.Constants.*;

@EnableScheduling
@EnableAsync
@SpringBootApplication
public class ReaperBot{

    @Bean
    public MessageSource messageSource(){
        ResourceBundleMessageSource bundle = new ResourceBundleMessageSource();
        bundle.setBasename("bundle");
        bundle.setDefaultEncoding("utf-8");
        return bundle;
    }

    public static void main(String[] args){
        init();
        SpringApplication.run(ReaperBot.class, args);


        if(true)
            return;
        GatewayDiscordClient client = DiscordClientBuilder
                .create(Objects.requireNonNull(config.token, "Token must be not null!"))
                .onClientResponse(ResponseFunction.emptyIfNotFound())
                .build()
                .gateway()
                .setEnabledIntents(IntentSet.of(
                        Intent.GUILD_MESSAGES,
                        Intent.GUILD_MESSAGE_REACTIONS
                ))
                .login()
                .blockOptional()
                .orElseThrow(RuntimeException::new);

        client.on(listener).subscribe();

        listener.guild = client.getGuildById(config.guildId).block();

        ownerId = client.rest().getApplicationInfo()
                .map(ApplicationInfoData::owner)
                .map(o -> Snowflake.of(o.id()))
                .block();

        Seq<String> arr = Seq.with(args);
        if(arr.any() && arr.get(0).equals("-info")){
            listener.sendInfo(Strings.parseInt(arr.get(1)));
        }

        client.onDisconnect().block();
    }

    private static void init(){
        configFile = Fi.get("prefs.json");
        if(!configFile.exists()){
            config = new Config();
            configFile.writeString(gson.toJson(config));
        }
        config = gson.fromJson(configFile.reader(), Config.class);
        if(true)
            return;

        cacheDir = new Fi("cache/");
        schemeDir = cacheDir.child("schem/");
        mapDir = cacheDir.child("map/");

        // Создаём схем и мод папку, кеш папка тоже создаться
        schemeDir.mkdirs();
        mapDir.mkdirs();

        contentHandler = new ContentHandler();
        listener = new Listener();
        commands = new Commands();
        net = new Net();
    }
}
