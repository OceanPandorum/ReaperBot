package reaper.event;

import arc.files.Fi;
import arc.struct.Seq;
import arc.util.*;
import discord4j.common.util.Snowflake;
import discord4j.core.*;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.message.*;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.Channel.Type;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.presence.Presence;
import discord4j.discordjson.json.*;
import discord4j.gateway.intent.*;
import discord4j.rest.response.ResponseFunction;
import discord4j.rest.util.Permission;
import discord4j.store.api.util.Lazy;
import io.netty.util.ResourceLeakDetector;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.*;
import reaper.*;
import reaper.service.MessageService;

import javax.annotation.*;
import java.util.*;

import static arc.Files.FileType.classpath;
import static reaper.Constants.*;

@Component
public class Listener extends ReactiveEventAdapter implements CommandLineRunner{
    private static final Logger log = Loggers.getLogger(Listener.class);

    public static final Map<Snowflake, boolean[]> validation = Collections.synchronizedMap(new WeakHashMap<>());

    @Autowired
    private MessageService bundle;

    @Autowired
    private reaper.event.command.CommandHandler handler;

    protected GatewayDiscordClient gateway;

    private Seq<Snowflake> roleMessages;
    private final Lazy<boolean[]> all = new Lazy<>(() -> {
        boolean[] booleans = new boolean[roleMessages.size];
        Arrays.fill(booleans, true);
        return booleans;
    });

    public static String[] swears;

    public Listener(){
        // из-за гиганских стак трейсов о утечке озу, которые я пока не понял как решать
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
    }

    @PostConstruct
    public void init(){
        contentHandler = new ContentHandler();
        lcontentHandler = new LContentHandler();

        roleMessages = Seq.with(config.listenedMessages);
        swears = new Fi("great_russian_language.txt", classpath)
                .readString("UTF-8")
                .toLowerCase()
                .split("\n");

        gateway = DiscordClientBuilder
                .create(Objects.requireNonNull(config.token, "token"))
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

        reactionListener = new ReactionListener();

        gateway.on(this).subscribe();
        gateway.on(reactionListener).subscribe();

        gateway.updatePresence(Presence.idle(ActivityUpdateRequest.builder().type(0).name(bundle.get("discord.status")).build())).block();

        ownerId = gateway.rest().getApplicationInfo()
                         .map(ApplicationInfoData::owner)
                         .map(o -> Snowflake.of(o.id()))
                         .block();
    }

    @PreDestroy
    public void destroy(){
        gateway.logout().block();
    }

    @Override
    public Publisher<?> onMessageCreate(MessageCreateEvent event){
        return Mono.just(event.getMessage())
                .filter(message -> !message.getAuthor().map(User::isBot).orElse(true))
                .filterWhen(message -> message.getChannel().map(c -> c.getType() == Type.GUILD_TEXT))
                .flatMap(__ -> handle(event));
    }

    @Override
    public Publisher<?> onMessageUpdate(MessageUpdateEvent event){
        return event.getMessage()
                .filter(m -> event.isContentChanged())
                .filter(m -> !m.getAuthor().map(User::isBot).orElse(false))
                .flatMap(message -> message.getAuthorAsMember().flatMap(member -> {
                    String text = event.getCurrentContent().map(String::toLowerCase).orElse("");

                    return isAdmin(member).flatMap(b -> !b && Structs.contains(swears, s -> Structs.contains(text.split("\\s+"), t -> t.matches(s))) ? message.delete() : Mono.empty());
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

        Mono<Void> swear = isAdmin(member).flatMap(b -> !b && Structs.contains(swears, s -> Structs.contains(text.split("\\w+"), t -> t.matches(s))) ? message.delete() : Mono.empty());

        return message.getChannel().flatMap(channel -> {
            Mono<Void> botChannel = isAdmin(member).flatMap(b -> !config.commandChannelId.equals(channel.getId()) && !b ? Mono.empty() : handler.handle(event));

            return Mono.when(swear, botChannel);
        });
    }

    public Mono<Boolean> isAdmin(Member member){
        return member.getRoles().any(r -> config.adminRoleId.equals(r.getId()) || r.getPermissions().contains(Permission.ADMINISTRATOR))
                .map(b -> b || ownerId.equals(member.getId()));
    }

    @Override
    public void run(String... args) throws Exception{
        if(args.length > 0 && args[0].equals("--info")){
            int index = Strings.parseInt(args[1]);
            try{
                roleMessages = Seq.with(config.listenedMessages);
                InfoEmbed i = config.info.get(index - 1);
                if(i == null){
                    log.error("Info embed with index '{}' not found", index);
                    return;
                }

                gateway.getChannelById(i.channelId)
                        .cast(TextChannel.class)
                        .flatMap(c -> c.createEmbed(e -> e.setColor(MessageService.normalColor)
                                .setTitle(i.title).setDescription(i.description)))
                        .map(Message::getId)
                        .doOnNext(signal -> {
                            if(i.listenable){
                                config.listenedMessages.add(signal);
                            }
                        })
                        .block();
            }catch(Throwable t){
                throw new RuntimeException(t);
            }
        }
    }
}
