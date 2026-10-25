package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
        SimpleMailMessage message = new SimpleMailMessage();
        String subject = notification.getSubject() != null ? notification.getSubject() : "";
        message.setSubject(subject);
        message.setText(notification.getContent());

        String[] to = getRecipientsByType(notification, "TO");
        String[] cc = getRecipientsByType(notification, "CC");
        String[] bcc = getRecipientsByType(notification, "BCC");

        if (to.length > 0) {
            message.setTo(to);
        }
        if (cc.length > 0) {
            message.setCc(cc);
        }
        if (bcc.length > 0) {
            message.setBcc(bcc);
        }

        try {
            mailSender.send(message);
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

    private String[] getRecipientsByType(Notification notification, String type) {
        List<String> result = new ArrayList<>();
        List<RecipientDTO> recipients = notification.getRecipients();
        if (recipients != null) {
            for (RecipientDTO r : recipients) {
                String recipientType = r.getType();
                if (type.equalsIgnoreCase(recipientType) || ("TO".equalsIgnoreCase(type) && recipientType == null)) {
                    result.add(r.getDestination());
                }
            }
        }
        return result.toArray(new String[0]);
    }
}
