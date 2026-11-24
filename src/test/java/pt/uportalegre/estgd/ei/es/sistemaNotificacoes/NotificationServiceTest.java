package pt.uportalegre.estgd.ei.es.sistemaNotificacoes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dao.NotificationQueueRepository;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dao.NotificationRepository;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.DeliveryChannel;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.factory.DeliveryChannelFactory;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.*;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.NotificationDTO;
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
    private NotificationQueueRepository notificationQueueRepository;

    @Mock
    private DeliveryChannel emailChannel;

    @Mock
    private DeliveryChannelFactory deliveryChannelFactory;

    @InjectMocks
    private NotificationService notificationService;

    private Notification createNotificationTest(int id) {
        List<RecipientDTO> recipients = new ArrayList<>();
        recipients.add(new RecipientDTO("student@test.pt", "TO"));

        Notification notification = new Notification("Test Subject", "Test Content", ChannelType.EMAIL, recipients);
        notification.setId(id);
        notification.setMaxAttempts(3);
        notification.setAttempts(0);
        notification.setStatus(DeliveryStatus.CREATED);
        return notification;
    }

    private NotificationQueueItem createQueueItem(int queueId, Notification notification) {
        NotificationQueueItem item = new NotificationQueueItem(notification.getId(), notification.getPriority());
        item.setId(queueId);
        return item;
    }

    private void stubSingleReadyQueueItem(Notification notification, NotificationQueueItem item) {
        when(notificationQueueRepository.findNextReadyItems(
                eq(List.of(DeliveryStatus.CREATED, DeliveryStatus.RETRYING)), any(), any(), any()))
                .thenReturn(List.of(item));
        when(notificationQueueRepository.claimItem(
                eq(item.getId()),
                eq(List.of(DeliveryStatus.CREATED, DeliveryStatus.RETRYING)),
                eq(DeliveryStatus.PROCESSING),
                any()))
                .thenReturn(1);
        when(notificationRepository.findByIdWithAttempts(notification.getId()))
                .thenReturn(Optional.of(notification));
    }

    @Test
    void shouldSendNotificationSuccessfullyAndRemoveFromQueue() {
        Notification notification = createNotificationTest(1);
        NotificationQueueItem item = createQueueItem(100, notification);
        stubSingleReadyQueueItem(notification, item);
        when(deliveryChannelFactory.getDeliveryChannel(ChannelType.EMAIL)).thenReturn(emailChannel);
        doNothing().when(emailChannel).send(notification);

        notificationService.processNextQueueItem();

        assertEquals(DeliveryStatus.SENT, notification.getStatus());
        assertNotNull(notification.getSentAt());
        assertNull(notification.getFailureReason());
        assertEquals(1, notification.getAttempts());
        assertEquals(1, notification.getAttemptsHistory().size());
        assertEquals(DeliveryStatus.SENT, notification.getAttemptsHistory().getFirst().getStatus());

        verify(emailChannel).send(notification);
        verify(notificationRepository).save(notification);
        verify(notificationQueueRepository).delete(item);
    }

    @Test
    void shouldRetryAndSucceedAfterInitialFailure() {
        Notification notification = createNotificationTest(2);
        NotificationQueueItem item = createQueueItem(101, notification);
        stubSingleReadyQueueItem(notification, item);
        when(deliveryChannelFactory.getDeliveryChannel(ChannelType.EMAIL)).thenReturn(emailChannel);

        doThrow(new RuntimeException("Network failure"))
                .doNothing()
                .when(emailChannel).send(notification);

        notificationService.processNextQueueItem();

        assertEquals(DeliveryStatus.RETRYING, notification.getStatus());
        assertEquals(1, notification.getAttempts());
        assertEquals("Network failure", notification.getFailureReason());
        assertNull(notification.getSentAt());
        assertEquals(DeliveryStatus.RETRYING, item.getStatus());
        assertNull(item.getLockedAt());
        verify(notificationQueueRepository).save(item);

        notificationService.processNextQueueItem();

        assertEquals(DeliveryStatus.SENT, notification.getStatus());
        assertEquals(2, notification.getAttempts());
        assertNull(notification.getFailureReason());
        assertNotNull(notification.getSentAt());
        assertEquals(2, notification.getAttemptsHistory().size());
        assertEquals(DeliveryStatus.SENT, notification.getAttemptsHistory().getLast().getStatus());
        verify(notificationQueueRepository).delete(item);
    }

    @Test
    void shouldKeepStatusAsRetryingAfterConsecutiveFailures() {
        Notification notification = createNotificationTest(3);
        NotificationQueueItem item = createQueueItem(102, notification);
        stubSingleReadyQueueItem(notification, item);
        when(deliveryChannelFactory.getDeliveryChannel(ChannelType.EMAIL)).thenReturn(emailChannel);

        doThrow(new RuntimeException("Connection error")).when(emailChannel).send(notification);

        notificationService.processNextQueueItem();
        assertEquals(DeliveryStatus.RETRYING, notification.getStatus());
        assertEquals(1, notification.getAttempts());

        notificationService.processNextQueueItem();
        assertEquals(DeliveryStatus.RETRYING, notification.getStatus());
        assertEquals(2, notification.getAttempts());
        assertEquals(2, notification.getAttemptsHistory().size());
        verify(notificationQueueRepository, times(2)).save(item);
        verify(notificationQueueRepository, never()).delete(item);
    }

    @Test
    void shouldMarkAsFailedWhenMaxAttemptsReachedAndDeleteFromQueue() {
        Notification notification = createNotificationTest(4);
        notification.setMaxAttempts(3);
        NotificationQueueItem item = createQueueItem(103, notification);
        stubSingleReadyQueueItem(notification, item);
        when(deliveryChannelFactory.getDeliveryChannel(ChannelType.EMAIL)).thenReturn(emailChannel);

        doThrow(new RuntimeException("Connection error")).when(emailChannel).send(notification);

        notificationService.processNextQueueItem();
        notificationService.processNextQueueItem();
        notificationService.processNextQueueItem();

        assertEquals(DeliveryStatus.FAILED, notification.getStatus());
        assertEquals(3, notification.getAttempts());
        assertEquals("Connection error", notification.getFailureReason());
        assertEquals(3, notification.getAttemptsHistory().size());
        assertEquals(DeliveryStatus.FAILED, notification.getAttemptsHistory().getLast().getStatus());
        verify(notificationQueueRepository, times(2)).save(item);
        verify(notificationQueueRepository).delete(item);
    }

    @Test
    void shouldMarkNotificationAsFailedWhenBounceIsDetected() {
        Notification notification = createNotificationTest(5);
        notification.setStatus(DeliveryStatus.SENT);
        notification.setAttempts(1);
        notification.setSentAt(LocalDateTime.now());

        String trackingId = "<bounce-12345@domain.pt>";
        notification.getPayload().put("messageId", trackingId);

        when(notificationRepository.findSentNotificationsSince(eq(DeliveryStatus.SENT), any(LocalDateTime.class)))
                .thenReturn(List.of(notification));
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
        verify(notificationRepository).save(notification);
    }

    @Test
    void shouldSaveFailedRecipientsWhenBounceIncludesXFailedRecipients() {
        Notification notification = createNotificationTest(6);
        notification.setStatus(DeliveryStatus.SENT);
        notification.setAttempts(1);
        notification.setSentAt(LocalDateTime.now());

        String trackingId = "<bounce-999@domain.pt>";
        notification.getPayload().put("messageId", trackingId);

        when(notificationRepository.findSentNotificationsSince(eq(DeliveryStatus.SENT), any(LocalDateTime.class)))
                .thenReturn(List.of(notification));
        when(deliveryChannelFactory.getDeliveryChannels()).thenReturn(List.of(emailChannel));
        when(emailChannel.supports(notification.getChannelType())).thenReturn(true);
        when(emailChannel.checkDeliveryStatus(List.of(trackingId))).thenReturn(List.of(trackingId));
        when(emailChannel.getFailedRecipients(trackingId)).thenReturn(List.of("bananas@uportalegre.pt"));
        when(notificationRepository.findByMessageId(trackingId)).thenReturn(Optional.of(notification));

        notificationService.checkDeliveryStatus();

        assertEquals(DeliveryStatus.FAILED, notification.getStatus());
        assertEquals("Bounce: Delivery failure (Failed recipients: bananas@uportalegre.pt)", notification.getFailureReason());
        assertEquals(List.of("bananas@uportalegre.pt"), notification.getPayload().get("failedRecipients"));
        verify(notificationRepository).save(notification);
    }

    @Test
    void shouldSkipQueueItemIfAlreadyClaimedByAnotherWorker() {
        Notification notification = createNotificationTest(20);
        NotificationQueueItem item = createQueueItem(200, notification);

        when(notificationQueueRepository.findNextReadyItems(
                eq(List.of(DeliveryStatus.CREATED, DeliveryStatus.RETRYING)), any(), any(), any()))
                .thenReturn(List.of(item));
        when(notificationQueueRepository.claimItem(
                eq(200),
                eq(List.of(DeliveryStatus.CREATED, DeliveryStatus.RETRYING)),
                eq(DeliveryStatus.PROCESSING),
                any()))
                .thenReturn(0);

        notificationService.processNextQueueItem();

        verify(emailChannel, never()).send(any());
        verify(notificationRepository, never()).findByIdWithAttempts(any());
    }

    @Test
    void shouldRequeryHighestPriorityItemBetweenEachSendInCycle() {
        Notification low = createNotificationTest(30);
        low.setPriority(Priority.LOW);
        NotificationQueueItem lowItem = createQueueItem(300, low);

        Notification high = createNotificationTest(31);
        high.setPriority(Priority.HIGH);
        NotificationQueueItem highItem = createQueueItem(301, high);

        when(notificationQueueRepository.findNextReadyItems(
                eq(List.of(DeliveryStatus.CREATED, DeliveryStatus.RETRYING)), any(), any(), eq(org.springframework.data.domain.PageRequest.of(0, 1))))
                .thenReturn(List.of(lowItem), List.of(highItem), List.of());

        when(notificationQueueRepository.claimItem(eq(300), any(), eq(DeliveryStatus.PROCESSING), any())).thenReturn(1);
        when(notificationQueueRepository.claimItem(eq(301), any(), eq(DeliveryStatus.PROCESSING), any())).thenReturn(1);
        when(notificationRepository.findByIdWithAttempts(30)).thenReturn(Optional.of(low));
        when(notificationRepository.findByIdWithAttempts(31)).thenReturn(Optional.of(high));
        when(deliveryChannelFactory.getDeliveryChannel(ChannelType.EMAIL)).thenReturn(emailChannel);

        notificationService.processQueue();

        var inOrder = inOrder(emailChannel);
        inOrder.verify(emailChannel).send(low);
        inOrder.verify(emailChannel).send(high);
    }

    @Test
    void shouldRecoverLockedQueueItemsOnStartup() {
        notificationService.onStartup();

        verify(notificationQueueRepository).recoverAllLocked(DeliveryStatus.PROCESSING, DeliveryStatus.RETRYING);
    }

    @Test
    void shouldRecoverStaleLockedQueueItemsDuringRuntime() {
        notificationService.recoverStaleQueueItems();

        verify(notificationQueueRepository).recoverStaleLocked(
                eq(DeliveryStatus.PROCESSING),
                eq(DeliveryStatus.RETRYING),
                any(LocalDateTime.class)
        );
    }

    @Test
    void shouldSaveAndEnqueueNotificationWhenCreated() {
        Notification saved = createNotificationTest(99);
        saved.setPriority(Priority.HIGH);

        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);
        when(notificationQueueRepository.save(any(NotificationQueueItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationDTO dto = new NotificationDTO(
                "Test Subject",
                "Test Content",
                ChannelType.EMAIL,
                Priority.HIGH,
                List.of(new RecipientDTO("student@test.pt", "TO"))
        );
        NotificationDTO result = notificationService.processNotification(dto);

        assertNotNull(result);
        assertEquals(99, result.id());
        assertEquals(DeliveryStatus.CREATED, result.status());
        verify(notificationQueueRepository).save(argThat(q ->
                q.getNotificationId().equals(99)
                        && q.getPriority() == Priority.HIGH
                        && q.getStatus() == DeliveryStatus.CREATED
        ));
    }
}
