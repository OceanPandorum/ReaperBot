package reaperbot;

import arc.math.Mathf;
import arc.util.CommandHandler;
import arc.util.CommandHandler.*;
import arc.util.Log;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;

import java.util.Arrays;

import static reaperbot.ReaperBot.*;

public class Commands{
    private final String prefix = "!";
    private final CommandHandler handler = new CommandHandler(prefix);
    private final CommandHandler adminHandler = new CommandHandler(prefix);
    private final String[] warningStrings = {"once", "twice", "thrice", "too many times"};

    Commands() {
        handler.register("help", "Displays all bot commands.", args -> {
            if (messages.lastMessage.getChannel().getIdLong() != commandChannelID) {
                messages.err("Use this command in #bots.");
                messages.deleteMessages();
                return;
            }

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

        handler.register("ping", "<ip>", "Pings a server.", args -> {
            if(!messages.lastMessage.getChannel().getName().equalsIgnoreCase("bots")){
                messages.err("Use this command in #bots.");
                messages.deleteMessages();
                return;
            }

            net.pingServer(args[0], result -> {
                if(result.name != null){
                    messages.info("Server Online", "Host: {0}\nPlayers: {1}\nMap: {2}\nWave: {3}\nVersion: {4}\nPing: {5}ms",
                    result.name, result.players, result.mapname, result.wave, result.version, result.ping);
                }else{
                    messages.err("Server Offline", "Timed out.");
                }
            });
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


        /*handler.register("postplugin", "<github-url>", "Post a plugin via Github repository URL.", args -> {
            if(!args[0].startsWith("https") || !args[0].contains("github")){
                messages.err("That's not a valid Github URL.");
            }else{
                try{
                    Document doc = Jsoup.connect(args[0]).get();

                    EmbedBuilder builder = new EmbedBuilder().setColor(messages.normalColor).
                    setColor(messages.normalColor)
                    .setAuthor(messages.lastUser.getName(), messages.lastUser.getAvatarUrl(), messages.lastUser.getAvatarUrl()).setTitle(doc.select("strong[itemprop=name]").text());

                    Elements elem = doc.select("span[itemprop=about]");
                    if(!elem.isEmpty()){
                        builder.addField("About", elem.text(), false);
                    }

                    builder.addField("Link", args[0], false);

                    builder.addField("Downloads", args[0] + (args[0].endsWith("/") ? "" : "/") + "releases", false);

                    messages.channel.getGuild().getTextChannelById(pluginChannelID).sendMessage(builder.build()).queue();

                    messages.text("*Plugin posted.*");
                }catch(IOException e){
                    e.printStackTrace();
                    messages.err("Failed to fetch plugin info from URL.");
                }
            }
        });

        handler.register("postmap", "Post a .msav file to the #maps channel.", args -> {
            Message message = messages.lastMessage;

            if(message.getAttachments().size() != 1 || !message.getAttachments().get(0).getFileName().endsWith(".msav")){
                messages.err("You must have one .msav file in the same message as the command!");
                messages.deleteMessages();
                return;
            }

            Attachment a = message.getAttachments().get(0);

            try{
                Map map = contentHandler.parseMap(net.download(a.getUrl()));
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

        handler.register("addserver", "<IP>", "Add your server to list. Must be online and 24/7.", args -> {
            Array<String> servers = prefs.getArray("servers");
            if(servers.contains(args[0])){
                messages.err("That server is already in the list.");
            }else{
                TextChannel channel = messages.channel;
                Member mem = messages.lastMessage.getMember();
                net.pingServer(args[0], res -> {
                    if(res.name != null){
                        servers.add(args[0]);
                        prefs.putArray("servers", servers);
                        prefs.put("owner-" + args[0], mem.getId());
                        channel.sendMessage("*Server added.*").queue();
                    }else{
                        channel.sendMessage("*That server is offline or cannot be reached.*").queue();
                    }
                });
            }
        });

        handler.register("getposter", "<IP>", "Get who posted a server. This may not necessarily be the owner.", args -> {
            Array<String> servers = prefs.getArray("servers");
            String key = "owner-" + args[0];
            if(!servers.contains(args[0])){
                messages.err("That server doesn't exist.");
                messages.deleteMessages();
            }else if(prefs.get(key, null) == null){
                messages.err("That server doesn't have a registered poster or maintainer.");
                messages.deleteMessages();
            }else{
                User user = jda.getUserById(prefs.get(key, null));
                if(user != null){
                    messages.info("Owner of: " + args[0], "{0}#{1}", user.getName(), user.getDiscriminator());
                }else{
                    messages.err("Use lookup failed. Internal error, or the user may have left the server.");
                    messages.deleteMessages();
                }
            }
        });*/
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
        adminHandler.register("warn", "<@user> [reason...]", "Warn a user.", args -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if (author.startsWith("!")) author = author.substring(1);
            try {
                long l = Long.parseLong(author);
                User user = jda.getUserById(l);
                int warnings = prefs.getInt("warnings-" + l, 0) + 1;
                messages.text("**{0}**, you've been warned *{1}*.", user.getAsMention(), warningStrings[Mathf.clamp(warnings - 1, 0, warningStrings.length - 1)]);
                prefs.put("warnings-" + l, String.valueOf(warnings));
                if (warnings >= 3) {
                    messages.lastMessage.getGuild().getTextChannelById(moderationChannelID)
                            .sendMessage("User " + user.getAsMention() + " has been warned 3 or more times!").queue();
                }
            } catch (Exception e) {
                e.printStackTrace();
                messages.err("Incorrect name format.");
                messages.deleteMessages();
            }
        });
        adminHandler.register("warnings", "<@user>", "Get number of warnings a user has.", args -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if (author.startsWith("!")) author = author.substring(1);
            try {
                long l = Long.parseLong(author);
                User user = jda.getUserById(l);
                int warnings = prefs.getInt("warnings-" + l, 0);
                messages.text("User '{0}' has **{1}** {2}.", user.getName(), warnings, warnings == 1 ? "warning" : "warnings");
            } catch (Exception e) {
                e.printStackTrace();
                messages.err("Incorrect name format.");
                messages.deleteMessages();
            }
        });
        adminHandler.register("clearwarnings", "<@user>", "Clear number of warnings for a person.", args -> {
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
        });
    }

    boolean emptyText(Message message){
        return message.getContentRaw() == null || message.getContentRaw().isEmpty();
    }

    void edited(Message message){
        messages.logTo("------\n**{0}#{1}** just edited a message.\n\n*From*: \"{2}\"\n*To*: \"{3}\"", message.getAuthor().getName(), message.getAuthor().getDiscriminator(), "<broken>", message.getContentRaw());
    }

    void deleted(Message message){
        if(message == null || message.getAuthor() == null) return;
        messages.logTo("------\n**{0}#{1}** just deleted a message.\n *Text:* \"{2}\"", message.getAuthor().getName(), message.getAuthor().getDiscriminator(), message.getContentRaw());
    }

    void handle(Message message){
        if(message.getAuthor().isBot()) return;

        /*if(isAdmin(message.getAuthor()) && message.getChannel().getIdLong() == commandChannelID){
            server.send(message.getContentRaw());
            return;
        }

        if(message.getChannel().getIdLong() == screenshotsChannelID && message.getAttachments().isEmpty()){
            message.delete().queue();
            try{
                message.getAuthor().openPrivateChannel().complete().sendMessage("Don't send messages without images in the #screenshots channel.").queue();
            }catch(Exception e){
                e.printStackTrace();
            }
            return;
        }*/

        String text = message.getContentRaw();

        if(message.getContentRaw().startsWith(prefix)){
            messages.channel = message.getTextChannel();
            messages.lastUser = message.getAuthor();
            messages.lastMessage = message;
        }

        /*if((message.getContentRaw().startsWith(ContentHandler.schemHeader) && message.getAttachments().isEmpty()) ||
        (message.getAttachments().size() == 1 && message.getAttachments().get(0).getFileExtension() != null && message.getAttachments().get(0).getFileExtension().equals(Vars.schematicExtension))){
            try{
                Schematic schem = message.getAttachments().size() == 1 ? contentHandler.parseSchematicURL(message.getAttachments().get(0).getUrl()) : contentHandler.parseSchematic(message.getContentRaw());
                BufferedImage preview = contentHandler.previewSchematic(schem);

                File previewFile = new File("img_" + UUID.randomUUID().toString() + ".png");
                File schemFile = new File(schem.name() + "." + Vars.schematicExtension);
                Schematics.write(schem, new Fi(schemFile));
                ImageIO.write(preview, "png", previewFile);

                EmbedBuilder builder = new EmbedBuilder().setColor(messages.normalColor).setColor(messages.normalColor)
                .setImage("attachment://" + previewFile.getName())
                .setAuthor(message.getAuthor().getName(), message.getAuthor().getAvatarUrl(), message.getAuthor().getAvatarUrl()).setTitle(schem.name());

                StringBuilder field = new StringBuilder();

                for(ItemStack stack : schem.requirements()){
                    List<Emote> emotes = guild.getEmotesByName(stack.item.name.replace("-", ""), true);
                    Emote result = emotes.isEmpty() ? guild.getEmotesByName("ohno", true).get(0) : emotes.get(0);

                    field.append(result.getAsMention()).append(stack.amount).append("  ");
                }
                builder.addField("Requirements", field.toString(), false);

                message.getChannel().sendFile(schemFile).addFile(previewFile).embed(builder.build()).queue();
                message.delete().queue();
            }catch(Throwable e){
                if(message.getTextChannel().getIdLong() == schematicsChannelID){
                    message.delete().queue();
                    try{
                        message.getAuthor().openPrivateChannel().complete().sendMessage("Invalid schematic: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")")).queue();
                    }catch(Exception e2){
                        e2.printStackTrace();
                    }
                }

                Log.err("Failed to parse schematic, skipping.");
                Log.err(e);
            }
        }else if(message.getTextChannel().getIdLong() == schematicsChannelID && !isAdmin(message.getAuthor())){
            message.delete().queue();
            try{
                message.getAuthor().openPrivateChannel().complete().sendMessage("Only send valid schematics in the #schematics channel. You may send them either as clipboard text or as a schematic file.").queue();
            }catch(Exception e){
                e.printStackTrace();
            }
            return;
        }*/

        if(isAdmin(message.getAuthor())){
            boolean unknown = handleResponse(adminHandler.handleMessage(text), false);
            handleResponse(handler.handleMessage(text), !unknown);
        }else{
            handleResponse(handler.handleMessage(text), true);
        }
    }

    boolean isAdmin(User user) {
        try {
            return jda.getRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase("developer"));
        } catch (Exception e) {
            return false;
        }
    }

    boolean handleResponse(CommandResponse response, boolean logUnknown){
        if(response.type == ResponseType.unknownCommand){
            if(logUnknown){
                messages.err("Unknown command. Type !help for a list of commands.");
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
