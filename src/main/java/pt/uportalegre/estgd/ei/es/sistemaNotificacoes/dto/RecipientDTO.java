package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.dto;

public record RecipientDTO(
        String destination,
        String type
) {
    public RecipientDTO(String destination) {
        this(destination, "TO");
    }

    public String getDestination() {
        return destination;
    }

    public String getType() {
        return type;
    }
}
