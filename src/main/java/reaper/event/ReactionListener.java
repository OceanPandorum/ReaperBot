package reaper.event;

import arc.func.*;
import arc.struct.*;
import arc.util.*;
import discord4j.common.util.Snowflake;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.message.*;
import org.reactivestreams.Publisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ReactionListener extends ReactiveEventAdapter{
    public static final long EXPIRE_TIME = 30000;
    public static final Seq<String> all = Seq.with("⬅", "⏺", "➡️");

    public static ObjectMap<Snowflake, Func<ReactionAddEvent, Boolean>> addListeners = new ObjectMap<>();
    public static ObjectMap<Snowflake, Func2<ReactionAddEvent, ReactionRemoveEvent, Boolean>> listeners = new ObjectMap<>();
    public static ObjectMap<Snowflake, Long> messageTtl = new ObjectMap<>();

    @Override
    public Publisher<?> onReactionAdd(ReactionAddEvent event){
        Func<ReactionAddEvent, Boolean> addHandler = addListeners.get(event.getMessageId());
        if(addHandler != null){
            if(Boolean.TRUE.equals(addHandler.get(event))){
                addListeners.remove(event.getMessageId());
            }
        }
        return Mono.empty();
    }

    @Override
    public Publisher<?> onReactionRemove(ReactionRemoveEvent event){
        Func2<ReactionAddEvent, ReactionRemoveEvent, Boolean> handler = listeners.get(event.getMessageId());
        if(handler != null){
            if(Boolean.TRUE.equals(handler.get(null, event))){
                listeners.remove(event.getMessageId());
            }
        }
        return Mono.empty();
    }

    @Override
    public Publisher<?> onMessageDelete(MessageDeleteEvent event){
        return Mono.fromRunnable(() -> unsubscribe(event.getMessageId()));
    }

    @Scheduled(fixedDelay = EXPIRE_TIME)
    public void monitor(){
        Seq.with(messageTtl.entries()).each(e -> Time.timeSinceMillis(e.value) > EXPIRE_TIME, e -> unsubscribe(e.key));
    }

    public void onReactionAdd(Snowflake message, Func<ReactionAddEvent, Boolean> handler){
        addListeners.put(message, handler);
        messageTtl.put(message, Time.millis());
    }

    public void onReaction(Snowflake message, Func2<ReactionAddEvent, ReactionRemoveEvent, Boolean> handler){
        listeners.put(message, handler);
        messageTtl.put(message, Time.millis());
    }

    public void unsubscribe(Snowflake messageId){
        addListeners.remove(messageId);
        listeners.remove(messageId);
        messageTtl.remove(messageId);
    }
}
