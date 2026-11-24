package pt.uportalegre.estgd.ei.es.sistemaNotificacoes.domain;

public enum Priority {
    HIGH(3),
    NORMAL(2),
    LOW(1);

    private final int level;

    Priority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}
