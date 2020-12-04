package reaperbot;

import arc.files.Fi;
import arc.util.*;
import arc.util.CommandHandler.*;
import arc.util.io.Streams;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.management.*;
import java.util.List;
import java.util.UUID;

import static reaperbot.ContentHandler.*;
import static reaperbot.ReaperBot.*;

public class Commands{
    private final String prefix = "$";
    private final CommandHandler handler = new CommandHandler(prefix), adminHandler = new CommandHandler(prefix);

    public void appendCommandInfo(Command command, StringBuilder builder){
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
    }

    Commands(){
        handler.register("help", bundle.get("commands.help.description"), args -> {
            StringBuilder builder = new StringBuilder();

            for(Command command : handler.getCommandList()){
                appendCommandInfo(command, builder);
            }

            if(isAdmin(listener.guild.getMember(listener.lastUser))){
                builder.append(bundle.get("commands.help.admin.title"));
                for(Command command : adminHandler.getCommandList()){
                    appendCommandInfo(command, builder);
                }
            }

            listener.info(bundle.get("commands.help.title"), builder.toString());
        });

        // для дебагов
        adminHandler.register("status", bundle.get("commands.status.description"), args -> {
            StringBuilder builder = new StringBuilder();
            RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();

            long mem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
            builder.append(bundle.format("commands.status.cache-size", logger.hist.size));
            builder.append('\n');
            builder.append(bundle.format("commands.status.memory", mem));
            builder.append('\n');
            builder.append(bundle.format("commands.status.uptime", Strings.formatMillis(rb.getUptime())));
            builder.append('\n');
            builder.append(bundle.format("commands.status.swears-count", listener.swears.length));
            builder.append('\n');
            builder.append(bundle.format("commands.status.schem-dir-size", schemDir.findAll(f -> f.extension().equals(Vars.schematicExtension)).size));
            builder.append('\n');
            builder.append(bundle.format("commands.status.map-dir-size", mapDir.findAll(f -> f.extension().equals(Vars.mapExtension)).size));

            listener.info(bundle.get("commands.status.title"), builder.toString());
        });

        adminHandler.register("delete", "<amount>", bundle.get("commands.delete.description"), args -> {
            if(Strings.parseInt(args[0]) <= 0){
                listener.err(bundle.get("commands.delete.incorrect-number"));
                return;
            }

            int number = Integer.parseInt(args[0]) + 1;

            if(number >= 100){
                listener.err(bundle.format("commands.delete.limit-number", 100));
                return;
            }

            MessageHistory hist = listener.channel.getHistoryBefore(listener.lastMessage, number).complete();
            listener.channel.deleteMessages(hist.getRetrievedHistory()).queue();
            Log.info("Deleted @ messages.", number);
        });

        handler.register("postmap", bundle.get("commands.postmap.description"), args -> {
            Message message = listener.lastMessage;

            if(message.getAttachments().size() != 1 || !message.getAttachments().get(0).getFileName().endsWith(Vars.mapExtension)){
                listener.err(bundle.get("commands.postmap.empty-attachments"));
                listener.deleteMessages();
                return;
            }

            Message.Attachment a = message.getAttachments().get(0);

            try{
                Map map = contentHandler.readMap(net.download(a.getUrl()));
                File mapFile = mapDir.child(a.getFileName()).file();
                Fi image = mapDir.child("image_" + a.getFileName().replace(".msav", ".png"));
                Streams.copy(net.download(a.getUrl()), new FileOutputStream(mapFile));
                ImageIO.write(map.image, "png", image.file());

                User current = listener.lastUser;

                String name = listener.fullName(current);

                EmbedBuilder builder = new EmbedBuilder()
                .setColor(listener.normalColor)
                .setImage("attachment://" + image.name())
                .setAuthor(name, null, current.getAvatarUrl())
                .setTitle(map.name == null ? a.getFileName().replace(Vars.mapExtension, "") : map.name);

                if(map.description != null) builder.setFooter(map.description);

                listener.guild.getTextChannelById(mapsChannelID).sendFile(mapFile)
                              .addFile(image.file()).embed(builder.build()).queue();

                listener.text(bundle.get("commands.postmap.successful"));
            }catch(Exception e){
                Log.err(e);
                listener.err(bundle.get("commands.parsing-error"), Strings.neatError(e, true));
                listener.deleteMessages();
            }
        });

        handler.register("postschem", "[schem]", bundle.get("commands.postschem.description"), args -> {
            Message message = listener.lastMessage;

            try{
                Schematic schem = message.getAttachments().size() == 1
                ? contentHandler.parseSchematicURL(message.getAttachments().get(0).getUrl())
                : contentHandler.parseSchematic(args.length > 0 ? args[0] : null);

                if(schem == null){
                    throw new NullPointerException(bundle.get("commands.postschem.schem-is-null"));
                }

                BufferedImage preview = contentHandler.previewSchematic(schem);

                File previewFile = schemDir.child("img_" + UUID.randomUUID().toString() + ".png").file();
                File schemFile = schemDir.child(schem.name() + "." + Vars.schematicExtension).file();
                Schematics.write(schem, new Fi(schemFile));
                ImageIO.write(preview, "png", previewFile);

                EmbedBuilder builder = new EmbedBuilder().setColor(listener.normalColor).setColor(listener.normalColor)
                                                         .setImage("attachment://" + previewFile.getName())
                                                         .setAuthor(listener.fullName(message.getAuthor()), null,
                                                                    message.getAuthor().getAvatarUrl()).setTitle(schem.name());

                StringBuilder field = new StringBuilder();

                schem.requirements().forEach(stack -> {
                    List<Emote> emotes = listener.jda.getEmotesByName(stack.item.name.replace("-", ""), true);
                    Emote result = emotes.isEmpty() ? listener.jda.getEmotesByName("ohno", true).get(0) : emotes.get(0);

                    field.append(result.getAsMention()).append(stack.amount).append("  ");
                });
                builder.setTitle(schem.name());
                builder.setDescription(field.toString());

                listener.guild.getTextChannelById(schematicsChannelID).sendFile(schemFile)
                              .addFile(previewFile).embed(builder.build()).queue();

                message.delete().queue();
            }catch(Exception e){
                Log.err(e);
                listener.err(bundle.get("commands.parsing-error"), Strings.neatError(e, true));
                listener.deleteMessages();
            }
        });
    }

    void handle(MessageReceivedEvent event){
        String text = event.getMessage().getContentRaw();

        if(!commands.isAdmin(event.getMember())){
            if(Structs.contains(listener.swears, text::equalsIgnoreCase)){
                event.getMessage().delete().queue();
                return;
            }
        }

        if(event.getChannel().getIdLong() != commandChannelID && !isAdmin(event.getMember())) return;

        if(event.getMessage().getContentRaw().startsWith(prefix)){
            listener.channel = event.getTextChannel();
            listener.lastUser = event.getAuthor();
            listener.lastMessage = event.getMessage();
        }

        if(isAdmin(event.getMember())){
            boolean unknown = handleResponse(adminHandler.handleMessage(text), false);
            handleResponse(handler.handleMessage(text), !unknown);
        }else{
            handleResponse(handler.handleMessage(text), true);
        }
    }

    boolean isAdmin(@Nullable Member member){
        return member != null && !member.getRoles().isEmpty() &&
               member.getRoles().stream().anyMatch(role -> role.hasPermission(Permission.ADMINISTRATOR));
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
