package reaper.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;
import reaper.AdminActionType;
import reaper.entity.AdminAction;

public interface AdminActionRepository extends ReactiveCrudRepository<AdminAction, String>{

    Flux<AdminAction> findByTypeAndTargetId(AdminActionType type, String targetId);
}
