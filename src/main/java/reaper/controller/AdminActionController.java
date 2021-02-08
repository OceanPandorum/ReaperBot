package reaper.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.*;
import reactor.util.*;
import reaper.presence.AdminActionType;
import reaper.presence.entity.AdminAction;
import reaper.presence.repository.AdminActionRepository;

@RestController
@RequestMapping("/api/v1")
public class AdminActionController{
    private static final Logger log = Loggers.getLogger(AdminActionController.class);

    private final AdminActionRepository repository;

    public AdminActionController(@Autowired AdminActionRepository repository){
        this.repository = repository;
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
        return action.hasElement().flatMap(bool -> bool ? action.flatMap(a -> repository.save(a.plusEndTimestamp(adminAction.endTimestamp()))) : repository.save(adminAction));
    }

    @DeleteMapping("/actions/{type}/{targetId}")
    public Mono<Void> delete(@PathVariable AdminActionType type, @PathVariable String targetId){
        return repository.deleteAllByTypeAndTargetId(type, targetId);
    }
}
