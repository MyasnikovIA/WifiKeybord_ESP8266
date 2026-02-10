package ru.miacomsoft;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

public class KeyCodes {
    public static final Map<String, Integer> KEY_CODES = new HashMap<>();
    public static final Map<String, Integer> KEY_CODES_RUS = new HashMap<>();

    // Коды для конвертации раскладки
    public static final Map<Character, Character> RUS_TO_ENG_LOWER = new HashMap<>();
    public static final Map<Character, Character> RUS_TO_ENG_UPPER = new HashMap<>();
    public static final Map<Character, Character> ENG_TO_RUS_LOWER = new HashMap<>();
    public static final Map<Character, Character> ENG_TO_RUS_UPPER = new HashMap<>();

    // Специальные коды
    public static final int CODE_TILDE = 126;
    public static final int CODE_BACKTICK = 96;
    public static final int CODE_YO_UPPER = 126;
    public static final int CODE_YO_LOWER = 96;

    // Коды модификаторов для клавиатуры
    public static final int KEY_LEFT_CTRL = 0x80;
    public static final int KEY_LEFT_SHIFT = 0x81;
    public static final int KEY_LEFT_ALT = 0x82;
    public static final int KEY_LEFT_GUI = 0x83;
    public static final int KEY_RIGHT_CTRL = 0x84;
    public static final int KEY_RIGHT_SHIFT = 0x85;
    public static final int KEY_RIGHT_ALT = 0x86;
    public static final int KEY_RIGHT_GUI = 0x87;
    public static final int KEY_RELEASE_ALL = 0x00;

    // Код кнопки для переключения синхронизации мыши (например, F12)
    public static final int MOUSE_SYNC_TOGGLE_KEY = KeyEvent.VK_F12;

    static {
        // Инициализация мап для конвертации раскладки
        initLayoutMaps();

        // Инициализация кодов клавиш
        initKeyCodes();
    }

