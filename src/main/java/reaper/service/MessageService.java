package reaper.service;

import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.reaction.ReactionEmoji;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Color;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

public interface MessageService{
    ReactionEmoji success = ReactionEmoji.unicode("✅");
    ReactionEmoji failed = ReactionEmoji.unicode("❌");

    Color normalColor = Color.of(0xb9fca6);
    Color errorColor = Color.of(0xff3838);

    String get(String key);

    String format(String key, Object... args);

    Mono<Void> info(Mono<? extends MessageChannel> channel, String title, String text);

    Mono<Void> info(Mono<? extends MessageChannel> channel, Consumer<EmbedCreateSpec> embed);

    Mono<Void> err(Mono<? extends MessageChannel> channel, String text);

    Mono<Void> err(Mono<? extends MessageChannel> channel, String title, String text);
}

