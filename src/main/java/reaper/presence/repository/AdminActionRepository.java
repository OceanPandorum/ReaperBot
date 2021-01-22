package reaper.presence.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;
import reaper.presence.AdminActionType;
import reaper.presence.entity.AdminAction;

public interface AdminActionRepository extends ReactiveCrudRepository<AdminAction, String>{

    Mono<AdminAction> findByTypeAndTargetId(AdminActionType type, String targetId);

    Mono<Boolean> existsByTypeAndTargetId(AdminActionType type, String targetId);

    Flux<AdminAction> findAllByType(AdminActionType type);

    Mono<Void> deleteAllByTypeAndTargetId(AdminActionType type, String targetId);
}