    private static void initLayoutMaps() {
        // Строчные буквы
        RUS_TO_ENG_LOWER.put('й', 'q');
        RUS_TO_ENG_LOWER.put('ц', 'w');
        RUS_TO_ENG_LOWER.put('у', 'e');
        RUS_TO_ENG_LOWER.put('к', 'r');
        RUS_TO_ENG_LOWER.put('е', 't');
        RUS_TO_ENG_LOWER.put('н', 'y');
        RUS_TO_ENG_LOWER.put('г', 'u');
        RUS_TO_ENG_LOWER.put('ш', 'i');
        RUS_TO_ENG_LOWER.put('щ', 'o');
        RUS_TO_ENG_LOWER.put('з', 'p');
        RUS_TO_ENG_LOWER.put('х', '[');
        RUS_TO_ENG_LOWER.put('ъ', ']');

        RUS_TO_ENG_LOWER.put('ф', 'a');
        RUS_TO_ENG_LOWER.put('ы', 's');
        RUS_TO_ENG_LOWER.put('в', 'd');
        RUS_TO_ENG_LOWER.put('а', 'f');
        RUS_TO_ENG_LOWER.put('п', 'g');
        RUS_TO_ENG_LOWER.put('р', 'h');
        RUS_TO_ENG_LOWER.put('о', 'j');
        RUS_TO_ENG_LOWER.put('л', 'k');
        RUS_TO_ENG_LOWER.put('д', 'l');
        RUS_TO_ENG_LOWER.put('ж', ';');
        RUS_TO_ENG_LOWER.put('э', '\'');

        RUS_TO_ENG_LOWER.put('я', 'z');
        RUS_TO_ENG_LOWER.put('ч', 'x');
        RUS_TO_ENG_LOWER.put('с', 'c');
        RUS_TO_ENG_LOWER.put('м', 'v');
        RUS_TO_ENG_LOWER.put('и', 'b');
        RUS_TO_ENG_LOWER.put('т', 'n');
        RUS_TO_ENG_LOWER.put('ь', 'm');
        RUS_TO_ENG_LOWER.put('б', ',');
        RUS_TO_ENG_LOWER.put('ю', '.');

        RUS_TO_ENG_LOWER.put('ё', '`');

        // Заглавные буквы
        RUS_TO_ENG_UPPER.put('Й', 'Q');
        RUS_TO_ENG_UPPER.put('Ц', 'W');
        RUS_TO_ENG_UPPER.put('У', 'E');
        RUS_TO_ENG_UPPER.put('К', 'R');
        RUS_TO_ENG_UPPER.put('Е', 'T');
        RUS_TO_ENG_UPPER.put('Н', 'Y');
        RUS_TO_ENG_UPPER.put('Г', 'U');
        RUS_TO_ENG_UPPER.put('Ш', 'I');
        RUS_TO_ENG_UPPER.put('Щ', 'O');
        RUS_TO_ENG_UPPER.put('З', 'P');
        RUS_TO_ENG_UPPER.put('Х', '{');
        RUS_TO_ENG_UPPER.put('Ъ', '}');

        RUS_TO_ENG_UPPER.put('Ф', 'A');
        RUS_TO_ENG_UPPER.put('Ы', 'S');
        RUS_TO_ENG_UPPER.put('В', 'D');
        RUS_TO_ENG_UPPER.put('А', 'F');
        RUS_TO_ENG_UPPER.put('П', 'G');
        RUS_TO_ENG_UPPER.put('Р', 'H');
        RUS_TO_ENG_UPPER.put('О', 'J');
        RUS_TO_ENG_UPPER.put('Л', 'K');
        RUS_TO_ENG_UPPER.put('Д', 'L');
        RUS_TO_ENG_UPPER.put('Ж', ':');
        RUS_TO_ENG_UPPER.put('Э', '"');

        RUS_TO_ENG_UPPER.put('Я', 'Z');
        RUS_TO_ENG_UPPER.put('Ч', 'X');
        RUS_TO_ENG_UPPER.put('С', 'C');
        RUS_TO_ENG_UPPER.put('М', 'V');
        RUS_TO_ENG_UPPER.put('И', 'B');
        RUS_TO_ENG_UPPER.put('Т', 'N');
        RUS_TO_ENG_UPPER.put('Ь', 'M');
        RUS_TO_ENG_UPPER.put('Б', '<');
        RUS_TO_ENG_UPPER.put('Ю', '>');

        RUS_TO_ENG_UPPER.put('Ё', '~');

        // Создаем обратные мапы для проверки
        for (Map.Entry<Character, Character> entry : RUS_TO_ENG_LOWER.entrySet()) {
            ENG_TO_RUS_LOWER.put(entry.getValue(), entry.getKey());
        }

        for (Map.Entry<Character, Character> entry : RUS_TO_ENG_UPPER.entrySet()) {
            ENG_TO_RUS_UPPER.put(entry.getValue(), entry.getKey());
        }
    }

