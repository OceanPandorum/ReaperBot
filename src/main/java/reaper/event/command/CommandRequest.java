package reaper.event.command;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.*;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface CommandRequest{

    MessageCreateEvent event();

    Mono<MessageChannel> getReplyChannel();

    Mono<PrivateChannel> getPrivateChannel();

    default GatewayDiscordClient getClient(){
        return event().getClient();
    }

    default Message getMessage(){
        return event().getMessage();
    }

    default Optional<User> getAuthor(){
        return event().getMessage().getAuthor();
    }

    default Member getAuthorAsMember(){
        return event().getMember().orElseThrow(RuntimeException::new);
    }
}
