package pt.uportalegre.estgd.ei.es.sistemaNotificacoes;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SistemaDeNotificacoesApplication {

    public static void main(String[] args) {
        loadEnv();
        SpringApplication.run(SistemaDeNotificacoesApplication.class, args);
    }

    public static void loadEnv() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        for (DotenvEntry entry : dotenv.entries()) {
            System.setProperty(entry.getKey(), entry.getValue());
        }
    }
}
