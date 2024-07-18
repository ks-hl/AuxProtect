package dev.heliosares.auxprotect.core;

public enum Activity {

    IN_SPAWN('$', 100),
    COMMAND('/', 5),
    CHAT('c', 5),
    DAMAGE('d', 0.25),
    INTERACT_ENTITY('e', 1),
    INTERACT_BLOCK('f', 1),
    INTERACT_AIR('g', 1),
    CLICK_ITEM('i', 1),
    OPEN_INVENTORY('o', 1),
    PICKUP('p', 1),
    DROP('q', 1),
    BLOCK_BREAK('r', 1),
    BLOCK_PLACE('s', 1);


    public final char character;
    public final double score;

    Activity(char character, double score) {
        this.character = character;
        this.score = score;
    }

    public static Activity getByChar(char c) {
        for (Activity value : values()) {
            if (value.character == c) return value;
        }
        return null;
    }
}