    private static void initKeyCodes() {
        // Английские строчные (a-z) с кодами строчных (97-122)
        putByte("A", 97);   putByteRus("ф", 97);
        putByte("B", 98);   putByteRus("и", 98);
        putByte("C", 99);   putByteRus("с", 99);
        putByte("D", 100);  putByteRus("в", 100);
        putByte("E", 101);  putByteRus("у", 101);
        putByte("F", 102);  putByteRus("а", 102);
        putByte("G", 103);  putByteRus("п", 103);
        putByte("H", 104);  putByteRus("р", 104);
        putByte("I", 105);  putByteRus("ш", 105);
        putByte("J", 106);  putByteRus("о", 106);
        putByte("K", 107);  putByteRus("л", 107);
        putByte("L", 108);  putByteRus("д", 108);
        putByte("M", 109);  putByteRus("ь", 109);
        putByte("N", 110);  putByteRus("т", 110);
        putByte("O", 111);  putByteRus("щ", 111);
        putByte("P", 112);  putByteRus("з", 112);
        putByte("Q", 113);  putByteRus("й", 113);
        putByte("R", 114);  putByteRus("к", 114);
        putByte("S", 115);  putByteRus("ы", 115);
        putByte("T", 116);  putByteRus("е", 116);
        putByte("U", 117);  putByteRus("г", 117);
        putByte("V", 118);  putByteRus("м", 118);
        putByte("W", 119);  putByteRus("ц", 119);
        putByte("X", 120);  putByteRus("ч", 120);
        putByte("Y", 121);  putByteRus("н", 121);
        putByte("Z", 122);  putByteRus("я", 122);

        // Английские заглавные (A-Z) с кодами заглавных (65-90)
        putByte("a", 65);   putByteRus("Ф", 65);
        putByte("b", 66);   putByteRus("И", 66);
        putByte("c", 67);   putByteRus("С", 67);
        putByte("d", 68);   putByteRus("В", 68);
        putByte("e", 69);   putByteRus("У", 69);
        putByte("f", 70);   putByteRus("А", 70);
        putByte("g", 71);   putByteRus("П", 71);
        putByte("h", 72);   putByteRus("Р", 72);
        putByte("i", 73);   putByteRus("Ш", 73);
        putByte("j", 74);   putByteRus("О", 74);
        putByte("k", 75);   putByteRus("Л", 75);
        putByte("l", 76);   putByteRus("Д", 76);
        putByte("m", 77);   putByteRus("Ь", 77);
        putByte("n", 78);   putByteRus("Т", 78);
        putByte("o", 79);   putByteRus("Щ", 79);
        putByte("p", 80);   putByteRus("З", 80);
        putByte("q", 81);   putByteRus("Й", 81);
        putByte("r", 82);   putByteRus("К", 82);
        putByte("s", 83);   putByteRus("Ы", 83);
        putByte("t", 84);   putByteRus("Е", 84);
        putByte("u", 85);   putByteRus("Г", 85);
        putByte("v", 86);   putByteRus("М", 86);
        putByte("w", 87);   putByteRus("Ц", 87);
        putByte("x", 88);   putByteRus("Ч", 88);
        putByte("y", 89);   putByteRus("Н", 89);
        putByte("z", 90);   putByteRus("Я", 90);

        // Спецсимволы
        putByte("\"", 34);   putByteRus("Э", 34);
        putByte("#", 35);    putByteRus("№", 35);
        putByte("$", 36);    putByteRus(";", 36);
        putByte("%", 37);    putByteRus("%", 37);
        putByte("&", 38);    putByteRus("?", 38);
        putByte("'", 39);    putByteRus("э", 39);
        putByte("(", 40);    putByteRus(")", 40);
        putByte(")", 41);    putByteRus("(", 41);
        putByte("*", 42);    putByteRus("*", 42);
        putByte("+", 43);    putByteRus("+", 43);
        putByte(",", 44);    putByteRus("б", 44);
        putByte("-", 45);    putByteRus("-", 45);
        putByte(".", 46);    putByteRus("ю", 46);
        putByte("/", 47);    putByteRus(".", 47);
        putByte(":", 58);    putByteRus("Ж", 58);
        putByte(";", 59);    // ???
        putByte("<", 60);    putByteRus("Б", 60);
        putByte("=", 61);    putByteRus("=", 61);
        putByte(">", 62);    putByteRus("Ю", 62);
        putByte("?", 63);    putByteRus(",", 63);
        putByte("@", 64);    putByteRus("\"", 64);
        putByte("[", 91);    putByteRus("х", 91);
        putByte("\\", 92);   putByteRus("\\", 92);
        putByte("]", 93);    putByteRus("ъ", 93);
        putByte("^", 94);    putByteRus(":", 94);
        putByte("_", 95);    putByteRus("_", 95);
        putByte("`", 96);    putByteRus("ё", 96);
        putByte("{", 123);   putByteRus("Х", 123);
        putByte("|", 124);   putByteRus("/", 124);
        putByte("}", 125);   putByteRus("Ъ", 125);
        putByte("~", 126);   putByteRus("Ё", 126);

        // Цифры (0-9)
        for (int i = 48; i <= 57; i++) {
            byte digit = (byte) i;
            KEY_CODES.put(String.valueOf(digit), i);
            KEY_CODES_RUS.put(String.valueOf(digit), i);
        }

        KEY_CODES_RUS.put("ж", 59);
        KEY_CODES_RUS.put("з", 112);
        KEY_CODES_RUS.put("ф", 140);
        KEY_CODES_RUS.put("и", 141);
        KEY_CODES_RUS.put("с", 142);
        KEY_CODES_RUS.put("в", 143);
        KEY_CODES_RUS.put("у", 144);
        KEY_CODES_RUS.put("а", 145);
        KEY_CODES_RUS.put("п", 146);
        KEY_CODES_RUS.put("р", 147);
        KEY_CODES_RUS.put("ш", 148);
        KEY_CODES_RUS.put("о", 149);
        KEY_CODES_RUS.put("л", 150);
        KEY_CODES_RUS.put("ь", 152);
        KEY_CODES_RUS.put("т", 153);
        KEY_CODES_RUS.put("щ", 154);
        KEY_CODES_RUS.put("з", 155);
        KEY_CODES_RUS.put("й", 156);
        KEY_CODES_RUS.put("к", 157);
        KEY_CODES_RUS.put("ы", 158);
        KEY_CODES_RUS.put("е", 159);
        KEY_CODES_RUS.put("г", 160);
        KEY_CODES_RUS.put("м", 161);
        KEY_CODES_RUS.put("ц", 162);
        KEY_CODES_RUS.put("ч", 163);
        KEY_CODES_RUS.put("н", 164);
        KEY_CODES_RUS.put("я", 165);
        KEY_CODES_RUS.put("1", 166);
        KEY_CODES_RUS.put("2", 167);
        KEY_CODES_RUS.put("3", 168);
        KEY_CODES_RUS.put("4", 169);
        KEY_CODES_RUS.put("5", 170);
        KEY_CODES_RUS.put("6", 171);
        KEY_CODES_RUS.put("7", 172);
        KEY_CODES_RUS.put("8", 173);
        KEY_CODES_RUS.put("9", 174);
        KEY_CODES_RUS.put("0", 175);
        KEY_CODES_RUS.put("\n", 176); // Enter
        KEY_CODES_RUS.put("0", 175);

        KEY_CODES_RUS.put("\b", 178); // BackSpace
        KEY_CODES_RUS.put("\t", 179); // Tab
        KEY_CODES_RUS.put(" ", 180);  // Space
        KEY_CODES_RUS.put("-", 181);
        KEY_CODES_RUS.put("=", 182);
        KEY_CODES_RUS.put("х", 183);
        KEY_CODES_RUS.put("ъ", 184);
        KEY_CODES_RUS.put("\\", 185);
        KEY_CODES_RUS.put("\\", 186);
        KEY_CODES_RUS.put("ж", 187);
        KEY_CODES_RUS.put("э", 188);
        KEY_CODES_RUS.put("ё", 189);
        KEY_CODES_RUS.put("б", 190);
        KEY_CODES_RUS.put("ю", 191);
        KEY_CODES_RUS.put(".", 192);
        KEY_CODES_RUS.put("CapsLock", 193);
        KEY_CODES_RUS.put(" F1", 194);
        KEY_CODES_RUS.put(" F2", 195);
        KEY_CODES_RUS.put(" F3", 196);
        KEY_CODES_RUS.put(" F4", 197);
        KEY_CODES_RUS.put(" F5", 198);
        KEY_CODES_RUS.put(" F6", 199);
        KEY_CODES_RUS.put(" F7", 200);
        KEY_CODES_RUS.put(" F8", 201);
        KEY_CODES_RUS.put(" F9", 202);
        KEY_CODES_RUS.put(" F10", 203);
        KEY_CODES_RUS.put(" F11", 204);
        KEY_CODES_RUS.put(" F12", 205);

        KEY_CODES_RUS.put("ESC", 177);
        KEY_CODES_RUS.put("inser", 209);
        KEY_CODES_RUS.put("home", 210);
        KEY_CODES_RUS.put("PageUp", 211);
        KEY_CODES_RUS.put("del", 212);
        KEY_CODES_RUS.put("end", 213);
        KEY_CODES_RUS.put("PageDown", 214);
        KEY_CODES_RUS.put("стрелка право", 215);
        KEY_CODES_RUS.put("стрелка лево", 216);
        KEY_CODES_RUS.put("стрелка низ", 217);
        KEY_CODES_RUS.put("стрелка верх", 218);
        KEY_CODES_RUS.put("NumLock", 219);
        KEY_CODES_RUS.put("/", 220);
        KEY_CODES_RUS.put("*", 221);
        KEY_CODES_RUS.put("-", 221);
        KEY_CODES_RUS.put("+", 222);
        KEY_CODES_RUS.put("enter", 223); // Enter (доп клавиатура)

        KEY_CODES_RUS.put("end", 225);          // end (доп клавиатура)
        KEY_CODES_RUS.put("стрелка низ", 226);  //  (доп клавиатура)
        KEY_CODES_RUS.put("PageDown", 227);     //  (доп клавиатура)
        KEY_CODES_RUS.put("стрелка лево", 228);  //  (доп клавиатура)
        KEY_CODES_RUS.put("5", 229);             //  (доп клавиатура) 5 если включен  NumLock
        KEY_CODES_RUS.put("стрелка право", 230);  //  (доп клавиатура)
        KEY_CODES_RUS.put("home", 231);  //  (доп клавиатура)
        KEY_CODES_RUS.put("стрелка верх", 232);  //  (доп клавиатура)
        KEY_CODES_RUS.put("PageUp", 233);  //  (доп клавиатура)
        KEY_CODES_RUS.put("inser", 234);  //  (доп клавиатура)
        KEY_CODES_RUS.put("del", 235);  //  (доп клавиатура)

        KEY_CODES_RUS.put("\\", 236);
        KEY_CODES_RUS.put("ContextMenu", 237);
        KEY_CODES_RUS.put("Fn", 238); // предположительно

        KEY_CODES_RUS.put("win", 135);
        KEY_CODES_RUS.put("CapsLock", 134);

        // Управляющие клавиши (как байты)
        putByte(" ", 32);
        putByte("!", 33);
    }

