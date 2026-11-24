package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dao.NotificationQueueRepository;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dao.NotificationRepository;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.DeliveryChannel;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.factory.DeliveryChannelFactory;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.*;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.NotificationDTO;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ITEMS_PER_CYCLE = 25;
    private static final long RETRY_DELAY_SECONDS = 10L;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationQueueRepository notificationQueueRepository;

    @Autowired
    private DeliveryChannelFactory deliveryChannelFactory;

    @Transactional
    public NotificationDTO processNotification(NotificationDTO dto) {
        Notification notification = dto.toEntity();
        notification.setStatus(DeliveryStatus.CREATED);

        Notification saved = notificationRepository.save(notification);
        NotificationQueueItem queueItem = new NotificationQueueItem(saved.getId(), saved.getPriority());
        notificationQueueRepository.save(queueItem);

        log.info("Notification #{} saved and enqueued (priority={})", saved.getId(), saved.getPriority());
        return NotificationDTO.fromEntity(saved);
    }

    public void processQueue() {
        recoverStaleQueueItems();

        for (int i = 0; i < MAX_ITEMS_PER_CYCLE; i++) {
            if (!processNextQueueItem()) {
                break;
            }
        }
    }

    public boolean processNextQueueItem() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime agingThreshold = now.minusMinutes(5);

        List<NotificationQueueItem> nextItems = notificationQueueRepository.findNextReadyItems(
                List.of(DeliveryStatus.CREATED, DeliveryStatus.RETRYING),
                now,
                agingThreshold,
                PageRequest.of(0, 1)
        );

        if (nextItems == null || nextItems.isEmpty()) {
            return false;
        }

        processQueueItem(nextItems.getFirst());
        return true;
    }

    public void processQueueItem(NotificationQueueItem item) {
        int claimed = notificationQueueRepository.claimItem(
                item.getId(),
                List.of(DeliveryStatus.CREATED, DeliveryStatus.RETRYING),
                DeliveryStatus.PROCESSING,
                LocalDateTime.now()
        );
        if (claimed == 0) {
            return;
        }

        Optional<Notification> optNotification = notificationRepository.findByIdWithAttempts(item.getNotificationId());
        if (optNotification.isEmpty()) {
            log.warn("Queue item #{} references missing notification #{}; removing from queue",
                    item.getId(), item.getNotificationId());
            notificationQueueRepository.delete(item);
            return;
        }

        Notification notification = optNotification.get();
        submitNotification(notification);
        notification.setUpdatedAt(LocalDateTime.now());
        notificationRepository.save(notification);

        if (notification.getStatus() == DeliveryStatus.RETRYING) {
            item.setStatus(DeliveryStatus.RETRYING);
            item.setLockedAt(null);
            item.setNextAttemptAt(LocalDateTime.now().plusSeconds(RETRY_DELAY_SECONDS));
            notificationQueueRepository.save(item);
            log.info("Notification #{} re-queued for retry after {}s", notification.getId(), RETRY_DELAY_SECONDS);
        } else {
            notificationQueueRepository.delete(item);
        }
    }

    public void recoverStaleQueueItems() {
        LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(5);
        int recovered = notificationQueueRepository.recoverStaleLocked(
                DeliveryStatus.PROCESSING,
                DeliveryStatus.RETRYING,
                staleThreshold
        );
        if (recovered > 0) {
            log.warn("Recovered {} stale queue item(s) back to RETRYING", recovered);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        int recovered = notificationQueueRepository.recoverAllLocked(DeliveryStatus.PROCESSING, DeliveryStatus.RETRYING);
        if (recovered > 0) {
            log.info("Startup recovery: reset {} interrupted queue item(s) to RETRYING", recovered);
        }
    }

    private void submitNotification(Notification notification) {
        int currentAttempt = notification.getAttempts() + 1;
        notification.setAttempts(currentAttempt);

        DeliveryStatus status;
        String errorMessage = null;

        try {
            DeliveryChannel channel = deliveryChannelFactory.getDeliveryChannel(notification.getChannelType());
            channel.send(notification);

            notification.setStatus(DeliveryStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            notification.setFailureReason(null);
            status = DeliveryStatus.SENT;
            log.info("Notification #{} sent successfully on attempt {}", notification.getId(), currentAttempt);
        } catch (Exception e) {
            status = DeliveryStatus.FAILED;
            errorMessage = e.getMessage();

            notification.setSentAt(null);
            notification.setFailureReason(errorMessage);

            if (currentAttempt < notification.getMaxAttempts()) {
                notification.setStatus(DeliveryStatus.RETRYING);
                log.warn("Notification #{} failed on attempt {}/{}. Marked as RETRYING. Reason: {}",
                        notification.getId(), currentAttempt, notification.getMaxAttempts(), errorMessage);
            } else {
                notification.setStatus(DeliveryStatus.FAILED);
                log.error("Notification #{} failed permanently after {} attempts. Reason: {}",
                        notification.getId(), currentAttempt, errorMessage);
            }
        }

        NotificationAttempt attempt = new NotificationAttempt(notification, currentAttempt, status, errorMessage);
        notification.getAttemptsHistory().add(attempt);
    }

    @Transactional
    public void checkDeliveryStatus() {
        LocalDateTime window = LocalDateTime.now().minusMinutes(15);
        List<Notification> sentNotifications = notificationRepository.findSentNotificationsSince(DeliveryStatus.SENT, window);

        for (DeliveryChannel channel : deliveryChannelFactory.getDeliveryChannels()) {
            List<String> messageIds = new ArrayList<>();
            for (Notification notification : sentNotifications) {
                if (channel.supports(notification.getChannelType())) {
                    if (notification.getPayload() != null && notification.getPayload().get("messageId") != null) {
                        messageIds.add(notification.getPayload().get("messageId").toString());
                    }
                }
            }

            if (!messageIds.isEmpty()) {
                for (String messageId : channel.checkDeliveryStatus(messageIds)) {
                    List<String> failedRecipients = channel.getFailedRecipients(messageId);
                    handleDeliveryFailure(messageId, "Bounce: Delivery failure", failedRecipients);
                }
            }
        }
    }

    @Transactional
    public void handleDeliveryFailure(String trackingId, String reason) {
        handleDeliveryFailure(trackingId, reason, List.of());
    }

    @Transactional
    public void handleDeliveryFailure(String trackingId, String reason, List<String> failedRecipients) {
        Optional<Notification> optNotification = notificationRepository.findByMessageId(trackingId);

        if (optNotification.isPresent()) {
            Notification notification = optNotification.get();

            String finalReason = reason;
            if (failedRecipients != null && !failedRecipients.isEmpty()) {
                notification.getPayload().put("failedRecipients", new ArrayList<>(failedRecipients));
                finalReason = reason + " (Failed recipients: " + String.join(", ", failedRecipients) + ")";
            }

            int attemptNumber = Math.max(1, notification.getAttempts());
            notification.setStatus(DeliveryStatus.FAILED);
            notification.setFailureReason(finalReason);

            NotificationAttempt attempt = new NotificationAttempt(
                    notification,
                    attemptNumber,
                    DeliveryStatus.FAILED,
                    finalReason
            );
            notification.getAttemptsHistory().add(attempt);

            notificationRepository.save(notification);
            log.warn("Notification #{} marked as FAILED due to bounce (messageId={}, failedRecipients={})",
                    notification.getId(), trackingId, failedRecipients);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> findAll() {
        List<NotificationDTO> dtos = new ArrayList<>();
        for (Notification notification : notificationRepository.findAllOrderedByCreatedAtDesc()) {
            dtos.add(NotificationDTO.fromEntity(notification));
        }
        return dtos;
    }

    @Transactional(readOnly = true)
    public NotificationDTO findById(Integer id) {
        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification == null) {
            throw new NoSuchElementException("Notification not found with ID: " + id);
        }
        return NotificationDTO.fromEntity(notification);
    }
}
