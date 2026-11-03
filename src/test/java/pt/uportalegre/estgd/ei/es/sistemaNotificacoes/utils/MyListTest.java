package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.utils;

import org.junit.jupiter.api.Test;
import pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto.RecipientDTO;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class MyListTest {

    @Test
    void testAdicionarESincronizar() {
        AtomicReference<String> jsonSalvo = new AtomicReference<>("");

        MyList<String> lista = new MyList<>(
                () -> "[\"primeiro\"]",
                jsonSalvo::set,
                String.class
        );

        assertEquals(1, lista.size());
        assertEquals("primeiro", lista.get(0));

        lista.add("segundo");

        assertEquals(2, lista.size());
        assertTrue(jsonSalvo.get().contains("segundo"));
    }

    @Test
    void testComRecipientDTO() {
        AtomicReference<String> jsonSalvo = new AtomicReference<>("");

        MyList<RecipientDTO> lista = new MyList<>(
                () -> "[{\"destination\":\"teste@teste.com\",\"type\":\"TO\"}]",
                jsonSalvo::set,
                RecipientDTO.class
        );

        assertEquals(1, lista.size());
        assertEquals("teste@teste.com", lista.get(0).getDestination());

        lista.add(new RecipientDTO("outro@teste.com", "CC"));
        assertEquals(2, lista.size());
        assertTrue(jsonSalvo.get().contains("outro@teste.com"));

        lista.remove(0);
        assertEquals(1, lista.size());
        assertEquals("outro@teste.com", lista.get(0).getDestination());
    }
}
