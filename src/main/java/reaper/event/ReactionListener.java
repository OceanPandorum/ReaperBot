package reaper.event;

import arc.func.*;
import arc.struct.Seq;
import arc.util.Time;
import discord4j.common.util.Snowflake;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.message.*;
import org.reactivestreams.Publisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReactionListener extends ReactiveEventAdapter{
    public static final long EXPIRE_TIME = 30000;
    public static final Seq<String> all = Seq.with("⬅", "⏺", "➡️");

    public static ConcurrentHashMap<Snowflake, Func<ReactionAddEvent, Boolean>> addListeners = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Snowflake, Func2<ReactionAddEvent, ReactionRemoveEvent, Boolean>> listeners = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Snowflake, Long> messageTtl = new ConcurrentHashMap<>();

    @Override
    public Publisher<?> onReactionAdd(ReactionAddEvent event){
        Func<ReactionAddEvent, Boolean> addHandler = addListeners.get(event.getMessageId());
        if(addHandler != null){
            if(Boolean.TRUE.equals(addHandler.get(event))){
                addListeners.remove(event.getMessageId());
            }
        }
        Func2<ReactionAddEvent, ReactionRemoveEvent, Boolean> handler = listeners.get(event.getMessageId());
        if(handler != null){
            if(Boolean.TRUE.equals(handler.get(event, null))){
                listeners.remove(event.getMessageId());
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
        Seq.with(messageTtl.entrySet()).each(e -> Time.timeSinceMillis(e.getValue()) >= EXPIRE_TIME, e -> unsubscribe(e.getKey()));
    }

    public static void onReactionAdd(Snowflake message, Func<ReactionAddEvent, Boolean> handler){
        addListeners.put(message, handler);
        messageTtl.put(message, Time.millis());
    }

    public static void onReaction(Snowflake message, Func2<ReactionAddEvent, ReactionRemoveEvent, Boolean> handler){
        listeners.put(message, handler);
        messageTtl.put(message, Time.millis());
    }

    public static void unsubscribe(Snowflake messageId){
        addListeners.remove(messageId);
        listeners.remove(messageId);
        messageTtl.remove(messageId);
    }
}
