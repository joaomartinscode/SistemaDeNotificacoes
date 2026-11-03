package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto;

import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.*;

import java.time.LocalDateTime;
import java.util.*;

public record NotificationDTO(
        Integer id,
        String subject,
        String content,
        ChannelType channelType,
        DeliveryStatus status,
        LocalDateTime createdAt,
        LocalDateTime sentAt,
        String failureReason,
        List<RecipientDTO> recipients,
        Map<String, Object> payload,
        Integer attempts,
        Integer maxAttempts,
        List<NotificationAttemptDTO> attemptsHistory
) {
    public NotificationDTO {
        recipients = recipients != null ? recipients : new ArrayList<>();
        if (payload == null) {
            payload = new HashMap<>();
            if (subject != null) {
                payload.put("subject", subject);
            }
        }

        attemptsHistory = attemptsHistory != null ? attemptsHistory : new ArrayList<>();
    }

    public NotificationDTO(String subject, String content, ChannelType channelType, List<RecipientDTO> recipients) {
        this(null, subject, content, channelType, null, null, null, null, recipients, null, 0, 3, null);
    }

    public static NotificationDTO fromEntity(Notification notification) {
        if (notification == null) {
            return null;
        }

        List<RecipientDTO> recipientDTOs = new ArrayList<>();
        if (notification.getRecipients() != null) {
            recipientDTOs.addAll(notification.getRecipients());
        }

        List<NotificationAttemptDTO> attemptsDTOs = new ArrayList<>();
        if (notification.getAttemptsHistory() != null) {
            for (NotificationAttempt attempt : notification.getAttemptsHistory()) {
                attemptsDTOs.add(NotificationAttemptDTO.fromEntity(attempt));
            }
        }

        return new NotificationDTO(
                notification.getId(),
                notification.getSubject(),
                notification.getContent(),
                notification.getChannelType(),
                notification.getStatus(),
                notification.getCreatedAt(),
                notification.getSentAt(),
                notification.getFailureReason(),
                recipientDTOs,
                notification.getPayload(),
                notification.getAttempts(),
                notification.getMaxAttempts(),
                attemptsDTOs
        );
    }

    public Notification toEntity() {
        Notification entity = new Notification(this.subject, this.content, this.channelType, this.recipients);
        if (this.id != null) {
            entity.setId(this.id);
        }
        if (this.status != null) {
            entity.setStatus(this.status);
        }
        if (this.payload != null) {
            entity.getPayload().putAll(this.payload);
        }
        return entity;
    }
}
