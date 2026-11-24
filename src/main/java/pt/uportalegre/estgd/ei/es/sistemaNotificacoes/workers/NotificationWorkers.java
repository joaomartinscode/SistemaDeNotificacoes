package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.workers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.services.NotificationService;

@Component
public class NotificationWorkers {

    private static final Logger log = LoggerFactory.getLogger(NotificationWorkers.class);

    @Autowired
    private NotificationService notificationService;

    @Scheduled(fixedDelay = 2000)
    public void processNotificationQueue() {
        log.debug("Polling notification queue");
        notificationService.processQueue();
    }

    @Scheduled(fixedDelay = 15000)
    public void checkDeliveryStatus() {
        log.debug("Running periodic delivery status (bounce) check");
        notificationService.checkDeliveryStatus();
    }
}
