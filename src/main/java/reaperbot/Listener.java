package reaperbot;

import arc.Core;
import arc.files.Fi;
import arc.func.Func;
import arc.struct.*;
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
    private boolean[] all;

    protected Guild guild;
    protected JDA jda;
    protected @Nullable TextChannel channel;
    protected @Nullable User lastUser;
    protected @Nullable Member lastMember;
    protected @Nullable Message lastMessage, lastSentMessage;

    public final Color normalColor = Color.decode("#b9fca6"), errorColor = Color.decode("#ff3838");

    public final String[] swears = new Fi("great_russian_language.txt", classpath).readString("UTF-8")
                                                                                          .replaceAll("\n", "")
                                                                                          .split(", ");

    ObjectMap<Long, boolean[]> temp = new ObjectMap<>();
    Seq<Long> roleMessages = new Seq<>();

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

                    embed.setFooter(bundle.format("listener.servers.last-update", time()));

                    jda.getTextChannelById(serverChannelID).editMessageById(747117737268215882L, embed.build()).queue();
                });
            },60, TimeUnit.SECONDS);

            Log.info("Common listener loaded.");
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    @Nonnull
    protected String time(){
        return DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss ZZZZ").format(ZonedDateTime.now());
    }

    public void sendInfo(){
        try{
            for(JsonValue v : config.getJArray("info")){
                if(!v.asObject().getBoolean("ignore", true)){
                    String title = v.asObject().get("title").asString();
                    String description = v.asObject().get("description").asString();
                    String channelId = v.asObject().get("channel-id").asString();
                    boolean listen = v.asObject().getBoolean("listen", false);

                    MessageEmbed e = new EmbedBuilder().setTitle(title).setDescription(description).setColor(normalColor).build();

                    Message m = guild.getTextChannelById(channelId).sendMessage(e).complete();
                    if(listen){
                        roleMessages.add(m.getIdLong());
                    }
                }
            }
            all = new boolean[roleMessages.size];
            Arrays.fill(all, true);
        }catch(Throwable t){
            throw new RuntimeException(t);
        }
        Log.info("All embeds sent.");
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
        if(!roleMessages.isEmpty()){
            boolean[] b = temp.get(event.getUserIdLong(), () -> new boolean[roleMessages.size]);
            for(int i = 0; i < roleMessages.size; i++){
                if(roleMessages.get(i) == event.getMessageIdLong()){
                    b[i] = true;
                }
            }
            long userId = event.getUserIdLong();
            temp.put(userId, b);
            if(Arrays.equals(b, all)){
                guild.addRoleToMember(userId, guild.getRoleById(memberRoleId)).queue();
                temp.remove(userId);
            }
        }
    }

    @Nonnull
    protected String fullName(User user){
        String name = user.getName();
        Member member = listener.guild.retrieveMember(user).complete();
        if(member != null && member.getNickname() != null){
            name += " / " + member.getNickname();
        }
        return name;
    }

    public void deleteMessages(){
        Message last = lastMessage, lastSent = lastSentMessage;

        new Timer().schedule(new TimerTask(){
            @Override
            public void run(){
                if(last != null && lastMessage != null){
                    last.delete().queue();
                    lastSent.delete().queue();
                }
            }
        }, messageDeleteTime);
    }

    public void embed(MessageEmbed embed){
        lastSentMessage = channel.sendMessage(embed).complete();
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
