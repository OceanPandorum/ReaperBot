package reaperbot.data;

import net.dv8tion.jda.api.entities.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public class MessageMeta{
    private User user;
    private @Nullable Member member;
    private @Nonnull String oldContent = "", newContent = "";
    private TextChannel channel;
    private final long messageId;

    // Конструктор для пост-инициализации
    public MessageMeta(long messageId){
        this.messageId = messageId;
    }

    public MessageMeta(long messageId, @Nullable Member member, @Nonnull String content, TextChannel channel){
        this.messageId = messageId;
        this.user = member != null ? member.getUser() : null;
        this.member = member;
        this.oldContent = content;
        this.channel = channel;
    }

    // сеттеры

    public void user(User user){
        this.user = user;
    }

    public void member(@Nullable Member member){
        this.member = member;
    }

    public void content(@Nonnull String content){
        this.oldContent = newContent;
        this.newContent = content;
    }

    public void channel(TextChannel channel){
        this.channel = channel;
    }

    // геттеры

    public User user(){
        return user;
    }

    @Nullable
    public Member member(){
        return member;
    }

    @Nonnull
    public String oldContent(){
        return oldContent;
    }

    @Nonnull
    public String newContent(){
        return newContent;
    }

    public TextChannel channel(){
        return channel;
    }

    public long messageId(){
        return messageId;
    }

    @Override
    public boolean equals(Object o){
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;
        MessageMeta meta = (MessageMeta)o;
        return messageId == meta.messageId &&
               Objects.equals(user, meta.user) &&
               Objects.equals(member, meta.member) &&
               oldContent.equals(meta.oldContent) &&
               newContent.equals(meta.newContent) &&
               Objects.equals(channel, meta.channel);
    }

    @Override
    public int hashCode(){
        return Objects.hash(user, member, oldContent, newContent, channel, messageId);
    }

    @Override
    public String toString(){
        return "MessageMeta{" +
               "user=" + user +
               ", member=" + member +
               ", oldContent='" + oldContent + '\'' +
               ", newContent='" + newContent + '\'' +
               ", channel=" + channel +
               ", messageId=" + messageId +
               '}';
    }
}
