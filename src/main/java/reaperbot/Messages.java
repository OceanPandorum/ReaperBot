package reaperbot;

import arc.Core;
import arc.util.Log;
import arc.util.Strings;
import mindustry.net.Host;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.hjson.JsonValue;

import javax.annotation.Nonnull;
import java.awt.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;

import static reaperbot.ReaperBot.*;

public class Messages extends ListenerAdapter{
    TextChannel channel;
    User lastUser;
    Guild guild;
    Message lastMessage;
    Message lastSentMessage;
    Color normalColor = Color.decode("#b9fca6");
    Color errorColor = Color.decode("#ff3838");

    public Messages(){
        try{
            Core.net = new arc.Net();

            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                List<Host> results = new CopyOnWriteArrayList<>();

                for(String server : config.getArray("servers")){
                    net.pingServer(server, results::add);
                }

                net.run(Net.timeout, () -> {
                    results.sort((a, b) -> a.name != null && b.name == null ? 1 : a.name == null && b.name != null ? -1 : Integer.compare(a.players, b.players));

                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setColor(normalColor);

                    for(Host result : results){
                        if(result.name != null){
                            embed.addField(result.address,
                            Strings.format("*{0}*\nPlayers: {1}\nMap: {2}\nWave: {3}\nVersion: {4}\nMode: {5}\n_\n_\n",
                                    result.name.replace("\\", "\\\\").replace("_", "\\_").replace("*", "\\*").replace("`", "\\`"),
                                    (result.playerLimit > 0 ? result.players + "/" + result.playerLimit : result.players),
                                    result.mapname.replace("\\", "\\\\").replace("_", "\\_").replace("*", "\\*").replace("`", "\\`").replaceAll("\\[.*?\\]", ""),
                                    result.wave,
                                    result.version,
                                    Strings.capitalize(result.mode.name())), false);
                        }
                    }

                    embed.setFooter(Strings.format("Last Updated: {0}", DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss ZZZZ").format(ZonedDateTime.now())));

                    jda.getTextChannelById(serverChannelID).editMessageById(747117737268215882L, embed.build()).queue();
                });
            }, 10, 60, TimeUnit.SECONDS);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public void sendInfo(){
        for(JsonValue v : config.getJArray("info")){
            String title = v.asObject().get("title").asString();
            String description = v.asObject().get("description").asString();
            String channelId = v.asObject().get("channel-id").asString();

            MessageEmbed e = new EmbedBuilder()
                    .addField(title, description, true).setColor(normalColor).build();

            guild.getTextChannelById(channelId).sendMessage(e).queue();
        }
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        try{
            if(!event.getAuthor().isBot()) commands.handle(event);
        }catch(Exception e){
            Log.err(e);
        }
    }

    @Override
    public void onMessageReactionAdd(@Nonnull MessageReactionAddEvent event) {
        try {
            if(event.getChannel().getIdLong() == 747012468576092200L && event.getMessageIdLong() == 747174743320559626L){
                if (event.getReactionEmote().getName().equals("\uD83C\uDDF7\uD83C\uDDFA")){
                    event.getGuild().addRoleToMember(event.getUserIdLong(), event.getGuild().getRolesByName("ru", true).get(0)).queue();
                }else if (event.getReactionEmote().getName().equals("\uD83C\uDDFA\uD83C\uDDF8")){
                    event.getGuild().addRoleToMember(event.getUserIdLong(), event.getGuild().getRolesByName("en", true).get(0)).queue();
                }
            }
        } catch (Exception e){
            Log.err(e);
        }
    }

    public void deleteMessages(){
        Message last = lastMessage, lastSent = lastSentMessage;

        new Timer().schedule(new TimerTask(){
            @Override
            public void run(){
                last.delete().queue();
                lastSent.delete().queue();
            }
        }, ReaperBot.messageDeleteTime);
    }

    public void deleteMessage(){
        Message last = lastSentMessage;

        new Timer().schedule(new TimerTask(){
            @Override
            public void run(){
                last.delete().queue();
            }
        }, ReaperBot.messageDeleteTime);
    }

    public void text(String text, Object... args){
        lastSentMessage = channel.sendMessage(Strings.format(text, args)).complete();
    }

    public void info(String title, String text, Object... args){
        MessageEmbed object = new EmbedBuilder()
        .addField(title, Strings.format(text, args), true).setColor(normalColor).build();

        lastSentMessage = channel.sendMessage(object).complete();
    }

    public void err(String text, Object... args){
        err("Error", text, args);
    }

    public void err(String title, String text, Object... args){
        MessageEmbed e = new EmbedBuilder()
        .addField(title, Strings.format(text, args), true).setColor(errorColor).build();
        lastSentMessage = channel.sendMessage(e).complete();
    }
}
