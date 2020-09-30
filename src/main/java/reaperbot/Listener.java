package reaperbot;

import arc.Core;
import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.Strings;
import mindustry.net.Host;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
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

public class Listener extends ListenerAdapter{
    TextChannel channel;
    User lastUser;
    Guild guild;
    JDA jda;
    Message lastMessage;
    Message lastSentMessage;
    Color normalColor = Color.decode("#b9fca6");
    Color errorColor = Color.decode("#ff3838");

    ObjectMap<Long, boolean[]> temp = new ObjectMap<>();
    long[] roleMessages = {752178728586707094L, 752178728963932270L};

    public Listener(){
        try{
            Core.net = new arc.Net();

            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                List<Host> results = new CopyOnWriteArrayList<>();

                for(String server : config.getArray("servers")){
                    net.pingServer(server, results::add);
                }

                net.run(Net.timeout, () -> {
                    results.sort((a, b) -> a.name != null && b.name == null
                            ? 1
                            : a.name == null && b.name != null
                            ? -1
                            : Integer.compare(a.players, b.players));

                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setColor(normalColor);

                    for(Host result : results){
                        if(result.name != null){
                            embed.addField(result.address,
                                    Strings.format("*{0}*\n{1}: {2}\n{3}: {4}\n{5}: {6}\n{7}: {8}\n{9}: {10}\n_\n_\n",
                                            result.name.replace("\\", "\\\\").replace("_", "\\_")
                                                       .replace("*", "\\*").replace("`", "\\`"),
                                            bundle.get("listener.players"),
                                            (result.playerLimit > 0 ? result.players + "/" + result.playerLimit : result.players),
                                            bundle.get("listener.map"),
                                            result.mapname.replace("\\", "\\\\").replace("_", "\\_")
                                                          .replace("*", "\\*").replace("`", "\\`")
                                                          .replaceAll("\\[.*?\\]", ""),
                                            bundle.get("listener.wave"),
                                            result.wave,
                                            bundle.get("listener.version"),
                                            result.version,
                                            bundle.get("listener.mode"),
                                            Strings.capitalize(result.mode.name())), false);
                        }
                    }

                    embed.setFooter(bundle.format("listener.servers.last-update", DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss ZZZZ").format(ZonedDateTime.now())));

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

            MessageEmbed e = new EmbedBuilder().setTitle(title).setDescription(description).setColor(normalColor).build();

            guild.getTextChannelById(channelId).sendMessage(e).queue();
        }
        Log.info("All embeds sended.");
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event){
        try{
            if(!event.getAuthor().isBot()) commands.handle(event);
        }catch(Exception e){
            Log.err(e);
        }
    }

    @Override
    public void onMessageReactionAdd(@Nonnull MessageReactionAddEvent event){
        try{
            boolean[] b = !temp.containsKey(event.getUserIdLong()) ? new boolean[2] : temp.get(event.getUserIdLong());
            if(roleMessages[0] == event.getMessageIdLong())
                b[0] = true;
            else if(roleMessages[1] == event.getMessageIdLong())
                b[1] = true;
            temp.put(event.getUserIdLong(), b);
            accept(event.getUserIdLong());
        }catch(Exception e){
            Log.err(e);
        }
    }

    public String correctName(User user){
        String name = user.getName();
        Member member = listener.guild.retrieveMember(user).complete();
        if(member.getNickname() != null){
            name += " / " + member.getNickname();
        }
        return name;
    }

    private void accept(long id){
        if(temp.get(id)[0] && temp.get(id)[1]){
            guild.addRoleToMember(id, guild.getRoleById(747908856604262469L)).queue();
            temp.remove(id);
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

    public void text(String text, Object... args){
        lastSentMessage = channel.sendMessage(Strings.format(text, args)).complete();
    }

    public void info(String title, String text, Object... args){
        MessageEmbed object = new EmbedBuilder()
                .setTitle(title).setDescription(Strings.format(text, args)).setColor(normalColor).build();

        lastSentMessage = channel.sendMessage(object).complete();
    }

    public void err(String text, Object... args){
        err(bundle.get("listener.error"), text, args);
    }

    public void err(String title, String text, Object... args){
        MessageEmbed e = new EmbedBuilder()
                .setTitle(title).setDescription(Strings.format(text, args)).setColor(errorColor).build();
        lastSentMessage = channel.sendMessage(e).complete();
    }
}
