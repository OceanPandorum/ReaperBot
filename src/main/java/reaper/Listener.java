package reaper;

import arc.Core;
import arc.files.Fi;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.Streams;
import discord4j.common.util.Snowflake;
import discord4j.core.*;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.message.*;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.*;
import discord4j.core.object.entity.channel.Channel.Type;
import discord4j.core.object.presence.Presence;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.*;
import discord4j.gateway.intent.*;
import discord4j.rest.response.ResponseFunction;
import discord4j.rest.util.*;
import mindustry.Vars;
import mindustry.game.*;
import mindustry.net.Host;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reaper.service.MessageService;

import javax.annotation.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.management.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static arc.Files.FileType.classpath;
import static reaper.Constants.*;

@Component
public class Listener extends ReactiveEventAdapter implements CommandLineRunner{
    private static final DateTimeFormatter statusFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss ZZZZ");
    private static final DateTimeFormatter fileFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy_HH-mm-ss");
    private static final ReactionEmoji success = ReactionEmoji.unicode("✅");
    private boolean[] all;

    @Autowired
    private MessageService bundle;

    protected GatewayDiscordClient gateway;
    protected Guild guild;
    protected TextChannel channel;
    protected Member lastMember;
    protected Message lastMessage, lastSentMessage;

    private final ObjectMap<Snowflake, boolean[]> validation = new ObjectMap<>();
    private Seq<Snowflake> roleMessages;

    private final CommandHandler handler, adminHandler;

    public final Color normalColor = Color.of(0xb9fca6), errorColor = Color.of(0xff3838);

    public String[] swears;

    public Listener(){
        configFile = Fi.get("prefs.json");
        if(!configFile.exists()){
            config = new Config();
            configFile.writeString(gson.toJson(config));
        }
        config = gson.fromJson(configFile.reader(), Config.class);

        cacheDir = new Fi("cache/");
        schemeDir = cacheDir.child("schem/");
        mapDir = cacheDir.child("map/");

        schemeDir.mkdirs();
        mapDir.mkdirs();

        handler = new CommandHandler(config.prefix);

        adminHandler = new CommandHandler(config.prefix);
    }

    @PostConstruct
    public void init(){
        contentHandler = new ContentHandler();

        Core.net = new arc.Net();

        roleMessages = Seq.with(config.listenedMessages);
        Schedulers.parallel().schedule(this::lateInitialize);
        Schedulers.parallel().schedule(this::register);

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

        gateway.on(this).subscribe();

        gateway.updatePresence(Presence.idle(ActivityUpdateRequest.builder().type(0).name(bundle.get("listener.status")).build())).block();

        guild = gateway.getGuildById(config.guildId).block();

        ownerId = gateway.rest().getApplicationInfo()
                         .map(ApplicationInfoData::owner)
                         .map(o -> Snowflake.of(o.id()))
                         .block();
    }

