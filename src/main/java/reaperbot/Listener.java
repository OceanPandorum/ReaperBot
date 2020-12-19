package reaperbot;

import arc.Core;
import arc.files.Fi;
import arc.func.Func;
import arc.struct.*;
import arc.util.*;
import discord4j.common.util.Snowflake;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.message.*;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.*;
import discord4j.core.object.entity.channel.Channel.Type;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import mindustry.net.Host;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reaperbot.service.MessageService;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static arc.Files.FileType.classpath;
import static reaperbot.Constants.*;

@Component
public class Listener extends ReactiveEventAdapter{
    private boolean[] all;

    protected Guild guild;
    protected TextChannel channel;
    protected Member lastMember;
    protected Message lastMessage, lastSentMessage;

    public final Color normalColor = Color.of(0xb9fca6), errorColor = Color.of(0xff3838);

    public String[] swears;

    ObjectMap<Snowflake, boolean[]> temp = new ObjectMap<>();
    Seq<Snowflake> roleMessages;

    @Autowired
    private MessageService bundle;

    public Listener(){
        Core.net = new arc.Net();
        roleMessages = Seq.with(config.listenedMessages);
        lateInitialize();
    }

    @Scheduled(cron = "*/30 * * * * *") // каждые 30 секунд
    public void serviceStatus(){
        Func<String, String> replace = s -> s.replace("\\", "\\\\").replace("_", "\\_")
                                             .replace("*", "\\*").replace("`", "\\`");
        List<Host> results = new CopyOnWriteArrayList<>();

        config.servers.forEach(server -> net.pingServer(server, results::add));

        Schedulers.boundedElastic().schedule(() -> {
            results.sort((a, b) -> a.name != null && b.name == null
            ? 1 : a.name == null && b.name != null ? -1 : Integer.compare(a.players, b.players));

            Consumer<EmbedCreateSpec> embed = e -> {
                e.setColor(normalColor);
                results.stream().filter(h -> h.name != null).forEach(result -> {
                    e.addField(result.address, Strings.format("*@*\n@: @\n@: @\n@: @\n@: @\n@: @\n_\n_\n",
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
                        Strings.capitalize(result.mode.name())
                    ), false);
                });
                e.setFooter(bundle.format("listener.servers.last-update",
                                          DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss ZZZZ").format(ZonedDateTime.now())), null);
            };

            guild.getChannelById(config.serverChannelId)
                 .cast(TextChannel.class)
                 .flatMap(c -> c.getMessageById(config.serverMessageId).flatMap(m -> m.edit(e -> e.setEmbed(embed))))
                 .block();
        }, 2, TimeUnit.SECONDS);
    }

    //todo????
    protected void lateInitialize(){
        all = new boolean[roleMessages.size];
        Arrays.fill(all, true);
        swears = new Fi("great_russian_language.txt", classpath)
                .readString("UTF-8")
                .replaceAll("\n", "")
                .split(", ");
        config.update();
    }

    public void sendInfo(int index){
        try{
            roleMessages = Seq.with(config.listenedMessages);
            InfoEmbed i = config.info.get(index + 1);
            if(i == null){
                Log.err("Info embed with index '@' not found", index);
                return;
            }

            Snowflake messageId = guild.getChannelById(i.channelId)
                    .cast(TextChannel.class)
                    .flatMap(c -> c.createEmbed(e -> e.setColor(normalColor).setTitle(i.title).setDescription(i.description)))
                    .map(Message::getId)
                    .block();

            if(i.listenable && messageId != null){
                config.listenedMessages.add(messageId);
            }
            lateInitialize();
        }catch(Throwable t){
            throw new RuntimeException(t);
        }
    }

    @Override
    public Publisher<?> onMessageCreate(MessageCreateEvent event){
        Message message = event.getMessage();
        return message.getChannel().map(Channel::getType)
                      .filter(t -> t == Type.GUILD_TEXT && !message.getAuthor().map(User::isBot).orElse(true))
                      .flatMap(m -> commands.handle(event));
    }

    @Override
    public Publisher<?> onReactionAdd(ReactionAddEvent event){
        Member member = event.getMember().orElse(null);
        if(!roleMessages.isEmpty() && member != null){
            boolean[] b = temp.get(event.getUserId(), () -> new boolean[roleMessages.size]);
            for(int i = 0; i < roleMessages.size; i++){
                if(roleMessages.get(i).equals(event.getMessageId())){
                    b[i] = true;
                }
            }
            Snowflake userId = event.getUserId();
            temp.put(userId, b);
            if(Arrays.equals(b, all)){
                return member.addRole(config.memberRoleId).then(Mono.fromRunnable(() -> temp.remove(userId)));
            }
        }

        return Mono.empty();
    }

    public void deleteMessages(){
        Message last = lastMessage, lastSent = lastSentMessage;

        Schedulers.boundedElastic().schedule(() -> {
            if(last != null && lastMessage != null){
                last.delete().block();
                lastSent.delete().block();
            }
        }, 20, TimeUnit.SECONDS);
    }

    public void embed(Consumer<EmbedCreateSpec> embed){
        lastSentMessage = channel.createEmbed(embed).block();
    }

    public void text(String text, Object... args){
        lastSentMessage = channel.createMessage(Strings.format(text, args)).block();
    }

    public void info(String title, String text, Object... args){
        lastSentMessage = channel.createEmbed(e -> e.setTitle(title).setDescription(Strings.format(text, args)).setColor(normalColor)).block();
    }

    public void err(String text, Object... args){
        err(bundle.get("listener.error"), text, args);
    }

    public void err(String title, String text, Object... args){
        lastSentMessage = channel.createEmbed(e -> e.setTitle(title).setDescription(Strings.format(text, args)).setColor(errorColor)).block();
    }
}
