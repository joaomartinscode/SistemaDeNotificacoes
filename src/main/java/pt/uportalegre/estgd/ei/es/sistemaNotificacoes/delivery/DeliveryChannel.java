package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery;

import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.ChannelType;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.Notification;

import java.util.List;

public interface DeliveryChannel {
    boolean supports(ChannelType channelType);
    void send(Notification notification);
    List<String> checkDeliveryStatus(List<String> messageIds);
}