    private void register(){
        handler.register("help", bundle.get("commands.help.description"), args -> {
            StringBuilder common = new StringBuilder();
            Cons2<CommandHandler.Command, StringBuilder> append = (command, builder) -> {
                builder.append(config.prefix);
                builder.append("**");
                builder.append(command.text);
                builder.append("**");
                if(command.params.length > 0){
                    builder.append(" *");
                    builder.append(command.paramText);
                    builder.append('*');
                }
                builder.append(" - ");
                builder.append(command.description);
                builder.append('\n');
            };

            for(CommandHandler.Command command : handler.getCommandList()){
                append.get(command, common);
            }

            if(isAdmin(lastMember)){
                StringBuilder admin = new StringBuilder();
                for(CommandHandler.Command command : adminHandler.getCommandList()){
                    append.get(command, admin);
                }

                embed(embed -> embed.setColor(normalColor)
                                    .addField(bundle.get("commands.help.title"), common.toString(), false)
                                    .addField(bundle.get("commands.help.admin.title"), admin.toString(), true))
                        .subscribe();
            }else{
                info(bundle.get("commands.help.title"), common.toString()).subscribe();
            }
        });

        adminHandler.register("status", bundle.get("commands.status.description"), args -> {
            StringBuilder builder = new StringBuilder();
            RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();

            long mem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
            builder.append(bundle.format("commands.status.memory", mem)).append('\n');
            builder.append(bundle.format("commands.status.uptime", Strings.formatMillis(rb.getUptime()))).append('\n');
            builder.append(bundle.format("commands.status.swears-count", swears.length)).append('\n');
            builder.append(bundle.format("commands.status.schem-dir-size", schemeDir.findAll(f -> f.extension().equals(Vars.schematicExtension)).size)).append('\n');
            builder.append(bundle.format("commands.status.map-dir-size", mapDir.findAll(f -> f.extension().equals(Vars.mapExtension)).size));

            info(bundle.get("commands.status.title"), builder.toString()).subscribe();
        });

        adminHandler.register("delete", "<amount>", bundle.get("commands.delete.description"), args -> {
            if(Strings.parseInt(args[0]) <= 0){
                err(bundle.get("commands.delete.incorrect-number")).subscribe();
                return;
            }

            int number = Strings.parseInt(args[0]);

            if(number >= 100){
                err(bundle.format("commands.delete.limit-number", 100));
                return;
            }

            channel.getMessagesBefore(lastMessage.getId())
                            .limitRequest(number)
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe(m -> m.delete().block());
        });

        handler.register("postmap", bundle.get("commands.postmap.description"), args -> {
            Message message = lastMessage;

            if(message.getAttachments().size() != 1 ||
               message.getAttachments().stream().findFirst().map(a -> !a.getFilename().endsWith(Vars.mapExtension)).orElse(true)){
                err(bundle.get("commands.postmap.empty-attachments")).then(deleteMessages()).subscribe();
                return;
            }

            try{
                Attachment a = message.getAttachments().stream().findFirst().orElseThrow(RuntimeException::new);

                ContentHandler.Map map = contentHandler.readMap(Net.download(a.getUrl()));
                Fi mapFile = mapDir.child(a.getFilename());
                Fi image = mapDir.child(String.format("img_%s_%s.png", a.getFilename().replace(Vars.mapExtension, ""), fileFormatter.format(LocalDateTime.now())));
                Streams.copy(Net.download(a.getUrl()), mapFile.write());
                ImageIO.write(map.image, "png", image.file());

                Member member = message.getAuthorAsMember().block();
                Objects.requireNonNull(member);

                Consumer<EmbedCreateSpec> embed = spec -> {
                    spec.setColor(normalColor);
                    spec.setImage("attachment://" + image.name());
                    spec.setAuthor(member.getUsername(), null, member.getAvatarUrl());
                    spec.setTitle(map.name == null ? a.getFilename().replace(Vars.mapExtension, "") : map.name);
                    if(map.description != null) spec.setFooter(map.description, null);
                };

                guild.getChannelById(config.mapsChannelId)
                        .publishOn(Schedulers.boundedElastic())
                        .cast(TextChannel.class)
                        .flatMap(c -> c.createMessage(m -> m.addFile(image.name(), image.read()).setEmbed(embed)
                                .addFile(mapFile.name(), mapFile.read())))
                        .then(message.addReaction(success))
                        .block();
            }catch(Exception e){
                Log.err(e);
                err(bundle.get("commands.parsing-error"), Strings.neatError(e, true)).then(deleteMessages()).subscribe();
            }
        });

        handler.register("postschem", "[schem]", bundle.get("commands.postschem.description"), args -> {
            Message message = lastMessage;
            Member member = lastMember;

            try{
                Schematic schem = message.getAttachments().size() == 1
                ? contentHandler.parseSchematicURL(message.getAttachments().stream().findFirst().orElseThrow(RuntimeException::new).getUrl())
                : contentHandler.parseSchematic(args.length > 0 && args[0].startsWith(Vars.schematicBaseStart) ? args[0] : null);

                Objects.requireNonNull(schem, bundle.get("commands.postschem.schem-is-null"));

                BufferedImage preview = contentHandler.previewSchematic(schem);

                Fi previewFile = schemeDir.child(String.format("img_%s_%s.png", schem.name(), fileFormatter.format(LocalDateTime.now())));
                Fi schemFile = schemeDir.child(schem.name() + "." + Vars.schematicExtension);
                Schematics.write(schem, schemFile);
                ImageIO.write(preview, "png", previewFile.file());

                Consumer<EmbedCreateSpec> embed = spec -> {
                    spec.setColor(normalColor);
                    spec.setImage("attachment://" + previewFile.name());
                    spec.setAuthor(member.getUsername(), null, member.getAvatarUrl()).setTitle(schem.name());
                    StringBuilder field = new StringBuilder();

                    schem.requirements().forEach(stack -> {
                        GuildEmoji result = guild.getEmojis()
                                                          .filter(emoji -> emoji.getName().equalsIgnoreCase(stack.item.name.replace("-", "")))
                                                          .blockFirst();

                        field.append(Objects.requireNonNull(result).asFormat()).append(stack.amount).append("  ");
                    });
                    spec.setTitle(schem.name());
                    spec.setDescription(field.toString());
                };

                guild.getChannelById(config.schematicsChannelId)
                        .cast(TextChannel.class)
                        .flatMap(c -> c.createMessage(m -> m.addFile(previewFile.name(), previewFile.read())
                                .setEmbed(embed).addFile(schemFile.name(), schemFile.read())))
                        .then(message.addReaction(success))
                        .block();
            }catch(Exception e){
                Log.err(e);
                err(bundle.get("commands.parsing-error"), Strings.neatError(e, true)).then(deleteMessages()).subscribe();
            }
        });
    }

