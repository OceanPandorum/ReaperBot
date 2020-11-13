package reaperbot;

import arc.Core;
import arc.files.Fi;
import arc.func.Func;
import arc.struct.ObjectMap;
import arc.util.*;
import mindustry.net.Host;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.hjson.JsonValue;

import javax.annotation.Nonnull;
import java.awt.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Timer;
import java.util.*;
import java.util.concurrent.*;

import static arc.Files.FileType.classpath;
import static reaperbot.ReaperBot.*;

public class Listener extends ListenerAdapter{
    protected @Nullable TextChannel channel;
    protected @Nullable User lastUser;
    protected Guild guild;
    protected JDA jda;
    protected @Nullable Message lastMessage, lastSentMessage;

    public final Color normalColor = Color.decode("#b9fca6");
    public final Color errorColor = Color.decode("#ff3838");

    public final String[] swears = new Fi("great_russian_language.txt", classpath).readString("UTF-8")
                                                                                          .replaceAll("\n", "")
                                                                                          .split(", ");

    ObjectMap<Long, boolean[]> temp = new ObjectMap<>();
    long[] roleMessages = {760253624789762058L, 760253623602642954L}; // сообщения с правилами

    public Listener(){
        try{
            Core.net = new arc.Net();
            Func<String, String> replace = s -> s.replace("\\", "\\\\").replace("_", "\\_")
                                                 .replace("*", "\\*").replace("`", "\\`");

            service.schedule(() -> {
                List<Host> results = new CopyOnWriteArrayList<>();

                config.getArray("servers").forEach(server -> net.pingServer(server, results::add));

                net.run(Net.timeout, () -> {
                    results.sort((a, b) -> a.name != null && b.name == null ? 1
                    : a.name == null && b.name != null ? -1 : Integer.compare(a.players, b.players));

                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setColor(normalColor);

                    results.forEach(result -> {
                        if(result.name != null){
                            embed.addField(result.address, Strings.format("*@*\n@: @\n@: @\n@: @\n@: @\n@: @\n_\n_\n",
                                    replace.get(result.name),
                                    bundle.get("listener.players"),
                                    (result.playerLimit > 0 ? result.players + "/" + result.playerLimit : result.players),
                                    bundle.get("listener.map"),
                                    replace.get(result.mapname).replaceAll("\\[.*?\\]", ""),
                                    bundle.get("listener.wave"),
                                    result.wave,
                                    bundle.get("listener.version"),
                                    result.version,
                                    bundle.get("listener.mode"),
                                    Strings.capitalize(result.mode.name())), false);
                        }
                    });

                    embed.setFooter(time());

                    jda.getTextChannelById(serverChannelID).editMessageById(747117737268215882L, embed.build()).queue();
                });
            },60, TimeUnit.SECONDS);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    protected String time(){
        return bundle.format("listener.servers.last-update", DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss ZZZZ").format(ZonedDateTime.now()));
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
    public void onReady(@Nonnull ReadyEvent event){
        Log.info("Discord bot up.");
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event){
        if(event.getAuthor().isBot() || event.getChannel().getType() != ChannelType.TEXT) return;
        commands.handle(event);
    }

    @Override
    public void onMessageReactionAdd(@Nonnull MessageReactionAddEvent event){
        boolean[] b = temp.get(event.getUserIdLong(), () -> new boolean[2]);
        if(roleMessages[0] == event.getMessageIdLong()){
            b[0] = true;
        }else if(roleMessages[1] == event.getMessageIdLong()){
            b[1] = true;
        }
        temp.put(event.getUserIdLong(), b);
        accept(event.getUserIdLong());
    }

    protected String fullName(User user){
        String name = user.getName();
        Member member = listener.guild.retrieveMember(user).complete();
        if(member != null && member.getNickname() != null){
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
        }, messageDeleteTime);
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
