package reaper.controller;

import arc.util.Strings;
import discord4j.core.object.entity.channel.TextChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.*;
import reactor.util.*;
import reaper.Constants;
import reaper.presence.AdminActionType;
import reaper.presence.entity.AdminAction;
import reaper.presence.repository.AdminActionRepository;
import reaper.service.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1")
public class AdminActionController{
    private static final Logger log = Loggers.getLogger(AdminActionController.class);

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM.dd.yyyy HH:mm:ss")
            .withLocale(Locale.forLanguageTag("ru"))
            .withZone(ZoneId.systemDefault());

    private final AdminActionRepository repository;

    private final DiscordService discordService;

    private final MessageService messageService;

    public AdminActionController(@Autowired AdminActionRepository repository,
                                 @Autowired DiscordService discordService,
                                 @Autowired MessageService messageService){
        this.repository = repository;
        this.discordService = discordService;
        this.messageService = messageService;
    }

    @GetMapping("/actions/{type}")
    public Flux<AdminAction> get(@PathVariable AdminActionType type){
        return repository.findAllByType(type);
    }

    @GetMapping("/actions/{type}/{targetId}")
    public Mono<AdminAction> get(@PathVariable AdminActionType type, @PathVariable String targetId){
        return repository.findByTypeAndTargetId(type, targetId);
    }

    @PostMapping("/actions")
    public Mono<AdminAction> add(@RequestBody AdminAction adminAction){
        Mono<AdminAction> action = repository.findByTypeAndTargetId(adminAction.type(), adminAction.targetId());
        return action.hasElement().flatMap(bool -> bool ? action.flatMap(a -> repository.save(a.plusEndTimestamp(adminAction.endTimestamp()))) : repository.save(adminAction))
                .flatMap(act -> discordService.gateway().getChannelById(Constants.config.banListId)
                        .ofType(TextChannel.class)
                        .flatMap(channel -> channel.createEmbed(spec -> spec.setColor(MessageService.normalColor)
                                .setDescription(String.format("%s%n%s%n%s%n%s",
                                messageService.format("admin-action.target", Strings.stripColors(act.targetNickname())),
                                messageService.format("admin-action.admin", Strings.stripColors(act.adminNickname())),
                                messageService.format("admin-action.delay", formatter.format(act.endTimestamp().atZone(ZoneId.systemDefault()))),
                                messageService.format("admin-action.reason", act.reason().orElse(messageService.get("admin-action.reason.unset")))
                                )))
                        .thenReturn(act)));
    }

    @DeleteMapping("/actions/{type}/{targetId}")
    public Mono<Void> delete(@PathVariable AdminActionType type, @PathVariable String targetId){
        return repository.deleteAllByTypeAndTargetId(type, targetId);
    }
}
