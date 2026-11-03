package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class NotificationAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "attempt_date", nullable = false)
    private LocalDateTime attemptDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public NotificationAttempt() {
    }

    public NotificationAttempt(Notification notification, Integer attemptNumber, DeliveryStatus status, String errorMessage) {
        this.notification = notification;
        this.attemptNumber = attemptNumber;
        this.attemptDate = LocalDateTime.now();
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Notification getNotification() {
        return notification;
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public LocalDateTime getAttemptDate() {
        return attemptDate;
    }

    public void setAttemptDate(LocalDateTime attemptDate) {
        this.attemptDate = attemptDate;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
