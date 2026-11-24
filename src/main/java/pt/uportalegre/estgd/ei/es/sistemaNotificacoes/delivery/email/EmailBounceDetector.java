package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.email;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.HeaderTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class EmailBounceDetector {

    private static final Logger log = LoggerFactory.getLogger(EmailBounceDetector.class);

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;

    public List<String> findBouncedIds(List<String> messageIds) {
        return new ArrayList<>(findBouncedDetails(messageIds).keySet());
    }

    public Map<String, List<String>> findBouncedDetails(List<String> messageIds) {
        Map<String, List<String>> bouncedMap = new LinkedHashMap<>();
        if (messageIds == null || messageIds.isEmpty()) {
            return bouncedMap;
        }

        Store store = null;
        Folder folder = null;

        try {
            String imapServer = host != null && host.startsWith("smtp.") ? host.replace("smtp.", "imap.") : host;

            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.connectiontimeout", "5000");
            props.put("mail.imaps.timeout", "5000");

            Session session = Session.getInstance(props);
            store = session.getStore("imaps");
            store.connect(imapServer, username, password);

            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);

            for (String id : messageIds) {
                if (id != null && !id.isBlank()) {
                    HeaderTerm searchCondition = new HeaderTerm("In-Reply-To", id.trim());
                    Message[] messages = folder.search(searchCondition);
                    if (messages != null && messages.length > 0) {
                        List<String> failedRecipients = extractFailedRecipients(messages);
                        bouncedMap.put(id, failedRecipients);
                        log.warn("Bounce detected for messageId={}. X-Failed-Recipients={}", id, failedRecipients);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking IMAP inbox for bounced emails: {}", e.getMessage());
            return bouncedMap;
        } finally {
            try {
                if (folder != null && folder.isOpen()) folder.close(false);
                if (store != null && store.isConnected()) store.close();
            } catch (Exception ignored) {
            }
        }

        return bouncedMap;
    }

    protected List<String> extractFailedRecipients(Message[] messages) {
        Set<String> failedRecipients = new LinkedHashSet<>();
        if (messages == null) {
            return List.of();
        }
        for (Message msg : messages) {
            if (msg == null) {
                continue;
            }
            try {
                String[] headers = msg.getHeader("X-Failed-Recipients");
                if (headers != null) {
                    for (String header : headers) {
                        if (header != null && !header.isBlank()) {
                            for (String addr : header.split(",")) {
                                String clean = addr.trim();
                                if (!clean.isEmpty()) {
                                    failedRecipients.add(clean);
                                }
                            }
                        }
                    }
                }
            } catch (MessagingException e) {
                log.warn("Could not read X-Failed-Recipients header from bounce message: {}", e.getMessage());
            }
        }
        return new ArrayList<>(failedRecipients);
    }
}
