package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dao;

import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.DeliveryStatus;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.Notification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends CrudRepository<Notification, Integer> {

    @Query("SELECT n FROM Notification n ORDER BY n.createdAt DESC")
    List<Notification> findAllOrderedByCreatedAtDesc();

    @Query("SELECT n FROM Notification n WHERE n.status IN ?1 ORDER BY n.createdAt ASC")
    List<Notification> findByStatus(List<DeliveryStatus> status);

    @Query("SELECT n FROM Notification n WHERE n.status = :status AND n.sentAt >= :since")
    List<Notification> findSentNotificationsSince(@Param("status") DeliveryStatus status, @Param("since") LocalDateTime since);

    @Query(value = "SELECT * FROM notification n WHERE n.payload LIKE CONCAT('%', ?1, '%') LIMIT 1", nativeQuery = true)
    Optional<Notification> findByMessageId(String messageId);
}
