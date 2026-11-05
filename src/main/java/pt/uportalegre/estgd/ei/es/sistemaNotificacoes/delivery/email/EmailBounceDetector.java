package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.email;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.HeaderTerm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Component
public class EmailBounceDetector {

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;

    public List<String> findBouncedIds(List<String> messageIds) {
        List<String> bouncedList = new ArrayList<>();
        if (messageIds == null || messageIds.isEmpty()) {
            return bouncedList;
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
                        bouncedList.add(id);
                    }
                }
            }
        } catch (Exception e) {
            return bouncedList;
        } finally {
            try {
                if (folder != null && folder.isOpen()) folder.close(false);
                if (store != null && store.isConnected()) store.close();
            } catch (Exception ignored) {
            }
        }

        return bouncedList;
    }
}
