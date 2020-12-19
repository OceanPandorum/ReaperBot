package reaperbot.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;
import reaperbot.AdminActionType;
import reaperbot.entity.AdminAction;

public interface AdminActionRepository extends ReactiveCrudRepository<AdminAction, String>{

    Flux<AdminAction> findByTypeAndTargetId(AdminActionType type, String targetId);
}
