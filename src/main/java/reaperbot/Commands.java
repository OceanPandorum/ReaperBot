package reaperbot;

import arc.files.Fi;
import arc.math.Mathf;
import arc.struct.Array;
import arc.util.*;
import arc.util.CommandHandler.*;
import arc.util.io.Streams;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.game.Schematics;
import mindustry.type.ItemStack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

import static reaperbot.ReaperBot.*;

public class Commands{
    private final String prefix = "$";
    private final CommandHandler handler = new CommandHandler(prefix);
    private final CommandHandler adminHandler = new CommandHandler(prefix);

    Commands() {
        handler.register("help", "Displays all bot commands.", args -> {
            StringBuilder builder = new StringBuilder();
            for (Command command : handler.getCommandList()) {
                builder.append(prefix);
                builder.append("**");
                builder.append(command.text);
                builder.append("**");
                if (command.params.length > 0) {
                    builder.append(" *");
                    builder.append(command.paramText);
                    builder.append("*");
                }
                builder.append(" - ");
                builder.append(command.description);
                builder.append("\n");
            }

            messages.info("Commands", builder.toString());
        });
        handler.register("rule", "<page>", "Displays rules.", args -> {
            try{
                Rules rule = Rules.valueOf(args[0]);
                messages.info(rule.title, rule.text);
            }catch(IllegalArgumentException e){
                e.printStackTrace();
                messages.err("Error", "Invalid topic '{0}'.\nValid topics: *{1}*", args[0], Arrays.toString(Rules.values()));
                messages.deleteMessages();
            }
        });
        handler.register("postmap", "Post a .msav file to the #maps--карты channel.", args -> {
            Message message = messages.lastMessage;

            if(message.getAttachments().size() != 1 || !message.getAttachments().get(0).getFileName().endsWith(".msav")){
                messages.err("You must have one .msav file in the same message as the command!");
                messages.deleteMessages();
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

                EmbedBuilder builder = new EmbedBuilder().setColor(messages.normalColor).setColor(messages.normalColor)
                .setImage("attachment://" + imageFile.getName())
                .setAuthor(messages.lastUser.getName(), messages.lastUser.getAvatarUrl(), messages.lastUser.getAvatarUrl()).setTitle(map.name == null ? a.getFileName().replace(".msav", "") : map.name);

                if(map.description != null) builder.setFooter(map.description);

                messages.channel.getGuild().getTextChannelById(mapsChannelID).sendFile(mapFile).addFile(imageFile).embed(builder.build()).queue();

                messages.text("*Map posted successfully.*");
            }catch(Exception e){
                e.printStackTrace();
                messages.err("Error parsing map.", Strings.parseException(e, true));
                messages.deleteMessages();
            }
        });
        handler.register("postschem", "[schem]", "Post a .msch to the #schematics--схемы channel.", args -> {
            Message message = messages.lastMessage;

            try{
                Schematic schem = message.getAttachments().size() == 1 ? contentHandler.parseSchematicURL(message.getAttachments().get(0).getUrl()) : contentHandler.parseSchematic(args[0]);

                BufferedImage preview = contentHandler.previewSchematic(schem);

                File previewFile = new File("schem/img_" + UUID.randomUUID().toString() + ".png");
                File schemFile = new File("schem/" + schem.name() + "." + Vars.schematicExtension);
                Schematics.write(schem, new Fi(schemFile));
                ImageIO.write(preview, "png", previewFile);

                EmbedBuilder builder = new EmbedBuilder().setColor(messages.normalColor).setColor(messages.normalColor)
                        .setImage("attachment://" + previewFile.getName())
                        .setAuthor(message.getAuthor().getName(), message.getAuthor().getAvatarUrl(), message.getAuthor().getAvatarUrl()).setTitle(schem.name());

                StringBuilder field = new StringBuilder();

                for(ItemStack stack : schem.requirements()){
                    List<Emote> emotes = jda.getEmotesByName(stack.item.name.replace("-", ""), true);
                    Emote result = emotes.isEmpty() ? jda.getEmotesByName("ohno", true).get(0) : emotes.get(0);

                    field.append(result.getAsMention()).append(stack.amount).append("  ");
                }
                builder.addField("Requirements", field.toString(), false);

                messages.channel.getGuild().getTextChannelById(schematicsChannelID).sendFile(schemFile).addFile(previewFile).embed(builder.build()).queue();
                message.delete().queue();
            }catch(Exception e){
                message.delete().queue();
                Log.err("Failed to parse schematic, skipping.");
                Log.err(e);
            }
        });
        adminHandler.register("delete", "<amount>", "Delete some messages.", args -> {
            try {
                int number = Integer.parseInt(args[0]) + 1;
                MessageHistory hist = messages.channel.getHistoryBefore(messages.lastMessage, number).complete();
                messages.channel.deleteMessages(hist.getRetrievedHistory()).queue();
                Log.info("Deleted {0} messages.", number);
            } catch (NumberFormatException e) {
                messages.err("Invalid number.");
            }
        });
        /*adminHandler.register("warn", "<@user> [reason...]", "Warn a user.", args -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if (author.startsWith("!")) author = author.substring(1);
            try {
                long l = Long.parseLong(author);
                User user = jda.getUserById(l);
                int warnings = prefs.getWarns(l) + 1;
                messages.text("**{0}**, you've been warned *{1}*.", user.getName(), warningStrings[Mathf.clamp(warnings - 1, 0, warningStrings.length - 1)]);
                prefs.put("warnings-" + l, String.valueOf(warnings));
                if (warnings >= 3) {
                    messages.guild.getTextChannelById(moderationChannelID)
                            .sendMessage("User " + user.getAsMention() + " has been warned 3 or more times!").queue();
                }
            } catch (Exception e) {
                e.printStackTrace();
                messages.err("Incorrect name format.");
                messages.deleteMessages();
            }
        });*/
        /*adminHandler.register("warnings", "<@user>", "Get number of warnings a user has.", args -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if (author.startsWith("!")) author = author.substring(1);
            try {
                long l = Long.parseLong(author);
                User user = jda.getUserById(l);
                int warnings = prefs.getWarns(l);
                messages.text("User '{0}' has **{1}** {2}.", user.getName(), warnings, warnings == 1 ? "warning" : "warnings");
            } catch (Exception e) {
                e.printStackTrace();
                messages.err("Incorrect name format.");
                messages.deleteMessages();
            }
        });*/
        /*adminHandler.register("clearwarnings", "<@user>", "Clear number of warnings for a person.", args -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if (author.startsWith("!")) author = author.substring(1);
            try {
                long l = Long.parseLong(author);
                User user = jda.getUserById(l);
                prefs.put("warnings-" + l, 0 + "");
                messages.text("Cleared warnings for user '{0}'.", user.getName());
            } catch (Exception e) {
                e.printStackTrace();
                messages.err("Incorrect name format.");
                messages.deleteMessages();
            }
        });*/
    }

