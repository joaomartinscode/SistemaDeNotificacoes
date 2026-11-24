package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dao;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.DeliveryStatus;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.NotificationQueueItem;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationQueueRepository extends CrudRepository<NotificationQueueItem, Integer> {

    @Query("SELECT q FROM NotificationQueueItem q " +
            "WHERE q.status IN :statuses AND q.nextAttemptAt <= :now " +
            "ORDER BY " +
            "CASE " +
            "  WHEN q.createdAt <= :agingThreshold THEN 4 " +
            "  WHEN q.priority = pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.Priority.HIGH THEN 3 " +
            "  WHEN q.priority = pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.Priority.NORMAL THEN 2 " +
            "  ELSE 1 " +
            "END DESC, q.createdAt ASC")
    List<NotificationQueueItem> findNextReadyItems(
            @Param("statuses") List<DeliveryStatus> statuses,
            @Param("now") LocalDateTime now,
            @Param("agingThreshold") LocalDateTime agingThreshold,
            Pageable pageable
    );

    @Modifying
    @Transactional
    @Query("UPDATE NotificationQueueItem q SET q.status = :newStatus, q.lockedAt = :now " +
            "WHERE q.id = :id AND q.status IN :expectedStatuses")
    int claimItem(
            @Param("id") Integer id,
            @Param("expectedStatuses") List<DeliveryStatus> expectedStatuses,
            @Param("newStatus") DeliveryStatus newStatus,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Transactional
    @Query("UPDATE NotificationQueueItem q SET q.status = :toStatus, q.lockedAt = null " +
            "WHERE q.status = :fromStatus")
    int recoverAllLocked(
            @Param("fromStatus") DeliveryStatus fromStatus,
            @Param("toStatus") DeliveryStatus toStatus
    );

    @Modifying
    @Transactional
    @Query("UPDATE NotificationQueueItem q SET q.status = :toStatus, q.lockedAt = null " +
            "WHERE q.status = :fromStatus AND q.lockedAt <= :staleThreshold")
    int recoverStaleLocked(
            @Param("fromStatus") DeliveryStatus fromStatus,
            @Param("toStatus") DeliveryStatus toStatus,
            @Param("staleThreshold") LocalDateTime staleThreshold
    );
}
