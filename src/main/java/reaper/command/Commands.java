package reaper.command;

import arc.files.Fi;
import arc.func.Cons2;
import arc.struct.Seq;
import arc.util.*;
import arc.util.io.Streams;
import discord4j.common.util.Snowflake;
import discord4j.core.object.Embed;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.*;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.EmbedCreateSpec;
import io.netty.util.ResourceLeakDetector;
import mindustry.Vars;
import mindustry.game.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;
import reactor.util.*;
import reaper.ContentHandler;
import reaper.event.Listener;
import reaper.service.MessageService;
import reaper.util.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.management.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

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

    private final Timekeeper durkaCooldown = new Timekeeper(5);

    public Mono<Void> deleteMessages(Message... messages){
        Flux<Message> flux = Flux.just(messages);
        return Mono.delay(Duration.ofSeconds(20)).flatMapMany(__ -> flux.flatMap(Message::delete)).then();
    }

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
            builder.append(messageService.format("command.status.map-dir-size", mapDir.findAll(f -> f.extension().equals(Vars.mapExtension)).size));

            return messageService.info(req.getReplyChannel(), messageService.get("command.status.title"), builder.toString());
        }
    }

    @DiscordCommand(key = "вдурку", params = "<@user/id>", description = "command.vdurky.description")
    public class VdurkyCommand implements Command{
        @Override
        public Mono<Void> execute(String[] args, CommandRequest req, CommandResponse res){
            if(!durkaCooldown.get()){
                return req.getReplyChannel().flatMap(channel -> channel.createEmbed(spec -> spec.setColor(errorColor)
                        .setTitle(messageService.get("common.error"))
                        .setDescription(messageService.get("command.vdurky.cooldown"))))
                        .flatMap(self -> deleteMessages(self, req.getMessage()));
            }

            durkaCooldown.reset();
            Member member = req.getAuthorAsMember();
            Snowflake[] targetId = {MessageUtil.parseUserId(args[0])};
            if(targetId[0] == null){
                targetId[0] = member.getId();
            }

            return req.event().getGuild().flatMap(guild -> guild.getMemberById(targetId[0]))
                    .flatMap(target -> messageService.info(req.getReplyChannel(), messageService.get("command.vdurky.title"),
                            messageService.format("command.vdurky.text", member.getMention(), target.getMention())));
        }
    }

    @DiscordCommand(key = "postschem", params = "[schem]", description = "command.postschem.description")
    public class PostschemCommand implements Command{
        @Override
        public Mono<Void> execute(String[] args, CommandRequest req, CommandResponse res){
            Message message = req.getMessage();
            Member member = req.getAuthorAsMember();

            try{
                Schematic schem = message.getAttachments().size() == 1
                ? contentHandler.parseSchematicURL(message.getAttachments().stream().findFirst().orElseThrow(RuntimeException::new).getUrl())
                : args.length > 0 && args[0].startsWith(Vars.schematicBaseStart)
                ? contentHandler.parseSchematic(args[0]) : null;

                if(schem == null){
                    return req.getReplyChannel().flatMap(channel -> channel.createEmbed(spec -> spec.setColor(errorColor)
                            .setTitle(messageService.get("common.error"))
                            .setDescription(messageService.get("command.postschem.empty-attachments"))))
                            .flatMap(self -> deleteMessages(self, message));
                }

                BufferedImage preview = contentHandler.previewSchematic(schem);
                Fi previewFile = schemeDir.child(String.format("img_%s.png", UUID.randomUUID().toString()));
                Fi schemFile = schemeDir.child(schem.name() + "." + Vars.schematicExtension);
                Schematics.write(schem, schemFile);
                ImageIO.write(preview, "png", previewFile.file());

                Consumer<EmbedCreateSpec> embed = spec -> {
                    spec.setColor(normalColor);
                    spec.setImage("attachment://" + previewFile.name());
                    spec.setAuthor(member.getUsername(), null, member.getAvatarUrl());
                    spec.setTitle(schem.name());
                    StringBuilder field = new StringBuilder();

                    schem.requirements().forEach(stack -> req.event().getGuild().flatMap(guild -> guild.getEmojis()
                            .filter(emoji -> emoji.getName().equalsIgnoreCase(stack.item.name.replace("-", ""))).next())
                            .subscribe(emoji -> field.append(emoji.asFormat()).append(stack.amount).append("  ")));

                    spec.setTitle(schem.name());
                    spec.setDescription(field.toString());
                };

                return req.getClient().getChannelById(config.schematicsChannelId)
                        .cast(TextChannel.class)
                        .flatMap(c -> c.createMessage(m -> m.addFile(previewFile.name(), previewFile.read())
                                .setEmbed(embed).addFile(schemFile.name(), schemFile.read())))
                        .then(message.addReaction(success));
            }catch(Exception e){
                return req.getClient().getUserById(config.developerId)
                        .flatMap(User::getPrivateChannel)
                        .flatMap(channel -> channel.createEmbed(spec -> spec.setColor(errorColor)
                                .setTitle(messageService.get("command.parsing-error"))
                                .setDescription(Strings.neatError(e))
                        ))
                        .then(message.addReaction(failed));
            }
        }
    }

    @DiscordCommand(key = "postmap", description = "command.postmap.description")
    public class PostmapCommand implements Command{
        @Override
        public Mono<Void> execute(String[] args, CommandRequest req, CommandResponse res){
            Message message = req.getMessage();
            Member member = req.getAuthorAsMember();

            if(message.getAttachments().size() != 1 || message.getAttachments().stream().findFirst().map(a -> !a.getFilename().endsWith(Vars.mapExtension)).orElse(true)){
                return req.getReplyChannel().flatMap(channel -> channel.createEmbed(spec -> spec.setColor(errorColor)
                        .setTitle(messageService.get("common.error"))
                        .setDescription(messageService.get("command.postmap.empty-attachments"))))
                        .flatMap(self -> deleteMessages(self, message));
            }

            try{
                Attachment attachment = message.getAttachments().stream().findFirst().orElseThrow(RuntimeException::new);

                Fi mapFile = mapDir.child(attachment.getFilename());
                Streams.copy(Net.download(attachment.getUrl()), mapFile.write());
                ContentHandler.MapInfo map = contentHandler.readMap(mapFile.read());
                Fi image = mapDir.child(String.format("img_%s.png", UUID.randomUUID().toString()));
                ImageIO.write(map.image, "png", image.file());

                Consumer<EmbedCreateSpec> embed = spec -> {
                    spec.setColor(normalColor);
                    spec.setImage("attachment://" + image.name());
                    spec.setAuthor(member.getUsername(), null, member.getAvatarUrl());
                    spec.setTitle(map.name.orElse(attachment.getFilename().replace(Vars.mapExtension, "")));
                    map.description.ifPresent(description -> spec.setFooter(MessageUtil.trimTo(description, Embed.Footer.MAX_TEXT_LENGTH), null));
                };

                return req.getClient().getChannelById(config.mapsChannelId)
                        .publishOn(Schedulers.boundedElastic())
                        .cast(TextChannel.class)
                        .flatMap(c -> c.createMessage(m -> m.addFile(image.name(), image.read()).setEmbed(embed)
                                .addFile(mapFile.name(), mapFile.read())))
                        .then(message.addReaction(success));
            }catch(Exception e){
                return req.getClient().getUserById(config.developerId)
                        .flatMap(User::getPrivateChannel)
                        .flatMap(channel -> channel.createEmbed(spec -> spec.setColor(errorColor)
                                .setTitle(messageService.get("command.parsing-error"))
                                .setDescription(Strings.neatError(e))
                        ))
                        .then(message.addReaction(failed));
            }
        }
    }

    @DiscordCommand(key = "maps", params = "<server> [page]", description = "debug")
    public class MapsCommand implements Command{
        @Override
        public Mono<Void> execute(String[] args, CommandRequest req, CommandResponse res){
            if(!MessageUtil.canParseId(args[1]) && args.length > 2){
                return messageService.err(req.getReplyChannel(), "No u");
            }

            int index = args.length > 2 ? Strings.parseInt(args[1]) : 0;

            return send(args, req, index);
        }

        private Mono<Void> send(String[] args, CommandRequest req, int index){
            Mono<MessageChannel> channel = req.getReplyChannel();
            String path = config.serversMapDirs.get(args[0]);
            if(path == null){
                return messageService.err(channel, "Not found");
            }

            Seq<Fi> fiSeq = Fi.get(path).findAll(f -> f.extension().equals("msav"));
            if(index > fiSeq.size){
                return messageService.err(channel, "Oh no!");
            }

            Fi file = fiSeq.get(index);
            ContentHandler.MapInfo map;
            try{
                map = contentHandler.readMap(file.read());
            }catch(IOException e){
                throw new RuntimeException(e);
            }
            Fi image = mapDir.child(String.format("img_%s.png", UUID.randomUUID().toString()));
            try{
                ImageIO.write(map.image, "png", image.file());
            }catch(Throwable t){
                throw new RuntimeException(t);
            }

            Consumer<EmbedCreateSpec> embed = spec -> {
                spec.setColor(normalColor);
                spec.setImage("attachment://" + image.name());
                spec.setTitle(map.name.orElse(file.nameWithoutExtension()));
                spec.setDescription(messageService.format("command.maps.description", args[0], index, fiSeq.size));
                map.description.ifPresent(description -> spec.setFooter(MessageUtil.trimTo(description, Embed.Footer.MAX_TEXT_LENGTH), null));
            };

            return channel.flatMap(c -> c.createMessage(spec -> spec.addFile(image.name(), image.read()).setEmbed(embed)))
                    .doOnNext(signal -> {
                        all.each(emoji -> signal.addReaction(ReactionEmoji.unicode(emoji)).block());
                        reactionListener.onReactionAdd(signal.getId(), add -> {
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
                                    channel.flatMap(c -> c.createMessage(spec -> spec.addFile(file.name(), file.read()))).block();
                                }

                                return true;
                            }).orElse(false);
                        });
                    })
                    .then();
        }
    }
}
