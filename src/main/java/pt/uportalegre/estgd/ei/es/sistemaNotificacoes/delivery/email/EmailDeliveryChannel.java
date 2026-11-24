package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.email;

import jakarta.mail.Address;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.DeliveryChannel;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.ChannelType;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.Notification;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.RecipientDTO;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EmailDeliveryChannel implements DeliveryChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailDeliveryChannel.class);

    static final int DEFAULT_MAX_SEND_ATTEMPTS = 3;
    static final long DEFAULT_INITIAL_BACKOFF_MS = 4500L;
    static final long DEFAULT_MAX_BACKOFF_MS = 30000L;
    static final long DEFAULT_MAX_GLOBAL_PAUSE_MS = 20000L;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private EmailBounceDetector emailBounceDetector;

    private final int maxSendAttempts;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final long maxGlobalPauseMs;

    private final Object sendLock = new Object();
    private long globalPauseMs = 0L;

    private final Map<String, List<String>> bouncedRecipientsByMessageId = new ConcurrentHashMap<>();

    public EmailDeliveryChannel() {
        this(DEFAULT_MAX_SEND_ATTEMPTS, DEFAULT_INITIAL_BACKOFF_MS, DEFAULT_MAX_BACKOFF_MS, DEFAULT_MAX_GLOBAL_PAUSE_MS);
    }

    protected EmailDeliveryChannel(int maxSendAttempts, long initialBackoffMs, long maxBackoffMs, long maxGlobalPauseMs) {
        this.maxSendAttempts = maxSendAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.maxGlobalPauseMs = maxGlobalPauseMs;
    }

    protected void setMailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    protected long getGlobalPauseMs() {
        synchronized (sendLock) {
            return globalPauseMs;
        }
    }

    @Override
    public boolean supports(ChannelType channelType) {
        return channelType == ChannelType.EMAIL;
    }

    @Override
    public void send(Notification notification) {
        List<String> to = new ArrayList<>();
        List<String> cc = new ArrayList<>();
        List<String> bcc = new ArrayList<>();

        if (notification.getRecipients() != null) {
            for (RecipientDTO r : notification.getRecipients()) {
                String type = r.getType();
                if ("CC".equalsIgnoreCase(type)) {
                    cc.add(r.getDestination());
                } else if ("BCC".equalsIgnoreCase(type)) {
                    bcc.add(r.getDestination());
                } else {
                    to.add(r.getDestination());
                }
            }
        }

        MimeMessage mimeMessage = createAndPrepareMimeMessage(notification, to, cc, bcc);

        synchronized (sendLock) {
            if (globalPauseMs > 0) {
                log.info("Global SMTP cooldown active: pausing {}ms before sending notification #{}",
                        globalPauseMs, notification.getId());
                sleepMillis(globalPauseMs);
            }

            long currentBackoffMs = initialBackoffMs;

            for (int attempt = 1; attempt <= maxSendAttempts; attempt++) {
                try {
                    log.info("Sending email notification #{} (attempt {}/{}) to={}, cc={}, bcc={}",
                            notification.getId(), attempt, maxSendAttempts, to, cc, bcc);

                    mailSender.send(mimeMessage);

                    String messageId = mimeMessage.getMessageID();
                    if (messageId != null) {
                        notification.getPayload().put("messageId", messageId);
                    }

                    if (globalPauseMs > 0) {
                        globalPauseMs = globalPauseMs <= 1000L ? 0L : globalPauseMs / 2;
                        log.info("Email notification #{} sent successfully; reducing global SMTP pause to {}ms",
                                notification.getId(), globalPauseMs);
                    } else {
                        log.info("Email notification #{} sent successfully (messageId={})",
                                notification.getId(), messageId);
                    }
                    return;
                } catch (Exception e) {
                    List<String> immediateFailed = extractFailedAddresses(e);
                    if (!immediateFailed.isEmpty()) {
                        notification.getPayload().put("failedRecipients", immediateFailed);
                        log.warn("Immediate failed recipients captured for notification #{}: {}",
                                notification.getId(), immediateFailed);
                    }

                    if (isThrottleOrTransientException(e)) {
                        globalPauseMs = (globalPauseMs == 0L)
                                ? initialBackoffMs
                                : Math.min(maxGlobalPauseMs, globalPauseMs * 2);

                        if (attempt < maxSendAttempts) {
                            log.warn("SMTP rate-limit/transient error on attempt {}/{} for notification #{}. " +
                                            "Sleeping {}ms before retry (globalPauseMs={}ms). Error: {}",
                                    attempt, maxSendAttempts, notification.getId(),
                                    currentBackoffMs, globalPauseMs, e.getMessage());
                            sleepMillis(currentBackoffMs);
                            currentBackoffMs = Math.min(maxBackoffMs, currentBackoffMs * 2);
                            continue;
                        }
                    }

                    throw buildDeliveryException(e, to, immediateFailed);
                }
            }
        }
    }

    private MimeMessage createAndPrepareMimeMessage(
            Notification notification,
            List<String> to,
            List<String> cc,
            List<String> bcc) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            String subject = notification.getSubject() != null ? notification.getSubject() : "";
            helper.setSubject(subject);
            helper.setText(notification.getContent());

            if (!to.isEmpty()) {
                helper.setTo(to.toArray(new String[0]));
            }
            if (!cc.isEmpty()) {
                helper.setCc(cc.toArray(new String[0]));
            }
            if (!bcc.isEmpty()) {
                helper.setBcc(bcc.toArray(new String[0]));
            }
            return mimeMessage;
        } catch (Exception e) {
            throw buildDeliveryException(e, to, extractFailedAddresses(e));
        }
    }

    protected void sleepMillis(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Email sending interrupted while waiting for SMTP cooldown.", ie);
        }
    }

    private boolean isThrottleOrTransientException(Throwable throwable) {
        for (Throwable current : flattenExceptions(throwable)) {
            String message = current.getMessage();
            if (message == null || message.isBlank()) {
                continue;
            }
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("authentication") || lower.contains("535")
                    || lower.contains("invalid address") || lower.contains("not a valid")) {
                return false;
            }
            if (lower.contains("421") || lower.contains("450") || lower.contains("451") || lower.contains("454")
                    || lower.contains("too many") || lower.contains("rate") || lower.contains("throttl")
                    || lower.contains("quota") || lower.contains("try again") || lower.contains("temporary")
                    || lower.contains("busy") || lower.contains("timed out") || lower.contains("connection reset")) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractFailedAddresses(Throwable throwable) {
        Set<String> failed = new LinkedHashSet<>();
        for (Throwable current : flattenExceptions(throwable)) {
            if (current instanceof SendFailedException sfe) {
                addAddresses(failed, sfe.getInvalidAddresses());
                addAddresses(failed, sfe.getValidUnsentAddresses());
            }
        }
        return new ArrayList<>(failed);
    }

    private List<Throwable> flattenExceptions(Throwable root) {
        List<Throwable> result = new ArrayList<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Throwable> queue = new ArrayDeque<>();
        if (root != null) {
            queue.add(root);
        }
        while (!queue.isEmpty()) {
            Throwable current = queue.poll();
            if (current == null || !visited.add(current)) {
                continue;
            }
            result.add(current);
            if (current instanceof MailSendException mse && mse.getFailedMessages() != null) {
                for (Exception failedEx : mse.getFailedMessages().values()) {
                    if (failedEx != null) {
                        queue.add(failedEx);
                    }
                }
            }
            if (current.getCause() != null) {
                queue.add(current.getCause());
            }
        }
        return result;
    }

    private void addAddresses(Set<String> target, Address[] addresses) {
        if (addresses == null) {
            return;
        }
        for (Address address : addresses) {
            if (address != null) {
                String value = address.toString().trim();
                if (!value.isEmpty()) {
                    target.add(value);
                }
            }
        }
    }

    private RuntimeException buildDeliveryException(Exception e, List<String> to, List<String> failedAddresses) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        log.error("Email delivery failed: {}", msg);

        if (msg.contains("not a valid") || msg.contains("Invalid Addresses") || !failedAddresses.isEmpty()) {
            String failedList = !failedAddresses.isEmpty() ? String.join(", ", failedAddresses) : String.join(", ", to);
            return new RuntimeException("Invalid email address: " + failedList, e);
        }
        if (msg.contains("Authentication") || msg.contains("535")) {
            return new RuntimeException("Email server authentication failed.", e);
        }
        if (msg.contains("Connection refused") || msg.contains("timed out")) {
            return new RuntimeException("Could not connect to email server.", e);
        }

        return new RuntimeException("Error sending email: " + msg, e);
    }

    @Override
    public List<String> checkDeliveryStatus(List<String> messageIds) {
        Map<String, List<String>> bouncedDetails = emailBounceDetector.findBouncedDetails(messageIds);
        bouncedRecipientsByMessageId.putAll(bouncedDetails);
        return new ArrayList<>(bouncedDetails.keySet());
    }

    @Override
    public List<String> getFailedRecipients(String messageId) {
        if (messageId == null) {
            return List.of();
        }
        List<String> failed = bouncedRecipientsByMessageId.remove(messageId);
        return failed != null ? failed : List.of();
    }
}
