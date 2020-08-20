package reaperbot;

import arc.Core;
import arc.Net.HttpStatus;
import arc.struct.Array;
import arc.util.*;
import mindustry.mod.ModListing;
import mindustry.net.Host;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Timer;
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

                for(String server : prefs.getArray("servers")){
                    net.pingServer(server, results::add);
                }

                net.run(Net.timeout, () -> {
                    results.sort((a, b) -> a.name != null && b.name == null ? 1 : a.name == null && b.name != null ? -1 : Integer.compare(a.players, b.players));

                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setColor(normalColor);

                    for(Host result : results){
                        if(result.name != null && result.players > 0){
                            embed.addField(result.address,
                            Strings.format("*{0}*\nPlayers: {1}\nMap: {2}\nWave: {3}\nVersion: {4}\nMode: {5}\nPing: {6}ms\n_\n_\n",
                            result.name.replace("\\", "\\\\").replace("_", "\\_").replace("*", "\\*").replace("`", "\\`") + (result.description != null && result.description.length() > 0 ? "\n" + result.description : ""),
                            (result.playerLimit > 0 ? result.players + "/" + result.playerLimit : result.players),
                            result.mapname.replace("\\", "\\\\").replace("_", "\\_").replace("*", "\\*").replace("`", "\\`").replaceAll("\\[.*?\\]", ""),
                            result.wave,
                            result.version,
                            Strings.capitalize(result.mode.name()),
                            result.ping), false);
                        }
                    }

                    embed.setFooter(Strings.format("Last Updated: {0}", DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss ZZZZ").format(ZonedDateTime.now())));

                    guild.getTextChannelById(serverChannelID).editMessageById(746013318623396020L, embed.build()).queue();
                });
            }, 10, 60, TimeUnit.SECONDS);

            StringBuilder messageBuilder = new StringBuilder();

            server.connect(input -> {
                if(messageBuilder.length() > 1000){
                    String text = messageBuilder.toString();
                    messageBuilder.setLength(0);
                    guild.getTextChannelById(commandChannelID).sendMessage(text).queue();
                }else if(messageBuilder.length() == 0){
                    messageBuilder.append(input);
                    new Timer().schedule(new TimerTask(){
                        @Override
                        public void run(){
                            if(messageBuilder.length() == 0) return;
                            String text = messageBuilder.toString();
                            messageBuilder.setLength(0);
                            guild.getTextChannelById(commandChannelID).sendMessage(text).queue();
                        }
                    }, 60L);
                }else{
                    messageBuilder.append("\n").append(input);
                }
            });

            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                Core.net.httpGet("https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json", response -> {
                    if(response.getStatus() != HttpStatus.OK){
                        return;
                    }

                    Array<ModListing> listings = new Array<>();//json.fromJson(Array.class, ModListing.class, response.getResultAsString());
                    listings.sort(Structs.comparing(list -> Date.from(Instant.parse(list.lastUpdated))));
                    listings.reverse();
                    listings.truncate(20);
                    listings.reverse();

                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setColor(normalColor);
                    embed.setTitle("Last Updated Mods");
                    embed.setFooter(Strings.format("Last Updated: {0}", DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss ZZZZ").format(ZonedDateTime.now())));
                    for(ModListing listing : listings){
                        embed.addField(listing.repo + "  " + listing.stars + " | "
                            + "*Updated " + durFormat(Duration.between(Instant.parse(listing.lastUpdated), Instant.now()))+ " ago*",
                        Strings.format("**[{0}]({1})**\n{2}\n\n_\n_",
                            Strings.stripColors(listing.name),
                            "https://github.com/" + listing.repo,
                            Strings.stripColors(listing.description)), false);
                    }

                    //guild.getTextChannelById(modChannelID).editMessageById(663246057660219413L, embed.build()).queue();
                }, Log::err);
            }, 0, 20, TimeUnit.MINUTES);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    private static String durFormat(Duration duration){
        if(duration.toDays() > 0) return duration.toDays() + "d";
        if(duration.toHours() > 0) return duration.toHours() + "h";
        return duration.toMinutes() + "m";
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event){
        try{
            commands.handle(event.getMessage());
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event){
        try {
            commands.edited(event.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event){
        event.getUser().openPrivateChannel().complete().sendMessage(
        "**Welcome to the Mindustry Discord.**" +
        "\n\n*Make sure you read #rules and the channel topics before posting.*\n\n" +
        "**For a list of public servers**, see the #servers channel.\n" +
        "**Make sure you check out the #faq channel here:**\n<https://discordapp.com/channels/391020510269669376/611204372592066570/611586644402765828>"
        ).queue();
    }

    public void sendUpdate(Net.VersionInfo info){
        String text = info.description;
        int maxLength = 2000;
        while(true){
            String current = text.substring(0, Math.min(maxLength, text.length()));
            guild
            .getTextChannelById(announcementsChannelID)
            .sendMessage(new EmbedBuilder()
            .setColor(normalColor).setTitle(info.name)
            .setDescription(current).build()).queue();

            if(text.length() < maxLength){
                return;
            }

            text = text.substring(maxLength);
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

    public void logTo(String text, Object... args){
        jda.getTextChannelById(logChannelID).sendMessage(format(text, args)).queue();
    }

    public void text(String text, Object... args){
        lastSentMessage = channel.sendMessage(format(text, args)).complete();
    }

    public void info(String title, String text, Object... args){
        MessageEmbed object = new EmbedBuilder()
        .addField(title, format(text, args), true).setColor(normalColor).build();

        lastSentMessage = channel.sendMessage(object).complete();
    }

    public void err(String text, Object... args){
        err("Error", text, args);
    }

    public void err(String title, String text, Object... args){
        MessageEmbed e = new EmbedBuilder()
        .addField(title, format(text, args), true).setColor(errorColor).build();
        lastSentMessage = channel.sendMessage(e).complete();
    }

    private String format(String text, Object... args){
        for(int i = 0; i < args.length; i++){
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }

        return text;
    }
}
