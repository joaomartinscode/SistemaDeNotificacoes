package pt.uportalegre.estgd.ei.es.sistemaNotificacoes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain.ChannelType;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.*;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.services.NotificationService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest
class SistemaDeNotificacoesApplicationTests {

    @Test
    void contextLoads() {
    }
}
