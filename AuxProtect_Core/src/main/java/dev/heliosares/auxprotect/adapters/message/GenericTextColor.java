package dev.heliosares.auxprotect.adapters.message;

public class GenericTextColor {
    public static final GenericTextColor BLACK = new GenericTextColor('0');
    public static final GenericTextColor DARK_BLUE = new GenericTextColor('1');
    public static final GenericTextColor DARK_GREEN = new GenericTextColor('2');
    public static final GenericTextColor DARK_AQUA = new GenericTextColor('3');
    public static final GenericTextColor DARK_RED = new GenericTextColor('4');
    public static final GenericTextColor DARK_PURPLE = new GenericTextColor('5');
    public static final GenericTextColor GOLD = new GenericTextColor('6');
    public static final GenericTextColor GRAY = new GenericTextColor('7');
    public static final GenericTextColor DARK_GRAY = new GenericTextColor('8');
    public static final GenericTextColor BLUE = new GenericTextColor('9');
    public static final GenericTextColor GREEN = new GenericTextColor('a');
    public static final GenericTextColor AQUA = new GenericTextColor('b');
    public static final GenericTextColor RED = new GenericTextColor('c');
    public static final GenericTextColor LIGHT_PURPLE = new GenericTextColor('d');
    public static final GenericTextColor YELLOW = new GenericTextColor('e');
    public static final GenericTextColor WHITE = new GenericTextColor('f');
    public static final char COLOR_CHAR = '§';
    public static final GenericTextColor BOLD = new GenericTextColor('l');
    public static final GenericTextColor STRIKETHROUGH = new GenericTextColor('m');
    public static final GenericTextColor UNDERLINE = new GenericTextColor('n');
    public static final GenericTextColor ITALICS = new GenericTextColor('o');
    public static final GenericTextColor RESET = new GenericTextColor('r');

    private char colorChar;
    private String hex;

    private GenericTextColor(char c) {
        this.colorChar = c;
    }

    public GenericTextColor(String hex) {
        if (hex.length() == 6) {
            hex = "#" + hex;
        } else if (hex.length() != 7) {
            throw new IllegalArgumentException("Invalid hex color string: " + hex);
        }
        this.hex = hex;
    }

    char getColorChar() {
        return colorChar;
    }

    String getHex() {
        return hex;
    }

    @Override
    public String toString() {
        if (hex == null) {
            return COLOR_CHAR + "" + colorChar;
        } else {
            StringBuilder out = new StringBuilder();
            for (char c : hex.toCharArray()) {
                if (c == '#') c = 'x';
                out.append(COLOR_CHAR).append(c);
            }
            return out.toString();
        }
    }
}
