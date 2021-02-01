package reaper.event.command;

import arc.files.Fi;
import arc.func.Cons2;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.io.Streams;
import discord4j.core.object.Embed;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.*;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.EmbedCreateSpec;
import mindustry.Vars;
import mindustry.game.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;
import reactor.function.TupleUtils;
import reactor.util.*;
import reaper.ContentHandler;
import reaper.event.*;
import reaper.service.MessageService;
import reaper.util.MessageUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.management.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

import static reaper.Constants.*;
import static reaper.event.ReactionListener.all;
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

            for(CommandInfo command : handler.commands().map(Command::compile)){
                append.get(command, common);
            }

            return messageService.info(req.getReplyChannel(), messageService.get("command.help.title"), common.toString());
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
            builder.append(messageService.format("command.status.swears-count", Listener.swears.length)).append("\n");
            builder.append(messageService.format("command.status.schem-dir-size", schemeDir.findAll(f -> f.extension().equals(Vars.schematicExtension)).size)).append("\n");
            builder.append(messageService.format("command.status.map-dir-size", mapDir.findAll(f -> f.extension().equals(Vars.mapExtension)).size)).append("\n");
            builder.append(messageService.format("command.status.validation-size", Listener.validation.size()));

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

                return pre.then(Mono.fromCallable(() -> spec -> {
                    spec.setColor(normalColor);
                    spec.setImage("attachment://" + previewFile.name());
                    spec.setAuthor(member.getUsername(), null, member.getAvatarUrl());
                    spec.setTitle(s.name());
                }));
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
                Mono<ContentHandler.MapInfo> pre = Mono.fromCallable(() -> {
                    Streams.copy(MessageUtil.download(attachment.getUrl()), mapFile.write());
                    ContentHandler.MapInfo mapInfo = contentHandler.readMap(mapFile.read());
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
            if(index > fiSeq.size || index < 0){
                return messageService.err(reply, messageService.get("command.maps.index-of-bound"));
            }

            Fi file = fiSeq.get(index);
            Fi image = mapDir.child(String.format("img_%s.png", UUID.randomUUID().toString()));
            Mono<Consumer<EmbedCreateSpec>> map = Mono.defer(() -> {
                Mono<ContentHandler.MapInfo> pre = Mono.fromCallable(() -> {
                    ContentHandler.MapInfo mapInfo = contentHandler.readMap(file.read());
                    ImageIO.write(mapInfo.image, "png", image.file());
                    return mapInfo;
                });

                return pre.map(info -> spec -> {
                    spec.setColor(normalColor);
                    spec.setImage("attachment://" + image.name());
                    spec.setAuthor(member.getUsername(), null, member.getAvatarUrl());
                    spec.setTitle(info.name().orElse(file.nameWithoutExtension()));
                    spec.setDescription(messageService.format("command.maps.embed.description", args[0], index, fiSeq.size));
                });
            });

            return reply.publishOn(Schedulers.boundedElastic())
                    .zipWith(map)
                    .flatMap(TupleUtils.function((channel, spec) -> channel.createMessage(messageSpec -> messageSpec.addFile(image.name(), image.read()).setEmbed(spec))))
                    .flatMap(message -> {
                        Mono<Void> reaction = Flux.fromIterable(all).flatMap(emoji -> message.addReaction(ReactionEmoji.unicode(emoji))).then();

                        Mono<Void> controller = Mono.fromRunnable(() -> ReactionListener.onReactionAdd(message.getId(), add -> {
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

    // экспериментально
    // @DiscordCommand(key = "lconvert", params = "[forward/default] [buffer-size] [multiplier]", description = "command.lconvert.description")
    // public class LConvertCommand implements Command{
    //     @Override
    //     public Mono<Void> execute(String[] args, CommandRequest req, CommandResponse res){
    //         Message message = req.getMessage();
    //
    //         if(message.getAttachments().isEmpty()){
    //             return req.getReplyChannel().flatMap(channel -> channel.createEmbed(spec -> spec.setColor(errorColor)
    //                     .setTitle(messageService.get("common.error"))
    //                     .setDescription(messageService.get("command.lconvert.empty-attachments"))))
    //                     .flatMap(self -> deleteMessages(self, message));
    //         }
    //
    //         if(args.length > 2 && !MessageUtil.canParseInt(args[2])){
    //             return req.getReplyChannel().flatMap(channel -> channel.createEmbed(spec -> spec.setColor(errorColor)
    //                     .setTitle(messageService.get("common.error"))
    //                     .setDescription(messageService.get("command.lconvert.buffer-size-not-int"))))
    //                     .flatMap(self -> deleteMessages(self, message));
    //         }
    //
    //         if(args.length > 3 && !MessageUtil.canParseInt(args[3])){
    //             return req.getReplyChannel().flatMap(channel -> channel.createEmbed(spec -> spec.setColor(errorColor)
    //                     .setTitle(messageService.get("common.error"))
    //                     .setDescription(messageService.get("command.lconvert.multiplier-not-int"))))
    //                     .flatMap(self -> deleteMessages(self, message));
    //         }
    //
    //         Attachment attachment = message.getAttachments().stream().findFirst().orElseThrow(RuntimeException::new);
    //
    //         boolean forward = args.length > 1 && args[1].toLowerCase().equals("forward");
    //
    //         int bufferSize = args.length > 2 ? Strings.parseInt(args[2]) : 256;
    //
    //         int multiplier = args.length > 3 ? Strings.parseInt(args[3]) : 1;
    //
    //         Mono<BufferedImage> image = Mono.fromCallable(() -> ImageIO.read(MessageUtil.download(attachment.getUrl())));
    //         LContentHandler.HandlerSpec handlerSpec = new LContentHandler.HandlerSpec(image, bufferSize, multiplier, forward);
    //
    //         Fi f = lcontentHandler.convert(handlerSpec);
    //         Objects.requireNonNull(f);
    //
    //         Consumer<MessageCreateSpec> messageSpec = spec -> {
    //             if(f.length() > 32767){
    //                 spec.setContent(messageService.get("command.lconvert.length-warn"));
    //             }
    //             spec.addFile(f.name(), f.read());
    //         };
    //
    //         return res.sendMessage(messageSpec);
    //     }
    // }
}
