package reaper.command;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.*;
import discord4j.core.spec.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.*;

import java.util.function.*;

class CommandOperations implements CommandRequest, CommandResponse{
    private final MessageCreateEvent event;
    private final Supplier<Mono<MessageChannel>> replyChannel;
    private final Scheduler replyScheduler;

    public CommandOperations(MessageCreateEvent event){
        this.event = event;
        this.replyChannel = this::getReplyChannel;
        this.replyScheduler = Schedulers.immediate();
    }

    CommandOperations(MessageCreateEvent event, Supplier<Mono<MessageChannel>> replyChannel, Scheduler replyScheduler){
        this.event = event;
        this.replyChannel = replyChannel;
        this.replyScheduler = replyScheduler;
    }

    @Override
    public MessageCreateEvent event(){
        return event;
    }

    @Override
    public Mono<MessageChannel> getReplyChannel(){
        return event.getMessage().getChannel();
    }

    @Override
    public Mono<PrivateChannel> getPrivateChannel(){
        return Mono.justOrEmpty(event.getMessage().getAuthor()).flatMap(User::getPrivateChannel);
    }

    @Override
    public CommandResponse withDirectMessage(){
        return new CommandOperations(event, () -> getPrivateChannel().cast(MessageChannel.class), replyScheduler);
    }

    @Override
    public CommandResponse withReplyChannel(Mono<MessageChannel> channelSource){
        return new CommandOperations(event, () -> channelSource, replyScheduler);
    }

    @Override
    public CommandResponse withScheduler(Scheduler scheduler){
        return new CommandOperations(event, replyChannel, scheduler);
    }

    @Override
    public Mono<Void> sendMessage(Consumer<? super MessageCreateSpec> spec){
        return replyChannel.get()
                .publishOn(replyScheduler)
                .flatMap(channel -> channel.createMessage(spec))
                .then();
    }

    @Override
    public Mono<Void> sendEmbed(Consumer<? super EmbedCreateSpec> spec){
        return replyChannel.get()
                .publishOn(replyScheduler)
                .flatMap(channel -> channel.createEmbed(spec))
                .then();
    }
}
