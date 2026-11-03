package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.ChannelType;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.Notification;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.RecipientDTO;

import java.util.ArrayList;
import java.util.List;

@Component
public class EmailDeliveryChannel implements DeliveryChannel {

    @Autowired
    private JavaMailSender mailSender;

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

            mailSender.send(mimeMessage);

            String messageId = mimeMessage.getMessageID();
            if (messageId != null) {
                notification.getPayload().put("messageId", messageId);
            }
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";

            if (msg.contains("not a valid") || msg.contains("Invalid Addresses")) {
                throw new RuntimeException("Endereço de email inválido: " + String.join(", ", to));
            }
            if (msg.contains("Authentication") || msg.contains("535")) {
                throw new RuntimeException("Falha de autenticação no servidor de email.");
            }
            if (msg.contains("Connection refused") || msg.contains("timed out")) {
                throw new RuntimeException("Não foi possível ligar ao servidor de email.");
            }

            throw new RuntimeException("Erro ao enviar email: " + msg);
        }
    }
}
