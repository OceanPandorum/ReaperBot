package reaperbot;

import arc.files.Fi;
import arc.func.Cons2;
import arc.util.*;
import arc.util.CommandHandler.*;
import arc.util.io.Streams;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Permission;
import mindustry.Vars;
import mindustry.game.*;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.management.*;
import java.util.*;
import java.util.function.Consumer;

import static reaperbot.ContentHandler.Map;
import static reaperbot.ReaperBot.*;

public class Commands{
    private final CommandHandler handler = new CommandHandler(prefix), adminHandler = new CommandHandler(prefix);

    Commands(){
        handler.register("help", bundle.get("commands.help.description"), args -> {
            StringBuilder common = new StringBuilder();
            Cons2<Command, StringBuilder> append = (command, builder) -> {
                builder.append(prefix);
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

            for(Command command : handler.getCommandList()){
                append.get(command, common);
            }

            if(isAdmin(listener.lastMember)){
                StringBuilder admin = new StringBuilder();
                for(Command command : adminHandler.getCommandList()){
                    append.get(command, admin);
                }

                listener.embed(embed -> embed.setColor(listener.normalColor)
                                             .addField(bundle.get("commands.help.title"), common.toString(), false)
                                             .addField(bundle.get("commands.help.admin.title"), admin.toString(), true));
            }else{
                listener.info(bundle.get("commands.help.title"), common.toString());
            }
        });

        // для дебагов
        adminHandler.register("status", bundle.get("commands.status.description"), args -> {
            StringBuilder builder = new StringBuilder();
            RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();

            long mem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
            builder.append(bundle.format("commands.status.memory", mem)).append('\n');
            builder.append(bundle.format("commands.status.uptime", Strings.formatMillis(rb.getUptime()))).append('\n');
            builder.append(bundle.format("commands.status.swears-count", listener.swears.length)).append('\n');
            builder.append(bundle.format("commands.status.schem-dir-size", schemDir.findAll(f -> f.extension().equals(Vars.schematicExtension)).size)).append('\n');
            builder.append(bundle.format("commands.status.map-dir-size", mapDir.findAll(f -> f.extension().equals(Vars.mapExtension)).size));

            listener.info(bundle.get("commands.status.title"), builder.toString());
        });

        adminHandler.register("delete", "<amount>", bundle.get("commands.delete.description"), args -> {
            if(Strings.parseInt(args[0]) <= 0){
                listener.err(bundle.get("commands.delete.incorrect-number"));
                return;
            }

            int number = Strings.parseInt(args[0]);

            if(number >= 100){
                listener.err(bundle.format("commands.delete.limit-number", 100));
                return;
            }

            listener.channel.getMessagesBefore(listener.lastMessage.getId())
                            .limitRequest(number)
                            .subscribe(m -> m.delete().block());

            Log.info("Deleted @ messages.", number);
        });

        handler.register("postmap", bundle.get("commands.postmap.description"), args -> {
            Message message = listener.lastMessage;

            if(message.getAttachments().size() != 1 ||
               message.getAttachments().stream().findFirst().map(a -> !a.getFilename().endsWith(Vars.mapExtension)).orElse(true)){
                listener.err(bundle.get("commands.postmap.empty-attachments"));
                listener.deleteMessages();
                return;
            }

            try{
                Attachment a = message.getAttachments().stream().findFirst().orElseThrow(RuntimeException::new);

                Map map = contentHandler.readMap(net.download(a.getUrl()));
                Fi mapFile = mapDir.child(a.getFilename());
                Fi image = mapDir.child("image_" + a.getFilename().replace(Vars.mapExtension, "png"));
                Streams.copy(net.download(a.getUrl()), mapFile.write());
                ImageIO.write(map.image, "png", image.file());

                Member member = message.getAuthorAsMember().block();
                Objects.requireNonNull(member);

                Consumer<EmbedCreateSpec> embed = e -> {
                    e.setColor(listener.normalColor);
                    e.setImage("attachment://" + image.name());
                    e.setAuthor(fullName(member), null, member.getAvatarUrl());
                    e.setTitle(map.name == null ? a.getFilename().replace(Vars.mapExtension, "") : map.name);
                    if(map.description != null) e.setFooter(map.description, null);
                };

                listener.guild.getChannelById(mapsChannelID)
                              .cast(TextChannel.class)
                              .flatMap(c -> c.createMessage(m -> m.addFile(image.name(), image.read()).setEmbed(embed).addFile(mapFile.name(), mapFile.read())))
                              .block();

                listener.text(bundle.get("commands.postmap.successful"));
            }catch(Exception e){
                Log.err(e);
                listener.err(bundle.get("commands.parsing-error"), Strings.neatError(e, true));
                listener.deleteMessages();
            }
        });

        handler.register("postschem", "[schem]", bundle.get("commands.postschem.description"), args -> {
            Message message = listener.lastMessage;
            Member member = message.getAuthorAsMember().block();

            try{
                Schematic schem = message.getAttachments().size() == 1
                ? contentHandler.parseSchematicURL(message.getAttachments().stream().findFirst().orElseThrow(RuntimeException::new).getUrl())
                : contentHandler.parseSchematic(args.length > 0 && args[0].startsWith("bXNjaAB") ? args[0] : null);

                if(schem == null){
                    throw new NullPointerException(bundle.get("commands.postschem.schem-is-null"));
                }

                BufferedImage preview = contentHandler.previewSchematic(schem);

                Fi previewFile = schemDir.child("img_" + UUID.randomUUID().toString() + ".png");
                Fi schemFile = schemDir.child(schem.name() + "." + Vars.schematicExtension);
                Schematics.write(schem, schemFile);
                ImageIO.write(preview, "png", previewFile.file());

                Consumer<EmbedCreateSpec> embed = e -> {
                    e.setColor(listener.normalColor);
                    e.setImage("attachment://" + previewFile.name());
                    e.setAuthor(fullName(member), null, member.getAvatarUrl()).setTitle(schem.name());
                    StringBuilder field = new StringBuilder();

                    schem.requirements().forEach(stack -> {
                        GuildEmoji result = listener.guild.getEmojis()
                                .filter(emoji -> emoji.getName().equalsIgnoreCase(stack.item.name.replace("-", "")))
                                .blockFirst();

                        field.append(result.asFormat()).append(stack.amount).append("  ");
                    });
                    e.setTitle(schem.name());
                    e.setDescription(field.toString());
                };

                listener.guild.getChannelById(schematicsChannelID)
                              .cast(TextChannel.class)
                              .flatMap(c -> c.createMessage(m -> m.addFile(previewFile.name(), previewFile.read())
                                                                  .setEmbed(embed).addFile(schemFile.name(), schemFile.read())))
                              .block();

                message.delete().block();
            }catch(Exception e){
                Log.err(e);
                listener.err(bundle.get("commands.parsing-error"), Strings.neatError(e, true));
                listener.deleteMessages();
            }
        });
    }

    public Mono<Void> handle(MessageCreateEvent event){
        Message message = event.getMessage();
        TextChannel channel = message.getChannel().cast(TextChannel.class).blockOptional().orElseThrow(RuntimeException::new);
        Member member = event.getMember().orElseThrow(RuntimeException::new);
        String text = message.getContent();

        if(!commands.isAdmin(member)){
            if(Structs.contains(listener.swears, text::equalsIgnoreCase)){
                return Mono.fromRunnable(() -> message.delete().block());
            }
        }

        if(!commandChannelID.equals(channel.getId()) && !isAdmin(member)) return Mono.empty();

        if(text.startsWith(prefix)){
            listener.channel = channel;
            listener.lastMember = member;
            listener.lastMessage = message;
        }

        return Mono.just(member).flatMap(m -> isAdmin(m) ? Mono.fromRunnable(() -> {
            boolean unknown = handleResponse(adminHandler.handleMessage(text), false);
            handleResponse(handler.handleMessage(text), !unknown);
        }) : Mono.fromRunnable(() -> handleResponse(handler.handleMessage(text), true)));
    }

    public boolean isAdmin(Member member){
        if(member == null) return false;
        boolean admin = member.getRoles()
                              .any(r -> adminRoleId.equals(r.getId()) || r.getPermissions().contains(Permission.ADMINISTRATOR))
                              .blockOptional().orElse(false);

        return ownerId.equals(member.getId()) || admin;
    }

    protected String fullName(Member member){
        String effectiveName = member.getUsername();
        if(member.getNickname().isPresent()){
            effectiveName += " / " + member.getNickname();
        }
        return effectiveName;
    }

    boolean handleResponse(CommandResponse response, boolean logUnknown){
        if(response.type == ResponseType.unknownCommand){
            if(logUnknown){
                listener.err(bundle.format("commands.response.unknown", prefix));
                listener.deleteMessages();
            }
            return false;
        }else if(response.type == ResponseType.manyArguments || response.type == ResponseType.fewArguments){
            if(response.command.params.length == 0){
                listener.err(bundle.get("commands.response.incorrect-arguments"),
                             bundle.format("commands.response.incorrect-argument",
                                           prefix, response.command.text));
            }else{
                listener.err(bundle.get("commands.response.incorrect-arguments"),
                             bundle.format("commands.response.incorrect-arguments.text",
                                           prefix, response.command.text, response.command.paramText));
            }
            listener.deleteMessages();
            return false;
        }
        return true;
    }
}
