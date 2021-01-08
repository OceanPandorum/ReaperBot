package reaper.command;

import arc.struct.*;
import arc.util.Strings;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.channel.MessageChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reaper.Constants;
import reaper.service.MessageService;

import java.util.List;

@Service
public class CommandHandler{
    private final ObjectMap<String, Command> commands = new ObjectMap<>();
    private String prefix;

    @Autowired
    private MessageService messageService;

    public void setPrefix(String prefix){
        this.prefix = prefix;
    }

    @Autowired(required = false)
    public void init(List<Command> commands){
        commands.forEach(c -> this.commands.put(c.compile().text, c));
        prefix = Constants.config.prefix;
    }

    public Mono<Void> handle(MessageCreateEvent event){
        Mono<MessageChannel> channel = event.getMessage().getChannel();
        String message = event.getMessage().getContent();
        if(!message.startsWith(prefix)){
            return Mono.empty();
        }

        message = message.substring(prefix.length());

        String commandstr = message.contains(" ") ? message.substring(0, message.indexOf(" ")) : message;
        String argstr = message.contains(" ") ? message.substring(commandstr.length() + 1) : "";

        Seq<String> result = new Seq<>();

        Command cmd = commands.get(commandstr);

        if(cmd != null){
            CommandInfo info = cmd.compile();
            int index = 0;
            boolean satisfied = false;
            CommandOperations operations = new CommandOperations(event);

            while(true){
                if(index >= info.params.length && !argstr.isEmpty()){
                    return messageService.err(channel, messageService.get("command.response.many-arguments.title"),
                                              messageService.format("command.response.many-arguments.description",
                                                                    prefix, info.text, info.paramText));
                }else if(argstr.isEmpty()){
                    break;
                }

                if(info.params[index].optional || index >= info.params.length - 1 || info.params[index + 1].optional){
                    satisfied = true;
                }

                if(info.params[index].variadic){
                    result.add(argstr);
                    break;
                }

                int next = argstr.indexOf(" ");
                if(next == -1){
                    if(!satisfied){
                        return messageService.err(channel, messageService.get("command.response.few-arguments.title"),
                                                  messageService.format("command.response.few-arguments.description",
                                                                        prefix, info.text));
                    }
                    result.add(argstr);
                    break;
                }else{
                    String arg = argstr.substring(0, next);
                    argstr = argstr.substring(arg.length() + 1);
                    result.add(arg);
                }

                index++;
            }

            if(!satisfied && info.params.length > 0 && !info.params[0].optional){
                return messageService.err(channel, messageService.get("command.response.few-arguments.title"),
                                          messageService.format("command.response.few-arguments.description",
                                                                prefix, info.text));
            }

            return cmd.execute(result.toArray(String.class), operations, operations);
        }else{
            int min = 0;
            CommandInfo closest = null;

            for(CommandInfo c : commands().map(Command::compile)){
                int dst = Strings.levenshtein(c.text, commandstr);
                if(dst < 3 && (closest == null || dst < min)){
                    min = dst;
                    closest = c;
                }
            }


            if(closest != null){
                return messageService.err(channel, messageService.format("command.response.found-closest", closest.text));
            }
            return messageService.err(channel, messageService.format("command.response.unknown", prefix));
        }
    }

    public Seq<Command> commands(){
        return commands.values().toSeq();
    }
}
