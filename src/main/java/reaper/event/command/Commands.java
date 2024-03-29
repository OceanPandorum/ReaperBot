package reaper.event.command;

import arc.files.Fi;
import arc.func.Cons2;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.io.Streams;
import discord4j.common.util.Snowflake;
import discord4j.core.object.Embed;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.EmbedCreateSpec;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.function.TupleUtils;
import reactor.util.Logger;
import reactor.util.Loggers;
import reaper.ContentHandler;
import reaper.event.MessageEventListener;
import reaper.event.ReactionEventListener;
import reaper.service.MessageService;
import reaper.util.MessageUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static reaper.Constants.*;
import static reaper.event.ReactionEventListener.all;
import static reaper.service.MessageService.*;

@Service
public class Commands{
    private static final Logger log = Loggers.getLogger(Commands.class);

    @Autowired
    private CommandHandler handler;

    @Autowired
    private MessageService messageService;

    @DiscordCommand(key = "help", description = "command.help.description")
    public class HelpCommand implements Command{
        @Override
        public Mono<Void> execute(String[] args, CommandRequest req, CommandResponse res){
            StringBuilder common = new StringBuilder();
            Cons2<CommandInfo, StringBuilder> append = (command, builder) -> {
                builder.append(config.prefix);
                builder.append("**");
                builder.append(command.text);
                builder.append("**");
                if(command.params.length > 0){
                    builder.append(" *");
                    builder.append(command.paramText);
                    builder.append("*");
                }
                builder.append(" - ");
                builder.append(messageService.get(command.description));
                builder.append("\n");
            };

            handler.commands().map(Command::compile).each(commandInfo -> append.get(commandInfo, common));

            return messageService.info(req.getReplyChannel(), messageService.get("command.help.title"), common.toString());
        }
    }

    @DiscordCommand(key = "мат", params = "<слово>", description = "command.swear.description")
    public class SwearCommand implements Command{
        public final ReactionEmoji ok = ReactionEmoji.custom(Snowflake.of(802541424624795689L), "kekav", false);

        @Override
        public Mono<Void> execute(String[] args, CommandRequest req, CommandResponse res){
            if(args[0].length() == 1){
                return messageService.err(req.getReplyChannel(), messageService.get("command.swear.many-length"));
            }else if(args[0].length() > 20){
                return messageService.err(req.getReplyChannel(), messageService.get("command.swear.few-length"));
            }else{
                return Mono.fromRunnable(() -> Fi.get("swears.txt").writeString(args[0] + "\n", true)).then(req.getMessage().addReaction(ok));
            }
        }
    }

    @DiscordCommand(key = "status", description = "command.status.description")
    public class StatusCommand implements Command{
        @Override
        public Mono<Void> execute(String[] args, CommandRequest req, CommandResponse res){
            StringBuilder builder = new StringBuilder();
            RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();

            long mem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
            builder.append(messageService.format("command.status.memory", mem)).append("\n");
            builder.append(messageService.format("command.status.uptime", Strings.formatMillis(rb.getUptime()))).append("\n");
            builder.append(messageService.format("command.status.swears-count", MessageEventListener.swears.length + Fi.get("swears.txt").readString().split("\n").length)).append("\n");
            builder.append(messageService.format("command.status.schem-dir-size", schemeDir.findAll(f -> f.extension().equals(Vars.schematicExtension)).size)).append("\n");
            builder.append(messageService.format("command.status.map-dir-size", mapDir.findAll(f -> f.extension().equals(Vars.mapExtension)).size)).append("\n");
            builder.append(messageService.format("command.status.validation-size", MessageEventListener.validation.size()));

            return messageService.info(req.getReplyChannel(), messageService.get("command.status.title"), builder.toString());
        }
    }

