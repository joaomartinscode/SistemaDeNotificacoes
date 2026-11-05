package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.factory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.delivery.DeliveryChannel;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.ChannelType;

import java.util.List;

@Component
public class DeliveryChannelFactory {

    @Autowired
    private List<DeliveryChannel> deliveryChannels;

    public DeliveryChannel getDeliveryChannel(ChannelType channelType) {
        for (DeliveryChannel channel : deliveryChannels) {
            if (channel.supports(channelType)){
                return channel;
            }
        }
        throw new IllegalArgumentException("Delivery channel not found for type: " + channelType);
    }

    public List<DeliveryChannel> getDeliveryChannels() {
        return deliveryChannels;
    }
}
