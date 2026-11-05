package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto;

import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.ChannelType;

import java.util.*;

public record EmailNotificationRequestDTO(
        ChannelType channelType,
        String to,
        String cc,
        String bcc,
        String subject,
        String content
) {
    public NotificationDTO toNotificationDTO() {
        List<RecipientDTO> recipientDTOs = new ArrayList<>();
        addDestinations(recipientDTOs, this.to, "TO");
        addDestinations(recipientDTOs, this.cc, "CC");
        addDestinations(recipientDTOs, this.bcc, "BCC");

        if (recipientDTOs.isEmpty()) {
            throw new IllegalArgumentException("Notification must have at least one recipient.");
        }

        return new NotificationDTO(
                this.subject,
                this.content,
                this.channelType,
                recipientDTOs
        );
    }

    private void addDestinations(List<RecipientDTO> list, String rawDestinations, String type) {
        if (rawDestinations == null || rawDestinations.trim().isEmpty()) {
            return;
        }
        for (String dest : rawDestinations.split(",")) {
            String clean = dest.trim();
            if (!clean.isEmpty()) {
                list.add(new RecipientDTO(clean, type));
            }
        }
    }
}