    @DiscordCommand(key = "postschem", params = "[схема]", description = "command.postschem.description")
    public class PostschemCommand implements Command{
        @Override
        public Mono<Void> execute(String[] args, CommandRequest req, CommandResponse res){
            Message message = req.getMessage();
            Member member = req.getAuthorAsMember();
            Seq<Attachment> attachments = Seq.with(message.getAttachments());

            if(attachments.size != 1 || !attachments.first().getFilename().endsWith(Vars.schematicExtension) || args.length > 0 && !args[0].startsWith(Vars.schematicBaseStart)){
                return res.sendTempEmbed(spec -> spec.setColor(errorColor)
                        .setTitle(messageService.get("common.error"))
                        .setDescription(messageService.get("command.postschem.empty-attachments")));
            }

            Mono<Schematic> schematic = Mono.fromCallable(() ->
            attachments.size == 1
            ? contentHandler.parseSchematicURL(attachments.first().getUrl())
            : args.length > 0 && args[0].startsWith(Vars.schematicBaseStart)
            ? contentHandler.parseSchematic(args[0]) : null
            );

            Fi previewFile = schemeDir.child(String.format("img_%s.png", UUID.randomUUID().toString()));
            AtomicReference<Fi> schemFile = new AtomicReference<>();
            Mono<Consumer<EmbedCreateSpec>> schem = schematic.flatMap(s -> {
                schemFile.set(schemeDir.child(s.name() + "." + Vars.schematicExtension));

                Mono<Void> pre = Mono.fromRunnable(() -> {
                    try{
                        BufferedImage preview = contentHandler.previewSchematic(s);
                        Schematics.write(s, schemFile.get());
                        ImageIO.write(preview, "png", previewFile.file());
                    }catch(Throwable ignored){}
                });

                return pre.thenReturn(spec -> spec.setColor(normalColor)
                        .setImage("attachment://" + previewFile.name())
                        .setAuthor(member.getUsername(), null, member.getAvatarUrl())
                        .setTitle(s.name()));
            });

            Function<Throwable, Mono<Void>> fallback = t -> req.getClient().getUserById(config.developerId)
                    .publishOn(Schedulers.boundedElastic())
                    .flatMap(User::getPrivateChannel)
                    .flatMap(channel -> channel.createEmbed(spec -> spec.setColor(errorColor)
                            .setTitle(messageService.get("command.parsing-error"))
                            .setDescription(MessageUtil.trimTo(Strings.neatError(t), Embed.MAX_DESCRIPTION_LENGTH))
                    ))
                    .then(message.addReaction(failed));

            return req.getClient().getChannelById(config.schematicsChannelId)
                    .publishOn(Schedulers.boundedElastic())
                    .ofType(TextChannel.class)
                    .zipWith(schem)
                    .flatMap(TupleUtils.function((channel, embed) -> channel.createMessage(spec -> spec.addFile(previewFile.name(), previewFile.read())
                            .setEmbed(embed).addFile(schemFile.get().name(), schemFile.get().read()))))
                    .then(message.addReaction(success))
                    .onErrorResume(fallback);
        }
    }

    @DiscordCommand(key = "postmap", description = "command.postmap.description")
    public class PostmapCommand implements Command{
        @Override
        public Mono<Void> execute(String[] args, CommandRequest req, CommandResponse res){
            Message message = req.getMessage();
            Member member = req.getAuthorAsMember();
            Seq<Attachment> attachments = Seq.with(message.getAttachments());

            if(attachments.size != 1 || !attachments.first().getFilename().endsWith(Vars.mapExtension)){
                return res.sendTempEmbed(spec -> spec.setColor(errorColor)
                        .setTitle(messageService.get("common.error"))
                        .setDescription(messageService.get("command.postmap.empty-attachments")));
            }

            Attachment attachment = attachments.first();

            Fi mapFile = mapDir.child(attachment.getFilename());
            Fi image = mapDir.child(String.format("img_%s.png", UUID.randomUUID().toString()));
            Mono<Consumer<EmbedCreateSpec>> map = Mono.defer(() -> {
                Mono<ContentHandler.Map> pre = Mono.fromCallable(() -> {
                    Streams.copy(MessageUtil.download(attachment.getUrl()), mapFile.write());
                    ContentHandler.Map mapInfo = contentHandler.readMap(mapFile.read());
                    ImageIO.write(mapInfo.image, "png", image.file());
                    return mapInfo;
                });

                return pre.map(info -> spec -> {
                    spec.setColor(normalColor);
                    spec.setImage("attachment://" + image.name());
                    spec.setAuthor(member.getUsername(), null, member.getAvatarUrl());
                    spec.setTitle(info.name().orElse(attachment.getFilename().replace(Vars.mapExtension, "")));
                    info.description().filter(s -> !s.isEmpty()).ifPresent(description -> spec.setFooter(MessageUtil.trimTo(description, Embed.Footer.MAX_TEXT_LENGTH), null));
                });
            });

            Function<Throwable, Mono<Void>> fallback = t -> req.getClient().getUserById(config.developerId)
                    .publishOn(Schedulers.boundedElastic())
                    .flatMap(User::getPrivateChannel)
                    .flatMap(channel -> channel.createEmbed(spec -> spec.setColor(errorColor)
                            .setTitle(messageService.get("command.parsing-error"))
                            .setDescription(MessageUtil.trimTo(Strings.neatError(t), Embed.MAX_DESCRIPTION_LENGTH))
                    ))
                    .then(message.addReaction(failed));

            return req.getClient().getChannelById(config.mapsChannelId)
                    .publishOn(Schedulers.boundedElastic())
                    .ofType(TextChannel.class)
                    .zipWith(map)
                    .flatMap(TupleUtils.function((channel, embed) ->  channel.createMessage(spec -> spec.addFile(image.name(), image.read())
                            .setEmbed(embed).addFile(mapFile.name(), mapFile.read()))))
                    .then(message.addReaction(success))
                    .onErrorResume(fallback);
        }
    }

