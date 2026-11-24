package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto;

import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.ChannelType;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.Priority;

import java.util.List;
import java.util.Map;

public abstract class NotificationRequestDTO {

    private ChannelType channelType;
    private Priority priority;
    private String content;

    protected NotificationRequestDTO() {
    }

    protected NotificationRequestDTO(ChannelType channelType, Priority priority, String content) {
        this.channelType = channelType;
        this.priority = priority != null ? priority : Priority.NORMAL;
        this.content = content;
    }

    public static NotificationRequestDTO fromRequestParams(
            ChannelType channelType,
            Priority priority,
            String content,
            Map<String, String> params) {
        if (channelType == null) {
            throw new IllegalArgumentException("Channel type is required.");
        }

        return switch (channelType) {
            case EMAIL -> new EmailNotificationRequestDTO(channelType, priority, content, params);
        };
    }

    public abstract NotificationDTO toNotificationDTO();

    protected void addDestinations(List<RecipientDTO> list, String rawDestinations, String type) {
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

    public ChannelType getChannelType() {
        return channelType;
    }

    public void setChannelType(ChannelType channelType) {
        this.channelType = channelType;
    }

    public Priority getPriority() {
        return priority != null ? priority : Priority.NORMAL;
    }

    public void setPriority(Priority priority) {
        this.priority = priority != null ? priority : Priority.NORMAL;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