    private static void putByte(String charStr, int code) {
        KEY_CODES.put(charStr, code);
    }

    private static void putByteRus(String charStr, int code) {
        KEY_CODES_RUS.put(charStr, code);
    }

    // Методы для работы с раскладкой
    public static char convertRussianToEnglish(char russianChar) {
        if (RUS_TO_ENG_UPPER.containsKey(russianChar)) {
            return RUS_TO_ENG_UPPER.get(russianChar);
        }
        if (RUS_TO_ENG_LOWER.containsKey(russianChar)) {
            return RUS_TO_ENG_LOWER.get(russianChar);
        }
        return russianChar;
    }

    public static String convertRussianStringToEnglish(String russianString) {
        if (russianString == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (char c : russianString.toCharArray()) {
            result.append(convertRussianToEnglish(c));
        }
        return result.toString();
    }

    public static boolean isRussianLetter(char c) {
        return RUS_TO_ENG_LOWER.containsKey(c) || RUS_TO_ENG_UPPER.containsKey(c);
    }

    public static char convertEnglishToRussian(char englishChar) {
        if (ENG_TO_RUS_UPPER.containsKey(englishChar)) {
            return ENG_TO_RUS_UPPER.get(englishChar);
        }
        if (ENG_TO_RUS_LOWER.containsKey(englishChar)) {
            return ENG_TO_RUS_LOWER.get(englishChar);
        }
        return englishChar;
    }

    public static String convertEnglishStringToRussian(String englishString) {
        if (englishString == null) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (char c : englishString.toCharArray()) {
            result.append(convertEnglishToRussian(c));
        }
        return result.toString();
    }

    // Методы для проверки символов
    public static boolean isCyrillic(char c) {
        return Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC;
    }

    public static boolean isCyrillic(byte b) {
        char c = (char) (b & 0xFF);
        return Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC;
    }

    // Методы для получения кодов
    public static Integer getCode(byte b) {
        return KEY_CODES.get(String.valueOf((char) b));
    }

    public static Integer getRusCode(byte b) {
        return KEY_CODES_RUS.get(String.valueOf((char) b));
    }

    public static Integer getCode(String charStr) {
        return KEY_CODES.get(charStr);
    }

    public static Integer getRusCode(String charStr) {
        return KEY_CODES_RUS.get(charStr);
    }

    // Методы для проверки специальных символов
    public static boolean isLayoutSwitchChar(char c) {
        return c == '~' || c == 'Ё' || c == 'ё' || c == '`';
    }

    public static boolean isSpecialCharacterForIDE(char c) {
        return c == '{' || c == '\"' || c == '\'' || c == '[' || c == '(';
    }
    public static int convertVNCKeyCode(int javaKeyCode) {
        switch (javaKeyCode) {
            case KeyEvent.VK_ENTER: return 0xFF0D;
            case KeyEvent.VK_BACK_SPACE: return 0xFF08;
            case KeyEvent.VK_TAB: return 0xFF09;
            case KeyEvent.VK_ESCAPE: return 0xFF1B;
            case KeyEvent.VK_LEFT: return 0xFF51;
            case KeyEvent.VK_UP: return 0xFF52;
            case KeyEvent.VK_RIGHT: return 0xFF53;
            case KeyEvent.VK_DOWN: return 0xFF54;
            case KeyEvent.VK_HOME: return 0xFF50;
            case KeyEvent.VK_END: return 0xFF57;
            case KeyEvent.VK_PAGE_UP: return 0xFF55;
            case KeyEvent.VK_PAGE_DOWN: return 0xFF56;
            case KeyEvent.VK_DELETE: return 0xFFFF;
            case KeyEvent.VK_INSERT: return 0xFF63;
            case KeyEvent.VK_SHIFT: return 0xFFE1;
            case KeyEvent.VK_CONTROL: return 0xFFE3;
            case KeyEvent.VK_ALT: return 0xFFE9;
            case KeyEvent.VK_F1: return 0xFFBE;
            case KeyEvent.VK_F2: return 0xFFBF;
            case KeyEvent.VK_F3: return 0xFFC0;
            case KeyEvent.VK_F4: return 0xFFC1;
            case KeyEvent.VK_F5: return 0xFFC2;
            case KeyEvent.VK_F6: return 0xFFC3;
            case KeyEvent.VK_F7: return 0xFFC4;
            case KeyEvent.VK_F8: return 0xFFC5;
            case KeyEvent.VK_F9: return 0xFFC6;
            case KeyEvent.VK_F10: return 0xFFC7;
            case KeyEvent.VK_F11: return 0xFFC8;
            case KeyEvent.VK_F12: return 0xFFC9;
            default:
                if (javaKeyCode >= KeyEvent.VK_A && javaKeyCode <= KeyEvent.VK_Z) {
                    return 'a' + (javaKeyCode - KeyEvent.VK_A);
                } else if (javaKeyCode >= KeyEvent.VK_0 && javaKeyCode <= KeyEvent.VK_9) {
                    return '0' + (javaKeyCode - KeyEvent.VK_0);
                } else {
                    return javaKeyCode;
                }
        }
    }
}