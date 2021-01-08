package reaper.command;

import reactor.core.publisher.Mono;

public interface Command{

    Mono<Void> execute(String[] args, CommandRequest req, CommandResponse res);

    default CommandInfo compile(){
        DiscordCommand a = getClass().getDeclaredAnnotation(DiscordCommand.class);
        return new CommandInfo(a.key(), a.params(), a.description());
    }
}
