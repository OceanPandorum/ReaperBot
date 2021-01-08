package reaper.service.impl;

import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reaper.service.MessageService;

import java.util.Locale;
import java.util.function.Consumer;

@Service
public class MessageServiceImpl implements MessageService{

    private final ApplicationContext applicationContext;

    public MessageServiceImpl(ApplicationContext applicationContext){
        this.applicationContext = applicationContext;
    }

    @Override
    public String get(String key){
        try{
            return applicationContext.getMessage(key, null, Locale.forLanguageTag("ru"));
        }catch(Throwable t){
            return "???" + key + "???";
        }
    }

    @Override
    public String format(String key, Object... args){
        return applicationContext.getMessage(key, args, Locale.forLanguageTag("ru"));
    }

    @Override
    public Mono<Void> info(Mono<? extends MessageChannel> channel, String title, String text){
        return info(channel, e -> e.setColor(normalColor).setTitle(title).setDescription(text));
    }

    @Override
    public Mono<Void> info(Mono<? extends MessageChannel> channel, Consumer<EmbedCreateSpec> embed){
        return channel.publishOn(Schedulers.boundedElastic())
                .flatMap(c -> c.createEmbed(embed))
                .then();
    }

    @Override
    public Mono<Void> err(Mono<? extends MessageChannel> channel, String text){
        return err(channel, get("common.error"), text);
    }

    @Override
    public Mono<Void> err(Mono<? extends MessageChannel> channel, String title, String text){
        return channel.publishOn(Schedulers.boundedElastic())
                .flatMap(c -> c.createEmbed(e -> e.setColor(errorColor).setTitle(title).setDescription(text)))
                .then();
    }
}
