package pt.uportalegre.estgd.ei.es.sistemaNotificacoes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dao.NotificationRepository;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.DeliveryChannel;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.factory.DeliveryChannelFactory;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.ChannelType;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.DeliveryStatus;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.Notification;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.RecipientDTO;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.services.NotificationService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private DeliveryChannel emailChannel;

    @Mock
    private DeliveryChannelFactory deliveryChannelFactory;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setup() {
    }

    private Notification createNotificationTest() {
        List<RecipientDTO> recipients = new ArrayList<>();
        recipients.add(new RecipientDTO("student@test.pt", "TO"));

        Notification notification = new Notification("Test Subject", "Test Content", ChannelType.EMAIL, recipients);
        notification.setMaxAttempts(3);
        notification.setAttempts(0);
        notification.setStatus(DeliveryStatus.CREATED);
        return notification;
    }

    @Test
    void shouldSendNotificationSuccessfully() {
        Notification notification = createNotificationTest();
        when(deliveryChannelFactory.getDeliveryChannel(ChannelType.EMAIL)).thenReturn(emailChannel);
        when(notificationRepository.findByStatus(any())).thenReturn(List.of(notification));
        doNothing().when(emailChannel).send(notification);

        notificationService.sendPendingNotifications();

        assertEquals(DeliveryStatus.SENT, notification.getStatus());
        assertNotNull(notification.getSentAt());
        assertNull(notification.getFailureReason());
        assertEquals(1, notification.getAttempts());
        assertEquals(1, notification.getAttemptsHistory().size());
        assertEquals(DeliveryStatus.SENT, notification.getAttemptsHistory().getFirst().getStatus());

        verify(emailChannel).send(notification);
        verify(notificationRepository).save(notification);
    }

    @Test
    void shouldRetryAndSucceedAfterInitialFailure() {
        Notification notification = createNotificationTest();
        when(deliveryChannelFactory.getDeliveryChannel(ChannelType.EMAIL)).thenReturn(emailChannel);
        when(notificationRepository.findByStatus(any())).thenReturn(List.of(notification));

        doThrow(new RuntimeException("Network failure"))
                .doNothing()
                .when(emailChannel).send(notification);

        notificationService.sendPendingNotifications();

        assertEquals(DeliveryStatus.RETRYING, notification.getStatus());
        assertEquals(1, notification.getAttempts());
        assertEquals("Network failure", notification.getFailureReason());
        assertNull(notification.getSentAt());

        notificationService.sendPendingNotifications();

        assertEquals(DeliveryStatus.SENT, notification.getStatus());
        assertEquals(2, notification.getAttempts());
        assertNull(notification.getFailureReason());
        assertNotNull(notification.getSentAt());
        assertEquals(2, notification.getAttemptsHistory().size());
        assertEquals(DeliveryStatus.SENT, notification.getAttemptsHistory().getLast().getStatus());
    }

    @Test
    void shouldKeepStatusAsRetryingAfterConsecutiveFailures() {
        Notification notification = createNotificationTest();
        when(deliveryChannelFactory.getDeliveryChannel(ChannelType.EMAIL)).thenReturn(emailChannel);
        when(notificationRepository.findByStatus(any())).thenReturn(List.of(notification));

        doThrow(new RuntimeException("Connection error")).when(emailChannel).send(notification);

        notificationService.sendPendingNotifications();
        assertEquals(DeliveryStatus.RETRYING, notification.getStatus());
        assertEquals(1, notification.getAttempts());

        notificationService.sendPendingNotifications();
        assertEquals(DeliveryStatus.RETRYING, notification.getStatus());
        assertEquals(2, notification.getAttempts());
        assertEquals(2, notification.getAttemptsHistory().size());
    }

    @Test
    void shouldMarkAsFailedWhenMaxAttemptsReached() {
        Notification notification = createNotificationTest();
        notification.setMaxAttempts(3);
        when(deliveryChannelFactory.getDeliveryChannel(ChannelType.EMAIL)).thenReturn(emailChannel);
        when(notificationRepository.findByStatus(any())).thenReturn(List.of(notification));

        doThrow(new RuntimeException("Connection error")).when(emailChannel).send(notification);

        notificationService.sendPendingNotifications();
        notificationService.sendPendingNotifications();
        notificationService.sendPendingNotifications();

        assertEquals(DeliveryStatus.FAILED, notification.getStatus());
        assertEquals(3, notification.getAttempts());
        assertEquals("Connection error", notification.getFailureReason());
        assertEquals(3, notification.getAttemptsHistory().size());
        assertEquals(DeliveryStatus.FAILED, notification.getAttemptsHistory().getLast().getStatus());
    }

    @Test
    void shouldMarkNotificationAsFailedWhenBounceIsDetected() {
        Notification notification = createNotificationTest();
        notification.setStatus(DeliveryStatus.SENT);
        notification.setAttempts(1);
        notification.setSentAt(LocalDateTime.now());

        String trackingId = "<bounce-12345@domain.pt>";
        notification.getPayload().put("messageId", trackingId);

        when(notificationRepository.findSentNotificationsSince(eq(DeliveryStatus.SENT), any(LocalDateTime.class))).thenReturn(List.of(notification));
        when(deliveryChannelFactory.getDeliveryChannels()).thenReturn(List.of(emailChannel));
        when(emailChannel.supports(notification.getChannelType())).thenReturn(true);
        when(emailChannel.checkDeliveryStatus(List.of(trackingId))).thenReturn(List.of(trackingId));

        when(notificationRepository.findByMessageId(trackingId)).thenReturn(Optional.of(notification));

        notificationService.checkDeliveryStatus();

        assertEquals(DeliveryStatus.FAILED, notification.getStatus());
        assertEquals("Bounce: Delivery failure", notification.getFailureReason());
        assertEquals(1, notification.getAttempts());
        assertEquals(1, notification.getAttemptsHistory().size());
        assertEquals(DeliveryStatus.FAILED, notification.getAttemptsHistory().getLast().getStatus());
        assertEquals("Bounce: Delivery failure", notification.getAttemptsHistory().getLast().getErrorMessage());

        verify(notificationRepository).save(notification);
    }
}
