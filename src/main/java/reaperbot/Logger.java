package reaperbot;

import arc.files.Fi;
import arc.func.*;
import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.serialization.Json;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.guild.*;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import reaperbot.data.MessageMeta;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

import static reaperbot.ReaperBot.*;

public class Logger extends ListenerAdapter{
    private final Fi tmp = new Fi("message.txt");
    private final int maxLength = 1024;
    private final Func<String, String> substringer = s -> s.length() >= maxLength ? s.substring(0, maxLength - 4) + "..." : s;
    private final Func<Message, String> parser = m -> {
        StringBuilder b = new StringBuilder(m.getContentRaw());
        if(!m.getAttachments().isEmpty()){
            b.append("\n---\n");
            m.getAttachments().forEach(a -> b.append(a.getUrl()).append("\n"));
        }
        return b.toString();
    };
    final ObjectMap<Long, MessageMeta> hist = new ObjectMap<>();

    public Logger(){
        try{
            Log.info("Logger listener loaded.");
            service.schedule((Runnable)hist::clear, 12, TimeUnit.HOURS); // чистка истории
        }catch(Throwable t){
            throw new RuntimeException(t);
        }
    }

    protected boolean needSubstring(@Nullable String c){
        return needSubstring(c, null);
    }

    protected boolean needSubstring(@Nullable String o, @Nullable String n){
        return o != null && o.length() > maxLength || n != null && n.length() > maxLength;
    }

    @Override // слушает только сообщения с сервера
    public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event){
        if(event.getAuthor().isBot()) return;

        Message m = event.getMessage();
        hist.put(m.getIdLong(), new MessageMeta(m.getIdLong(), event.getMember(), parser.get(m), m.getTextChannel()));
    }

    @Override // какого onGuildMessageUpdate не работает?!?!
    public void onMessageUpdate(@Nonnull MessageUpdateEvent event){
        Message m = event.getMessage();
        if(event.getAuthor().isBot() || event.getChannelType() == ChannelType.PRIVATE) return;

        MessageMeta meta = hist.get(m.getIdLong());
        if(meta == null) return;

        String oldContent = meta.oldContent();
        String newContent = parser.get(m);
        boolean write = needSubstring(oldContent, newContent);

        if(m.isPinned() || oldContent.equals(newContent)) return;
        meta.content(newContent);
                                                        // Todo цвета
        EmbedBuilder embed = new EmbedBuilder().setColor(listener.normalColor).setFooter(listener.time());
        embed.setAuthor(listener.fullName(m.getAuthor()), null, m.getAuthor().getEffectiveAvatarUrl());
        embed.setTitle(bundle.format("logger.message-edit.title", m.getChannel().getName()));
        embed.setDescription(bundle.format("logger.message-edit.description",
                                           m.getGuild().getId(),
                                           event.getChannel().getId(),
                                           m.getId()));
        embed.addField(bundle.get("logger.message-edit.old-content"), substringer.get(oldContent), false);
        embed.addField(bundle.get("logger.message-edit.new-content"), substringer.get(newContent), false);

        if(write){
            tmp.writeString(String.format("%s\n%s\n\n%s\n%s",
                                          bundle.get("message.edit.old-content"), oldContent,
                                          bundle.get("message.edit.new-content"), newContent));
        }

        log(embed, write);
    }

    @Override
    public void onGuildMessageDelete(@Nonnull GuildMessageDeleteEvent event){
        MessageMeta m = hist.get(event.getMessageIdLong());
        if(m == null || m.user().isBot()) return;

        String c = m.oldContent();
        boolean write = needSubstring(c);
        EmbedBuilder embed = new EmbedBuilder().setColor(listener.normalColor).setFooter(listener.time());
        embed.setAuthor(listener.fullName(m.user()), null, m.user().getEffectiveAvatarUrl());
        embed.setTitle(bundle.format("logger.message-delete.title", m.channel().getName()));
        embed.addField(bundle.get("logger.message-delete.content"), substringer.get(c), false);

        if(write){
            tmp.writeString(String.format("%s\n%s", bundle.get("message.delete.content"), c));
        }

        log(embed, write);
        hist.remove(m.messageId());
    }

    private void log(@Nonnull EmbedBuilder embed){
        log(embed, false);
    }

    protected void log(@Nonnull EmbedBuilder embed, boolean file){
        if(file){
            listener.guild.getTextChannelById(logChannelID).sendMessage(embed.build()).addFile(tmp.file()).queue();
        }else{
            listener.guild.getTextChannelById(logChannelID).sendMessage(embed.build()).queue();
        }
    }
}
