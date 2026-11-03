package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.workers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.services.NotificationService;

@Component
public class NotificationWorkers {

    @Autowired
    private NotificationService notificationService;

    @Scheduled(fixedDelay = 10000)
    public void sendPendingNotifications() {
        notificationService.sendPendingNotifications();
    }

    @Scheduled(fixedDelay = 15000)
    public void checkDeliveryStatus() {
        notificationService.checkDeliveryStatus();
    }
}
