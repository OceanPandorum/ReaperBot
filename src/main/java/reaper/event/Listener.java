package reaper.event;

import arc.Core;
import arc.files.Fi;
import arc.struct.*;
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
import io.netty.util.ResourceLeakDetector;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reaper.*;
import reaper.service.MessageService;

import javax.annotation.*;
import java.util.*;
import java.util.function.Supplier;

import static arc.Files.FileType.classpath;
import static reaper.Constants.*;

@Component
public class Listener extends ReactiveEventAdapter implements CommandLineRunner{
    public static final ObjectMap<Snowflake, boolean[]> validation = new ObjectMap<>();

    @Autowired
    private MessageService bundle;

    @Autowired
    private reaper.command.CommandHandler handler;

    protected GatewayDiscordClient gateway;

    private Seq<Snowflake> roleMessages;
    private final Supplier<boolean[]> all = () -> {
        boolean[] booleans = new boolean[roleMessages.size];
        Arrays.fill(booleans, true);
        return booleans;
    };

    public static String[] swears;

    public Listener(){
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
    }

    @PostConstruct
    public void init(){
        contentHandler = new ContentHandler();

        Core.net = new arc.Net();

        roleMessages = Seq.with(config.listenedMessages);
        lateInitialize();

        gateway = DiscordClientBuilder
                .create(Objects.requireNonNull(config.token))
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

    private void lateInitialize(){
        swears = new Fi("great_russian_language.txt", classpath)
                .readString("UTF-8")
                .replaceAll("\n", "")
                .split(", ");
        config.update();
    }

    private void sendInfo(int index){
        try{
            roleMessages = Seq.with(config.listenedMessages);
            InfoEmbed i = config.info.get(index - 1);
            if(i == null){
                Log.err("Info embed with index '@' not found", index);
                return;
            }

            Snowflake messageId = gateway.getChannelById(i.channelId)
                    .cast(TextChannel.class)
                    .flatMap(c -> c.createEmbed(e -> e.setColor(MessageService.normalColor)
                            .setTitle(i.title).setDescription(i.description)))
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
    public Publisher<?> onReactionAdd(ReactionAddEvent event){
        Member member = event.getMember().orElse(null);
        if(!roleMessages.isEmpty() && member != null){
            boolean[] b = validation.get(event.getUserId(), () -> new boolean[roleMessages.size]);
            for(int i = 0; i < roleMessages.size; i++){
                if(roleMessages.get(i).equals(event.getMessageId())){
                    b[i] = true;
                }
            }
            Snowflake userId = event.getUserId();
            validation.put(userId, b);
            if(Arrays.equals(b, all.get())){
                return member.addRole(config.memberRoleId).then(Mono.fromRunnable(() -> validation.remove(userId)));
            }
        }

        return Mono.empty();
    }

    public Mono<Void> handle(MessageCreateEvent event){
        Message message = event.getMessage();
        Member member = event.getMember().orElseThrow(RuntimeException::new);
        String text = message.getContent();
        return message.getChannel()
                .cast(TextChannel.class)
                .flatMap(channel -> {
                    if(!isAdmin(member)){
                        if(Structs.contains(swears, text::equalsIgnoreCase)){
                            return message.delete();
                        }
                    }

                    if(!Objects.equals(config.commandChannelId, channel.getId()) && !isAdmin(member)){
                        return Mono.empty();
                    }

                    return handler.handle(event);
                });
    }

    public boolean isAdmin(Member member){
        if(member == null) return false;
        boolean admin = member.getRoles()
                .any(r -> config.adminRoleId.equals(r.getId()) || r.getPermissions().contains(Permission.ADMINISTRATOR))
                .blockOptional().orElse(false);

        return ownerId.equals(member.getId()) || admin;
    }

    @Override
    public void run(String... args) throws Exception{
        if(args.length > 0 && args[0].equals("--info")){
            sendInfo(Strings.parseInt(args[1]));
        }
    }
}
