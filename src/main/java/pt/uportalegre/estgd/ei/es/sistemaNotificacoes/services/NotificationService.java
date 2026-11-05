package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.services;

import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dao.NotificationRepository;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.DeliveryChannel;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.factory.DeliveryChannelFactory;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.*;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.NotificationDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DeliveryChannelFactory deliveryChannelFactory;

    public NotificationDTO processNotification(NotificationDTO dto) {
        Notification notification = dto.toEntity();
        notification.setStatus(DeliveryStatus.CREATED);

        Notification saved = notificationRepository.save(notification);

        return NotificationDTO.fromEntity(saved);
    }

    @Transactional
    public void sendPendingNotifications() {
        List<Notification> pendingNotifications = notificationRepository.findByStatus(List.of(DeliveryStatus.CREATED, DeliveryStatus.RETRYING));

        for (Notification notification : pendingNotifications) {
            submitNotification(notification);
            notificationRepository.save(notification);
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
        } catch (Exception e) {
            status = DeliveryStatus.FAILED;
            errorMessage = e.getMessage();

            notification.setSentAt(null);
            notification.setFailureReason(errorMessage);

            if (currentAttempt < notification.getMaxAttempts()) {
                notification.setStatus(DeliveryStatus.RETRYING);
            } else {
                notification.setStatus(DeliveryStatus.FAILED);
            }
        }

        NotificationAttempt attempt = new NotificationAttempt(notification, currentAttempt, status, errorMessage);
        notification.getAttemptsHistory().add(attempt);
    }

    @Transactional
    public void checkDeliveryStatus() {
        for (DeliveryChannel channel : deliveryChannelFactory.getDeliveryChannels()) {
            for (String messageId : channel.checkDeliveryStatus()) {
                handleDeliveryFailure(messageId, "Bounce: Delivery failure");
            }
        }
    }

    @Transactional
    public void handleDeliveryFailure(String trackingId, String reason) {
        Optional<Notification> optNotification = notificationRepository.findByMessageId(trackingId);

        if (optNotification.isPresent()) {
            Notification notification = optNotification.get();

            int attemptNumber = Math.max(1, notification.getAttempts());
            notification.setStatus(DeliveryStatus.FAILED);
            notification.setFailureReason(reason);

            NotificationAttempt attempt = new NotificationAttempt(
                    notification,
                    attemptNumber,
                    DeliveryStatus.FAILED,
                    reason
            );
            notification.getAttemptsHistory().add(attempt);

            notificationRepository.save(notification);
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
