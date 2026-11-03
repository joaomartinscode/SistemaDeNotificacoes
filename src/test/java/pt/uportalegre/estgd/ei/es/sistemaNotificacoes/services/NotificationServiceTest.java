package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dao.NotificationRepository;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.DeliveryChannel;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.ChannelType;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.DeliveryStatus;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.Notification;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.RecipientDTO;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private DeliveryChannel emailChannel;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService();
        ReflectionTestUtils.setField(notificationService, "notificationRepository", notificationRepository);
        ReflectionTestUtils.setField(notificationService, "deliveryChannels", List.of(emailChannel));

        when(emailChannel.supports(ChannelType.EMAIL)).thenReturn(true);
    }

    private Notification criarNotificacaoTeste() {
        List<RecipientDTO> recipients = new ArrayList<>();
        recipients.add(new RecipientDTO("teste@exemplo.pt", "TO"));
        return new Notification("Assunto Teste", "Conteudo Teste", ChannelType.EMAIL, recipients);
    }

    @Test
    void test1_EnvioBemSucedido() {
        Notification notification = criarNotificacaoTeste();
        when(notificationRepository.findByStatus(any())).thenReturn(List.of(notification));
        doNothing().when(emailChannel).send(any(Notification.class));

        notificationService.sendPendingNotifications();

        assertEquals(DeliveryStatus.SENT, notification.getStatus());
        assertNotNull(notification.getSentAt());
        assertEquals(1, notification.getAttempts());
        assertEquals(1, notification.getAttemptsHistory().size());
        assertEquals(DeliveryStatus.SENT, notification.getAttemptsHistory().get(0).getStatus());
        assertNull(notification.getFailureReason());
        verify(emailChannel, times(1)).send(notification);
    }

    @Test
    void test2_FalhaSeguidaDeTentativaBemSucedida() {
        Notification notification = criarNotificacaoTeste();
        when(notificationRepository.findByStatus(any())).thenReturn(List.of(notification));

        // 1ª tentativa: Falha
        doThrow(new RuntimeException("Servidor de email indisponível")).when(emailChannel).send(notification);
        notificationService.sendPendingNotifications();

        assertEquals(DeliveryStatus.RETRYING, notification.getStatus());
        assertEquals(1, notification.getAttempts());
        assertEquals("Servidor de email indisponível", notification.getFailureReason());

        // 2ª tentativa: Sucesso
        doNothing().when(emailChannel).send(notification);
        notificationService.sendPendingNotifications();

        assertEquals(DeliveryStatus.SENT, notification.getStatus());
        assertNotNull(notification.getSentAt());
        assertEquals(2, notification.getAttempts());
        assertEquals(2, notification.getAttemptsHistory().size());
        assertEquals(DeliveryStatus.FAILED, notification.getAttemptsHistory().get(0).getStatus());
        assertEquals(DeliveryStatus.SENT, notification.getAttemptsHistory().get(1).getStatus());
    }

    @Test
    void test3_VariasFalhasConsecutivas() {
        Notification notification = criarNotificacaoTeste();
        notification.setMaxAttempts(4);
        when(notificationRepository.findByStatus(any())).thenReturn(List.of(notification));
        doThrow(new RuntimeException("Falha de conexão")).when(emailChannel).send(notification);

        // 1ª Falha
        notificationService.sendPendingNotifications();
        assertEquals(DeliveryStatus.RETRYING, notification.getStatus());
        assertEquals(1, notification.getAttempts());

        // 2ª Falha
        notificationService.sendPendingNotifications();
        assertEquals(DeliveryStatus.RETRYING, notification.getStatus());
        assertEquals(2, notification.getAttempts());

        assertEquals(2, notification.getAttemptsHistory().size());
        assertEquals(DeliveryStatus.FAILED, notification.getAttemptsHistory().get(0).getStatus());
        assertEquals(DeliveryStatus.FAILED, notification.getAttemptsHistory().get(1).getStatus());
    }

    @Test
    void test4_NotificacaoAtingeNumeroMaximoDeTentativas() {
        Notification notification = criarNotificacaoTeste();
        notification.setMaxAttempts(3);
        when(notificationRepository.findByStatus(any())).thenReturn(List.of(notification));
        doThrow(new RuntimeException("Email rejeitado")).when(emailChannel).send(notification);

        // 1ª tentativa -> RETRYING
        notificationService.sendPendingNotifications();
        assertEquals(DeliveryStatus.RETRYING, notification.getStatus());

        // 2ª tentativa -> RETRYING
        notificationService.sendPendingNotifications();
        assertEquals(DeliveryStatus.RETRYING, notification.getStatus());

        // 3ª tentativa (máximo atingido) -> FAILED
        notificationService.sendPendingNotifications();
        assertEquals(DeliveryStatus.FAILED, notification.getStatus());
        assertEquals(3, notification.getAttempts());
        assertEquals(3, notification.getAttemptsHistory().size());
        assertEquals("Email rejeitado", notification.getFailureReason());
    }
}
