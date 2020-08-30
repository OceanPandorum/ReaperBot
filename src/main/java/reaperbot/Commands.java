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
    private final CommandHandler adminHandler = new CommandHandler(prefix);

    Commands() {
        handler.register("help", "Displays all bot commands.", args -> {
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
            listener.info("Commands", builder.toString());
        });
        handler.register("postmap", "Post a .msav file to the #maps channel.", args -> {
            Message message = listener.lastMessage;

            if(message.getAttachments().size() != 1 || !message.getAttachments().get(0).getFileName().endsWith(".msav")){
                listener.err("You must have one .msav file in the same message as the command!");
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

                EmbedBuilder builder = new EmbedBuilder().setColor(listener.normalColor).setColor(listener.normalColor)
                .setImage("attachment://" + imageFile.getName())
                .setAuthor(listener.lastUser.getName(), listener.lastUser.getAvatarUrl(), listener.lastUser.getAvatarUrl()).setTitle(map.name == null ? a.getFileName().replace(".msav", "") : map.name);

                if(map.description != null) builder.setFooter(map.description);

                listener.channel.getGuild().getTextChannelById(mapsChannelID).sendFile(mapFile).addFile(imageFile).embed(builder.build()).queue();

                listener.text("*Map posted successfully.*");
            }catch(Exception e){
                e.printStackTrace();
                listener.err("Error parsing map.", Strings.parseException(e, true));
                listener.deleteMessages();
            }
        });
        handler.register("postschem", "[schem]", "Post a .msch to the #schematics channel.", args -> {
            Message message = listener.lastMessage;

            try{
                Schematic schem = message.getAttachments().size() == 1 ? contentHandler.parseSchematicURL(message.getAttachments().get(0).getUrl()) : contentHandler.parseSchematic(args[0]);

                BufferedImage preview = contentHandler.previewSchematic(schem);

                File previewFile = new File("schem/img_" + UUID.randomUUID().toString() + ".png");
                File schemFile = new File("schem/" + schem.name() + "." + Vars.schematicExtension);
                Schematics.write(schem, new Fi(schemFile));
                ImageIO.write(preview, "png", previewFile);

                EmbedBuilder builder = new EmbedBuilder().setColor(listener.normalColor).setColor(listener.normalColor)
                        .setImage("attachment://" + previewFile.getName())
                        .setAuthor(message.getAuthor().getName(), message.getAuthor().getAvatarUrl(), message.getAuthor().getAvatarUrl()).setTitle(schem.name());

                StringBuilder field = new StringBuilder();

                for(ItemStack stack : schem.requirements()){
                    List<Emote> emotes = listener.jda.getEmotesByName(stack.item.name.replace("-", ""), true);
                    Emote result = emotes.isEmpty() ? listener.jda.getEmotesByName("ohno", true).get(0) : emotes.get(0);

                    field.append(result.getAsMention()).append(stack.amount).append("  ");
                }
                builder.addField("Requirements", field.toString(), false);

                listener.channel.getGuild().getTextChannelById(schematicsChannelID).sendFile(schemFile).addFile(previewFile).embed(builder.build()).queue();
                message.delete().queue();
            }catch(Exception e){
                message.delete().queue();
                Log.err("Failed to parse schematic, skipping.");
            }
        });
        adminHandler.register("delete", "<amount>", "Delete some messages.", args -> {
            try {
                int number = Integer.parseInt(args[0]) + 1;
                MessageHistory hist = listener.channel.getHistoryBefore(listener.lastMessage, number).complete();
                listener.channel.deleteMessages(hist.getRetrievedHistory()).queue();
                Log.info("Deleted {0} messages.", number);
            } catch (NumberFormatException e) {
                listener.err("Invalid number.");
            }
        });
    }

    void handle(MessageReceivedEvent event){
        String text = event.getMessage().getContentRaw();

        if(event.getMessage().getContentRaw().startsWith(prefix) && (isAdmin(event.getMember()) || event.getTextChannel().getIdLong() == commandChannelID)){
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

    boolean isAdmin(Member member) {
        try {
            return member.getRoles().stream().anyMatch(r -> r.getIdLong() == ownerRoleID || r.getIdLong() == adminRoleID);
        } catch (Exception e) {
            return false;
        }
    }

    boolean handleResponse(CommandResponse response, boolean logUnknown){
        if(response.type == ResponseType.unknownCommand){
            if(logUnknown){
                listener.err("Unknown command. Type " + prefix + "help for a list of commands.");
                listener.deleteMessages();
            }
            return false;
        }else if(response.type == ResponseType.manyArguments || response.type == ResponseType.fewArguments){
            if(response.command.params.length == 0){
                listener.err("Invalid arguments.", "Usage: {0}{1}", prefix, response.command.text);
            }else{
                listener.err("Invalid arguments.", "Usage: {0}{1} *{2}*", prefix, response.command.text, response.command.paramText);
            }
            listener.deleteMessages();
            return false;
        }
        return true;
    }
}
