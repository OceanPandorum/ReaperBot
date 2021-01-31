package reaper.service.impl;

import discord4j.common.util.Snowflake;
import discord4j.core.*;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.object.presence.Presence;
import discord4j.discordjson.json.*;
import discord4j.gateway.intent.*;
import discord4j.rest.response.ResponseFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reaper.service.*;

import javax.annotation.PreDestroy;
import java.util.*;

import static reaper.Constants.*;

@Component
public class DiscordServiceImpl implements DiscordService{
    private GatewayDiscordClient gateway;

    @Autowired
    private MessageService bundle;

    @Override
    public GatewayDiscordClient gateway(){
        return gateway;
    }

    @Autowired(required = false)
    public void init(List<ReactiveEventAdapter> adapters){

        gateway = DiscordClientBuilder.create(Objects.requireNonNull(config.token, "token"))
                .onClientResponse(ResponseFunction.emptyIfNotFound())
                .build()
                .gateway()
                .setEnabledIntents(IntentSet.of(
                        Intent.GUILD_MESSAGES,
                        Intent.GUILD_MESSAGE_REACTIONS
                ))
                .login()
                .blockOptional()
                .orElseThrow(RuntimeException::new);

        Flux.fromIterable(adapters).subscribe(adapter -> gateway.on(adapter).subscribe());

        gateway.updatePresence(Presence.idle(ActivityUpdateRequest.builder().type(0).name(bundle.get("discord.status")).build())).block();

        ownerId = gateway.rest().getApplicationInfo()
                .map(ApplicationInfoData::owner)
                .map(owner -> Snowflake.of(owner.id()))
                .cache();
    }

    @PreDestroy
    public void destroy(){
        gateway.logout().block();
    }
}
