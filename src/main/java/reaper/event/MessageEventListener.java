package reaper.event;

import arc.files.Fi;
import arc.struct.Seq;
import arc.util.*;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.message.*;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.Channel.Type;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.rest.util.Permission;
import discord4j.store.api.util.Lazy;
import io.netty.util.ResourceLeakDetector;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;
import reactor.util.*;
import reaper.*;
import reaper.event.command.CommandHandler;
import reaper.service.MessageService;

import javax.annotation.PostConstruct;
import java.util.*;

import static arc.Files.FileType.classpath;
import static reaper.Constants.*;

@Component
public class MessageEventListener extends ReactiveEventAdapter implements CommandLineRunner{
    private static final Logger log = Loggers.getLogger(MessageEventListener.class);

    public static final Map<Snowflake, boolean[]> validation = Collections.synchronizedMap(new WeakHashMap<>());

    @Autowired
    private CommandHandler handler;

    protected GatewayDiscordClient gateway;

    private Seq<Snowflake> roleMessages;
    private final Lazy<boolean[]> all = new Lazy<>(() -> {
        boolean[] booleans = new boolean[roleMessages.size];
        Arrays.fill(booleans, true);
        return booleans;
    });

    public static final String[] swears;

    static{
        swears = new Fi("great_russian_language.regexp", classpath)
                .readString("UTF-8")
                .toLowerCase()
                .split("\n");
    }

    public MessageEventListener(){
        // из-за гиганских стак трейсов о утечке озу, которые я пока не понял как решать
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
    }

    @PostConstruct
    public void init(){
        contentHandler = new ContentHandler();

        roleMessages = Seq.with(config.listenedMessages);
    }

    @Override
    public Publisher<?> onMessageCreate(MessageCreateEvent event){
        return Mono.just(event.getMessage())
                .filter(message -> !message.getAuthor().map(User::isBot).orElse(true))
                .filterWhen(message -> message.getChannel().map(channel -> channel.getType() == Type.GUILD_TEXT))
                .flatMap(__ -> handle(event));
    }

    @Override
    public Publisher<?> onMessageUpdate(MessageUpdateEvent event){
        return event.getMessage().filter(message -> event.isContentChanged() && !message.getAuthor().map(User::isBot).orElse(false))
                .flatMap(message -> message.getAuthorAsMember().flatMap(member -> {
                    String text = event.getCurrentContent().map(String::toLowerCase).orElse("");

                    return isAdmin(member).flatMap(bool -> !bool && Structs.contains(swears, s -> Structs.contains(text.split("\\s+"), t -> t.matches(s))) ? message.delete() : Mono.empty());
                }));
    }

    @Override
    public Publisher<?> onReactionAdd(ReactionAddEvent event){
        Member member = event.getMember().orElse(null);
        if(roleMessages.any() && member != null){
            boolean[] b = validation.computeIfAbsent(event.getUserId(), k -> new boolean[roleMessages.size]);
            for(int i = 0; i < roleMessages.size; i++){
                if(roleMessages.get(i).equals(event.getMessageId())){
                    b[i] = true;
                }
            }

            Snowflake userId = event.getUserId();
            if(Arrays.equals(b, all.get())){
                return member.addRole(config.memberRoleId).then(Mono.fromRunnable(() -> validation.remove(userId)));
            }
        }

        return Mono.empty();
    }

    public Mono<Void> handle(MessageCreateEvent event){
        Message message = event.getMessage();
        Member member = event.getMember().orElseThrow(RuntimeException::new);
        String text = message.getContent().toLowerCase();

        Mono<Void> swear = isAdmin(member).flatMap(bool -> !bool && Structs.contains(swears, s -> Structs.contains(text.split("\\s+"), t -> t.matches(s))) ? message.delete() : Mono.empty());

        return message.getChannel().flatMap(channel -> {
            Mono<Void> botChannel = isAdmin(member).flatMap(bool -> !config.commandChannelId.equals(channel.getId()) && !bool ? Mono.empty() : handler.handle(event));

            return Mono.when(swear, botChannel);
        });
    }

    public Mono<Boolean> isAdmin(Member member){
        return member.getRoles().any(role -> config.adminRoleId.equals(role.getId()) || role.getPermissions().contains(Permission.ADMINISTRATOR))
                .zipWith(ownerId)
                .map(TupleUtils.function((bool, ownerId) -> bool || ownerId.equals(member.getId())));
    }

    @Override
    public void run(String... args) throws Exception{
        if(args.length > 0 && args[0].equals("--info")){
            int index = Strings.parseInt(args[1]);
            try{
                InfoEmbed infoEmbed = config.info.get(index - 1);
                if(infoEmbed == null){
                    log.error("Info embed with index '{}' not found", index);
                    return;
                }

                gateway.getChannelById(infoEmbed.channelId)
                        .ofType(TextChannel.class)
                        .flatMap(channel -> channel.createEmbed(spec -> spec.setColor(MessageService.normalColor)
                                .setTitle(infoEmbed.title).setDescription(infoEmbed.description)))
                        .map(Message::getId)
                        .doOnNext(signal -> {
                            if(infoEmbed.listenable){
                                config.listenedMessages.add(signal);
                                config.update();
                            }
                        })
                        .block();
            }catch(Throwable t){
                throw new RuntimeException(t);
            }
        }
    }
}
