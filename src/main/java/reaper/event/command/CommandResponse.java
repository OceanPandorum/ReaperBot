package reaper.event.command;

import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.function.Consumer;

public interface CommandResponse{

    CommandResponse withDirectMessage();

    CommandResponse withReplyChannel(Mono<MessageChannel> channelSource);

    CommandResponse withScheduler(Scheduler scheduler);

    Mono<Void> sendMessage(Consumer<? super MessageCreateSpec> spec);

    Mono<Void> sendEmbed(Consumer<? super EmbedCreateSpec> spec);

    Mono<Void> sendTempEmbed(Consumer<? super EmbedCreateSpec> spec);
}
