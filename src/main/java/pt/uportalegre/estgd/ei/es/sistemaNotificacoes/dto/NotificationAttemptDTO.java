package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto;

import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.DeliveryStatus;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.NotificationAttempt;

import java.time.LocalDateTime;

public record NotificationAttemptDTO(
        Integer id,
        Integer attemptNumber,
        LocalDateTime attemptDate,
        DeliveryStatus status,
        String errorMessage
) {
    public static NotificationAttemptDTO fromEntity(NotificationAttempt attempt) {
        if (attempt == null) return null;
        
        return new NotificationAttemptDTO(
                attempt.getId(),
                attempt.getAttemptNumber(),
                attempt.getAttemptDate(),
                attempt.getStatus(),
                attempt.getErrorMessage()
        );
    }
}