    @DiscordCommand(key = "maps", params = "<сервер> [страница]", description = "command.maps.description")
    public class MapsCommand implements Command{
        @Override
        public Mono<Void> execute(String[] args, CommandRequest req, CommandResponse res){
            if(args.length > 1 && !MessageUtil.canParseInt(args[1])){
                return messageService.err(req.getReplyChannel(), messageService.get("command.postschem.page-not-int"));
            }

            int index = args.length > 1 ? Strings.parseInt(args[1]) : 1;

            return send(args, req, index);
        }

        private Mono<Void> send(String[] args, CommandRequest req, int index){
            Member member = req.getAuthorAsMember();
            Mono<MessageChannel> reply = req.getReplyChannel();
            String path = config.serversMapDirs.get(args[0]);
            if(path == null){
                return messageService.err(reply, messageService.format("command.maps.not-found-server", Strings.join(", ", config.serversMapDirs.keySet())));
            }

            Seq<Fi> fiSeq = Fi.get(path).findAll(f -> f.extension().equals(Vars.mapExtension));
            if(index >= fiSeq.size || index < 0){
                return messageService.err(reply, messageService.get("command.maps.index-of-bound"));
            }

            Fi file = fiSeq.get(index);
            Fi image = mapDir.child(String.format("img_%s.png", UUID.randomUUID().toString()));
            Mono<Consumer<EmbedCreateSpec>> map = Mono.defer(() -> {
                Mono<ContentHandler.Map> pre = Mono.fromCallable(() -> {
                    ContentHandler.Map mapInfo = contentHandler.readMap(file.read());
                    ImageIO.write(mapInfo.image, "png", image.file());
                    return mapInfo;
                });

                return pre.map(info -> spec -> spec.setColor(normalColor)
                        .setImage("attachment://" + image.name())
                        .setAuthor(member.getUsername(), null, member.getAvatarUrl())
                        .setTitle(info.name().orElse(file.nameWithoutExtension()))
                        .setDescription(messageService.format("command.maps.embed.description", args[0], index, fiSeq.size)));
            });

            return reply.publishOn(Schedulers.boundedElastic())
                    .zipWith(map)
                    .flatMap(TupleUtils.function((channel, spec) -> channel.createMessage(messageSpec -> messageSpec.addFile(image.name(), image.read()).setEmbed(spec))))
                    .flatMap(message -> {
                        Mono<Void> reaction = Flux.fromIterable(all).flatMap(emoji -> message.addReaction(ReactionEmoji.unicode(emoji))).then();

                        Mono<Void> controller = Mono.fromRunnable(() -> ReactionEventListener.onReactionAdd(message.getId(), add -> {
                            Optional<ReactionEmoji.Unicode> unicode = add.getEmoji().asUnicodeEmoji();
                            if(!add.getUserId().equals(req.getAuthorAsMember().getId())){
                                return false;
                            }

                            return unicode.map(u -> {
                                int i = all.indexOf(u.getRaw());
                                if(i == -1){
                                    return false;
                                }

                                if(i == 0){
                                    send(args, req, index - 1).block();
                                }else if(i == 2){
                                    send(args, req, index + 1).block();
                                }else{
                                    reply.flatMap(c -> c.createMessage(spec -> spec.addFile(file.name(), file.read()))).block();
                                }

                                return true;
                            }).orElse(false);
                        }));

                        return reaction.then(controller);
                    });
        }
    }
}
