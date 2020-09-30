package reaperbot;

import arc.files.Fi;
import arc.util.*;
import arc.util.CommandHandler.*;
import arc.util.io.Streams;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.type.ItemStack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.UUID;

import static reaperbot.ReaperBot.*;

public class Commands{
    private final String prefix = "$";
    private final CommandHandler handler = new CommandHandler(prefix);

    Commands(){
        handler.register("help", bundle.get("commands.help.description"), args -> {
            StringBuilder builder = new StringBuilder();

            for(Command command : handler.getCommandList()){
                builder.append(prefix);
                builder.append("**");
                builder.append(command.text);
                builder.append("**");
                if(command.params.length > 0){
                    builder.append(" *");
                    builder.append(command.paramText);
                    builder.append("*");
                }
                builder.append(" - ");
                builder.append(command.description);
                builder.append("\n");
            }

            listener.info(bundle.get("commands.help.title"), builder.toString());
        });
        handler.register("delete", "<amount>", bundle.get("commands.delete.description"), args -> {
            if(Strings.parseInt(args[0]) <= 0){
                listener.err(bundle.get("commands.delete.incorrect-number"));
                return;
            }

            int number = Integer.parseInt(args[0]) + 1;

            if(number >= 100){
                listener.err(bundle.get("commands.delete.limit-number"));
                return;
            }

            MessageHistory hist = listener.channel.getHistoryBefore(listener.lastMessage, number).complete();
            listener.channel.deleteMessages(hist.getRetrievedHistory()).queue();
            Log.info("Deleted {0} messages.", number);
        });
        handler.register("mute", "<@user> [reason...]", bundle.get("commands.mute.description"), args -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if(author.startsWith("!")) author = author.substring(1);

            long l = Long.parseLong(author);
            User user = listener.jda.retrieveUserById(l).complete();

            if(isAdmin(listener.guild.getMember(user))){
                listener.err(bundle.get("commands.user-is-admin"));
                return;
            }else if(user.isBot()){
                listener.err(bundle.get("commands.user-is-bot"));
                return;
            }else if(listener.lastUser == user){
                listener.err(bundle.get("commands.mute.self-user"));
                return;
            }

            listener.info(bundle.get("commands.mute.title"), bundle.get("commands.mute.text"), user.getAsMention());
            listener.guild.addRoleToMember(user.getIdLong(), listener.guild.getRoleById(747905698267922451L)).queue();
        });
        handler.register("unmute", "<@user>", bundle.get("commands.unmute.description"), args -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if(author.startsWith("!")) author = author.substring(1);

            long l = Long.parseLong(author);
            User user = listener.jda.retrieveUserById(l).complete();

            listener.info(bundle.get("commands.unmute.title"), bundle.get("commands.unmute.text"), user.getAsMention());
            listener.guild.removeRoleFromMember(user.getIdLong(), listener.guild.getRoleById(747905698267922451L)).queue();
        });
        handler.register("postmap", bundle.get("commands.postmap.description"), args -> {
            Message message = listener.lastMessage;

            if(message.getAttachments().size() != 1 || !message.getAttachments().get(0).getFileName().endsWith(".msav")){
                listener.err(bundle.get("commands.postmap.empty-attachments"));
                listener.deleteMessages();
                return;
            }

            Message.Attachment a = message.getAttachments().get(0);

            try{
                ContentHandler.Map map = contentHandler.parseMap(net.download(a.getUrl()));
                new File("maps/").mkdir();
                File mapFile = new File("maps/" + a.getFileName());
                File imageFile = new File("maps/image_" + a.getFileName().replace(".msav", ".png"));
                Streams.copy(net.download(a.getUrl()), new FileOutputStream(mapFile));
                ImageIO.write(map.image, "png", imageFile);

                User current = listener.lastUser;

                String name = listener.correctName(current);

                EmbedBuilder builder = new EmbedBuilder().setColor(listener.normalColor)
                        .setImage("attachment://" + imageFile.getName())
                        .setAuthor(name, null, current.getAvatarUrl()).setTitle(map.name == null ? a.getFileName().replace(".msav", "") : map.name);

                if(map.description != null) builder.setFooter(map.description);

                listener.channel.getGuild().getTextChannelById(mapsChannelID).sendFile(mapFile).addFile(imageFile).embed(builder.build()).queue();

                listener.text(bundle.get("commands.postmap.successful"));
            }catch(Exception e){
                listener.err(bundle.get("commands.parsing-error"), Strings.parseException(e, true));
                listener.deleteMessages();
            }
        });
        handler.register("postschem", "[schem]", bundle.get("commands.postschem.description"), args -> {
            Message message = listener.lastMessage;

            try{
                Schematic schem = message.getAttachments().size() == 1
                        ? contentHandler.parseSchematicURL(message.getAttachments().get(0).getUrl())
                        : contentHandler.parseSchematic(args[0]);

                BufferedImage preview = contentHandler.previewSchematic(schem);

                File previewFile = new File("schem/img_" + UUID.randomUUID().toString() + ".png");
                File schemFile = new File("schem/" + schem.name() + "." + Vars.schematicExtension);
                Schematics.write(schem, new Fi(schemFile));
                ImageIO.write(preview, "png", previewFile);

                EmbedBuilder builder = new EmbedBuilder().setColor(listener.normalColor).setColor(listener.normalColor)
                        .setImage("attachment://" + previewFile.getName())
                        .setAuthor(listener.correctName(message.getAuthor()), null,
                                message.getAuthor().getAvatarUrl()).setTitle(schem.name());

                StringBuilder field = new StringBuilder();

                for(ItemStack stack : schem.requirements()){
                    List<Emote> emotes = listener.jda.getEmotesByName(stack.item.name.replace("-", ""), true);
                    Emote result = emotes.isEmpty() ? listener.jda.getEmotesByName("ohno", true).get(0) : emotes.get(0);

                    field.append(result.getAsMention()).append(stack.amount).append("  ");
                }
                builder.setTitle(bundle.get("commands.postschem.requirements"));
                builder.setDescription(field.toString());

                listener.channel.getGuild().getTextChannelById(schematicsChannelID).sendFile(schemFile).addFile(previewFile).embed(builder.build()).queue();
                message.delete().queue();
            }catch(Exception e){
                listener.err(bundle.get("commands.parsing-error"), Strings.parseException(e, true));
                listener.deleteMessages();
            }
        });
    }

    void handle(MessageReceivedEvent event){
        String text = event.getMessage().getContentRaw();

        if(event.getChannel().getIdLong() != commandChannelID && !isAdmin(event.getMember())) return;

        if(event.getMessage().getContentRaw().startsWith(prefix)){
            listener.channel = event.getTextChannel();
            listener.lastUser = event.getAuthor();
            listener.lastMessage = event.getMessage();
        }

        handleResponse(handler.handleMessage(text));
    }

    boolean isAdmin(Member member){
        try{
            return member.getRoles().stream().anyMatch(r -> r.getIdLong() == 744837465310887996L || r.getIdLong() == 747899862389096640L);
        }catch(Exception e){
            return false;
        }
    }

    void handleResponse(CommandResponse response){
        if(response.type == ResponseType.unknownCommand){
            listener.err(bundle.format("commands.response.unknown", prefix));
            listener.deleteMessages();
        }else if(response.type == ResponseType.manyArguments || response.type == ResponseType.fewArguments){
            if(response.command.params.length == 0){
                listener.err(bundle.get("commands.response.incorrect-arguments"), bundle.format("commands.response.incorrect-argument",
                             prefix, response.command.text));
            }else{
                listener.err(bundle.get("commands.response.incorrect-arguments"), bundle.format("commands.response.incorrect-arguments.text",
                                        prefix, response.command.text, response.command.paramText));
            }
            listener.deleteMessages();
        }
    }
}
