package pt.uportalegre.estgd.ei.es.sistemaNotificacoes;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.email.EmailBounceDetector;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.email.EmailDeliveryChannel;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.ChannelType;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.Notification;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.Priority;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.EmailNotificationRequestDTO;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.NotificationDTO;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.NotificationRequestDTO;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.RecipientDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class EmailDeliveryChannelAndRequestTest {

    static class TestableEmailDeliveryChannel extends EmailDeliveryChannel {
        final List<Long> sleepCalls = new ArrayList<>();

        TestableEmailDeliveryChannel(int maxSendAttempts, long initialBackoffMs, long maxBackoffMs, long maxGlobalPauseMs) {
            super(maxSendAttempts, initialBackoffMs, maxBackoffMs, maxGlobalPauseMs);
        }

        @Override
        protected void sleepMillis(long millis) {
            sleepCalls.add(millis);
        }

        long currentGlobalPauseMs() {
            return getGlobalPauseMs();
        }

        void assignMailSender(JavaMailSender sender) {
            setMailSender(sender);
        }
    }

    static class TestableBounceDetector extends EmailBounceDetector {
        List<String> parseHeaders(Message[] messages) {
            return extractFailedRecipients(messages);
        }
    }

    private Notification createEmailNotification() {
        List<RecipientDTO> recipients = List.of(
                new RecipientDTO("user1@uportalegre.pt", "TO"),
                new RecipientDTO("bananas@uportalegre.pt", "CC")
        );
        Notification n = new Notification("Subject", "Body", ChannelType.EMAIL, recipients);
        n.setId(1);
        return n;
    }

    @Test
    void shouldBuildEmailNotificationRequestFromRequestParamsUsingInheritance() {
        Map<String, String> params = Map.of(
                "to", "a@uportalegre.pt, b@uportalegre.pt",
                "cc", "c@uportalegre.pt",
                "bcc", "d@uportalegre.pt",
                "subject", "Aviso"
        );

        NotificationRequestDTO req = NotificationRequestDTO.fromRequestParams(
                ChannelType.EMAIL,
                Priority.HIGH,
                "Mensagem de teste",
                params
        );

        assertInstanceOf(EmailNotificationRequestDTO.class, req);
        NotificationDTO dto = req.toNotificationDTO();
        assertEquals(ChannelType.EMAIL, dto.channelType());
        assertEquals(Priority.HIGH, dto.priority());
        assertEquals("Aviso", dto.subject());
        assertEquals("Mensagem de teste", dto.content());
        assertEquals(4, dto.recipients().size());
    }

    @Test
    void shouldRetryIterativelyWithIncreasingBackoffAndActivateGlobalPauseOnThrottle() {
        TestableEmailDeliveryChannel channel = new TestableEmailDeliveryChannel(3, 4500L, 30000L, 20000L);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        channel.assignMailSender(mailSender);

        MimeMessage mime1 = new MimeMessage(Session.getInstance(new Properties()));
        MimeMessage mime2 = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime1, mime2);

        AtomicInteger sendCalls = new AtomicInteger(0);
        doAnswer(invocation -> {
            int call = sendCalls.incrementAndGet();
            if (call <= 2) {
                throw new MailSendException("421 4.7.0 Too many simultaneous connections. Try again later.");
            }
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        channel.send(createEmailNotification());

        assertEquals(3, sendCalls.get());
        assertEquals(List.of(4500L, 9000L), channel.sleepCalls);
        assertEquals(4500L, channel.currentGlobalPauseMs());

        channel.sleepCalls.clear();
        channel.send(createEmailNotification());

        assertEquals(List.of(4500L), channel.sleepCalls);
        assertEquals(2250L, channel.currentGlobalPauseMs());
    }

    @Test
    void shouldExtractXFailedRecipientsFromBounceMessageHeaders() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage bounce = new MimeMessage(session);
        bounce.addHeader("X-Failed-Recipients", "bananas@uportalegre.pt, outro@uportalegre.pt");

        List<String> failed = new TestableBounceDetector().parseHeaders(new Message[]{bounce});
        assertEquals(List.of("bananas@uportalegre.pt", "outro@uportalegre.pt"), failed);
    }
}
