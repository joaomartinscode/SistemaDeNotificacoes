package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.RecipientDTO;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.utils.MyList;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

@Entity
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> payload = new HashMap<>();

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelType channelType;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Transient
    private MyList<RecipientDTO> recipients;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(nullable = false)
    private Integer maxAttempts = 3;

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotificationAttempt> attemptsHistory = new ArrayList<>();

    public Notification() {
        this.status = DeliveryStatus.CREATED;
        this.createdAt = LocalDateTime.now();
    }

    public Notification(String subject, String content, ChannelType channelType, List<RecipientDTO> recipients) {
        this();
        this.content = content;
        this.channelType = channelType;
        setSubject(subject);
        if (recipients != null) {
            setRecipients(recipients);
        }
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Map<String, Object> getPayload() {
        if (this.payload == null) {
            this.payload = new HashMap<>();
        }
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload != null ? payload : new HashMap<>();
        this.recipients = null;
    }

    public String getSubject() {
        if (this.payload != null) {
            Object s = this.payload.get("subject");
            if (s != null) {
                return s.toString();
            }
        }
        return null;
    }

    public void setSubject(String subject) {
        if (this.payload == null) {
            this.payload = new HashMap<>();
        }
        if (subject != null) {
            this.payload.put("subject", subject);
        } else {
            this.payload.remove("subject");
        }
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public ChannelType getChannelType() {
        return channelType;
    }

    public void setChannelType(ChannelType channelType) {
        this.channelType = channelType;
    }

    public String getRecipientsJson() {
        if (this.payload == null || !this.payload.containsKey("recipients")) {
            return null;
        }
        Object r = this.payload.get("recipients");
        if (r == null) {
            return null;
        }
        if (r instanceof String s) {
            return s;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(r);
        } catch (Exception e) {
            return null;
        }
    }

    public void setRecipientsJson(String recipientsJson) {
        if (this.payload == null) {
            this.payload = new HashMap<>();
        }
        if (recipientsJson == null || recipientsJson.isBlank()) {
            this.payload.remove("recipients");
        } else {
            try {
                Object parsed = OBJECT_MAPPER.readValue(recipientsJson, Object.class);
                this.payload.put("recipients", parsed);
            } catch (Exception e) {
                this.payload.put("recipients", recipientsJson);
            }
        }
    }

    public List<RecipientDTO> getRecipients() {
        if (this.recipients == null) {
            this.recipients = new MyList<>(this::getRecipientsJson, this::setRecipientsJson, RecipientDTO.class);
        }
        return recipients;
    }

    public void setRecipients(List<RecipientDTO> recipients) {
        getRecipients().clear();
        if (recipients != null) {
            getRecipients().addAll(recipients);
        }
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Integer getAttempts() { return attempts; }

    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public Integer getMaxAttempts() { return maxAttempts; }

    public void setMaxAttempts(Integer maxAttempts) { this.maxAttempts = maxAttempts; }

    public List<NotificationAttempt> getAttemptsHistory() {
        return attemptsHistory;
    }

    public void setAttemptsHistory(List<NotificationAttempt> attemptsHistory) {
        this.attemptsHistory = attemptsHistory;
    }
}