    @Scheduled(cron = "*/30 * * * * *") // каждые 30 секунд
    public void serviceStatus(){
        Func<String, String> replace = s -> s.replace("\\", "\\\\").replace("_", "\\_")
                                             .replace("*", "\\*").replace("`", "\\`");
        CopyOnWriteArrayList<Host> results = new CopyOnWriteArrayList<>();

        config.servers.forEach(server -> Net.pingServer(server, results::add));

        Schedulers.boundedElastic().schedule(() -> {
            results.sort((a, b) -> a.name != null && b.name == null
            ? 1 : a.name == null && b.name != null ? -1 : Integer.compare(a.players, b.players));

            Consumer<EmbedCreateSpec> embed = spec -> {
                spec.setColor(normalColor);
                results.stream().filter(h -> h.name != null).forEach(result -> {
                    spec.addField(result.address, Strings.format("*@*\n@: @\n@: @\n@: @\n@: @\n@: @\n_\n_\n",
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
                spec.setDescription(bundle.format("listener.servers.all", results.stream().mapToInt(h -> h.players).sum()));
                spec.setFooter(bundle.format("listener.servers.last-update", statusFormatter.format(ZonedDateTime.now())), null);
            };

            guild.getChannelById(config.serverChannelId)
                    .cast(TextChannel.class)
                    .flatMap(c -> c.getMessageById(config.serverMessageId).flatMap(m -> m.edit(e -> e.setEmbed(embed))))
                    .block();
        }, 2, TimeUnit.SECONDS);
    }

    //todo????
    private void lateInitialize(){
        all = new boolean[roleMessages.size];
        Arrays.fill(all, true);
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
            if(Arrays.equals(b, all)){
                return member.addRole(config.memberRoleId).then(Mono.fromRunnable(() -> validation.remove(userId)));
            }
        }

        return Mono.empty();
    }

    public Mono<Void> handle(MessageCreateEvent event){
        Message message = event.getMessage();                             // искоренить  //
        TextChannel channel = message.getChannel().cast(TextChannel.class).blockOptional().orElseThrow(RuntimeException::new);
        Member member = event.getMember().orElseThrow(RuntimeException::new);
        String text = message.getContent();

        if(!isAdmin(member)){
            if(Structs.contains(swears, text::equalsIgnoreCase)){
                return message.delete();
            }
        }

        if(!Objects.equals(config.commandChannelId, channel.getId()) && !isAdmin(member)){
            return Mono.empty();
        }

        this.channel = channel;
        if(text.startsWith(config.prefix)){
            lastMember = member;
            lastMessage = message;
        }

        return Mono.just(member).flatMap(m -> isAdmin(m) ? Mono.fromRunnable(() -> {
            boolean unknown = handleResponse(adminHandler.handleMessage(text), false);
            handleResponse(handler.handleMessage(text), !unknown);
        }) : Mono.fromRunnable(() -> handleResponse(handler.handleMessage(text), true)));
    }

    public Mono<Void> deleteMessages(){
        return Mono.fromRunnable(() -> Schedulers.boundedElastic().schedule(() -> {
            if(lastMessage != null && lastSentMessage != null){
                lastMessage.delete().block();
                lastSentMessage.delete().block();
            }
        }, 20, TimeUnit.SECONDS));
    }

    public boolean isAdmin(Member member){
        if(member == null) return false;
        boolean admin = member.getRoles()
                              .any(r -> config.adminRoleId.equals(r.getId()) || r.getPermissions().contains(Permission.ADMINISTRATOR))
                              .blockOptional().orElse(false);

        return ownerId.equals(member.getId()) || admin;
    }

    boolean handleResponse(CommandHandler.CommandResponse response, boolean logUnknown){
        if(response.type == CommandHandler.ResponseType.unknownCommand){
            if(logUnknown){
                err(bundle.format("commands.response.unknown", config.prefix)).then(deleteMessages()).subscribe();
            }
            return false;
        }else if(response.type == CommandHandler.ResponseType.manyArguments || response.type == CommandHandler.ResponseType.fewArguments){
            if(response.command.params.length == 0){
                err(bundle.get("commands.response.incorrect-arguments"),
                             bundle.format("commands.response.incorrect-argument",
                                           config.prefix, response.command.text)).subscribe();
            }else{
                err(bundle.get("commands.response.incorrect-arguments"),
                             bundle.format("commands.response.incorrect-arguments.text",
                                           config.prefix, response.command.text, response.command.paramText)).subscribe();
            }
            deleteMessages().subscribe();
            return false;
        }
        return true;
    }

    @Override
    public void run(String... args) throws Exception{
        if(args.length > 0 && args[0].equals("-info")){
            sendInfo(Strings.parseInt(args[1]));
        }
    }

    public Mono<Void> embed(Consumer<EmbedCreateSpec> embed){
        return channel.createEmbed(embed)
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(message -> lastSentMessage = message)
                .then();
    }

    public Mono<Void> info(String title, String text, Object... args){
        return channel.createEmbed(e -> e.setTitle(title).setDescription(Strings.format(text, args)).setColor(normalColor))
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(message -> lastSentMessage = message)
                .then();
    }

    public Mono<Void> err(String text, Object... args){
        return err(bundle.get("listener.error"), text, args);
    }

    public Mono<Void> err(String title, String text, Object... args){
        return channel.createEmbed(e -> e.setTitle(title).setDescription(Strings.format(text, args)).setColor(errorColor))
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(message -> lastSentMessage = message)
                .then();
    }
}
