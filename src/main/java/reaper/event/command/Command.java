package reaper.event.command;

import reactor.core.publisher.Mono;

public interface Command{

    Mono<Void> execute(String[] args, CommandRequest req, CommandResponse res);

    default CommandInfo compile(){
        DiscordCommand annotation = getClass().getDeclaredAnnotation(DiscordCommand.class);
        return new CommandInfo(annotation.key(), annotation.params(), annotation.description());
    }
}
