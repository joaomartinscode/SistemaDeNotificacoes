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
        Map<String, Object> payload
) {
    public NotificationDTO {
        recipients = recipients != null ? recipients : new ArrayList<>();
        if (payload == null) {
            payload = new HashMap<>();
            if (subject != null) {
                payload.put("subject", subject);
            }
        }
    }

    public NotificationDTO(String subject, String content, ChannelType channelType, List<RecipientDTO> recipients) {
        this(null, subject, content, channelType, null, null, null, null, recipients, null);
    }

    public static NotificationDTO fromEntity(Notification notification) {
        if (notification == null) {
            return null;
        }

        List<RecipientDTO> recipientDTOs = new ArrayList<>();
        if (notification.getRecipients() != null) {
            recipientDTOs.addAll(notification.getRecipients());
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
                notification.getPayload()
        );
    }

    public Notification toEntity() {
        Notification entity = new Notification();
        entity.setId(this.id);
        entity.setContent(this.content);
        entity.setChannelType(this.channelType);
        if (this.status != null) {
            entity.setStatus(this.status);
        }

        entity.setPayload(new HashMap<>(this.payload));
        if (this.subject != null) {
            entity.setSubject(this.subject);
        }

        if (this.recipients != null) {
            entity.setRecipients(new ArrayList<>(this.recipients));
        }
        return entity;
    }
}