    void handle(Message message){
        if(message.getAuthor().isBot()) return;

        String text = message.getContentRaw();

        if(message.getContentRaw().startsWith(prefix) && (isAdmin(message.getMember()) || message.getTextChannel().getIdLong() == commandChannelID)){
            messages.channel = message.getTextChannel();
            messages.lastUser = message.getAuthor();
            messages.lastMessage = message;
        }

        if(isAdmin(message.getMember())){
            boolean unknown = handleResponse(adminHandler.handleMessage(text), false);
            handleResponse(handler.handleMessage(text), !unknown);
        }else{
            handleResponse(handler.handleMessage(text), true);
        }
    }

    boolean isAdmin(Member member) {
        try {
            return member.getRoles().stream().anyMatch(r -> r.getIdLong() == 744837465310887996L
                    || r.getIdLong() == 746448599692345405L);
        } catch (Exception e) {
            return false;
        }
    }

    boolean handleResponse(CommandResponse response, boolean logUnknown){
        if(response.type == ResponseType.unknownCommand){
            if(logUnknown){
                messages.err("Unknown command. Type " + prefix + "help for a list of commands.");
                messages.deleteMessages();
            }
            return false;
        }else if(response.type == ResponseType.manyArguments || response.type == ResponseType.fewArguments){
            if(response.command.params.length == 0){
                messages.err("Invalid arguments.", "Usage: {0}{1}", prefix, response.command.text);
            }else{
                messages.err("Invalid arguments.", "Usage: {0}{1} *{2}*", prefix, response.command.text, response.command.paramText);
            }
            messages.deleteMessages();
            return false;
        }
        return true;
    }
}
