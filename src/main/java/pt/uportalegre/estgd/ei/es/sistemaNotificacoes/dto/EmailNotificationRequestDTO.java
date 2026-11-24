package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto;

import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.ChannelType;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.Priority;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EmailNotificationRequestDTO extends NotificationRequestDTO {

    private String to;
    private String cc;
    private String bcc;
    private String subject;

    public EmailNotificationRequestDTO() {
    }

    public EmailNotificationRequestDTO(
            ChannelType channelType,
            Priority priority,
            String to,
            String cc,
            String bcc,
            String subject,
            String content) {
        super(channelType != null ? channelType : ChannelType.EMAIL, priority, content);
        this.to = to;
        this.cc = cc;
        this.bcc = bcc;
        this.subject = subject;
    }

    public EmailNotificationRequestDTO(
            ChannelType channelType,
            Priority priority,
            String content,
            Map<String, String> params) {
        this(
                channelType,
                priority,
                params != null ? params.get("to") : null,
                params != null ? params.get("cc") : null,
                params != null ? params.get("bcc") : null,
                params != null ? params.get("subject") : null,
                content
        );
    }

    @Override
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
                getContent(),
                getChannelType(),
                getPriority(),
                recipientDTOs
        );
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getCc() {
        return cc;
    }

    public void setCc(String cc) {
        this.cc = cc;
    }

    public String getBcc() {
        return bcc;
    }

    public void setBcc(String bcc) {
        this.bcc = bcc;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
}
