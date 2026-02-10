package ru.miscomcoft;
import java.util.HashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.Map;

public class KeyCodes {
//    public static final Map<Byte, Integer> KEY_CODES = new HashMap<>();
//    public static final Map<Byte, Integer> KEY_CODES_RUS = new HashMap<>();
    public static final Map<String, Integer> KEY_CODES = new HashMap<>();
    public static final Map<String, Integer> KEY_CODES_RUS = new HashMap<>();
    /*
    document.addEventListener('keydown', function(event) {
    console.log('Нажата клавиша:', {
        key: event.key,          // Символьное представление (например, "a", "Enter")
        code: event.code,        // Физический код клавиши (например, "KeyA", "Enter")
        keyCode: event.keyCode,  // Устаревший числовой код (лучше не использовать)
        which: event.which,      // Аналогично keyCode (устаревшее)
        altKey: event.altKey,    // Нажат ли Alt
        ctrlKey: event.ctrlKey,  // Нажат ли Ctrl
        shiftKey: event.shiftKey,// Нажат ли Shift
        metaKey: event.metaKey   // Нажат ли Meta (Cmd на Mac)
    });
});


     */

//        for (int i=1; i<=255; i++) {
//            port2.writeBytes(new byte[]{(byte) i}, 1);
//            System.out.print((char)i+" = "+i);
//            String input = reader.readLine(); // Чтение строки
//
//        }
//        Thread.sleep(100);

    // message = "Йaaaaaaa ыыыыыыыы ППППППП VVVVV";
    static {
        // 135 - Win
        // 231 - Home
        // 134 - CapsLock

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
        byte b = charStr.getBytes()[0]; // Берем первый байт символа
        KEY_CODES.put(charStr, code);
    }

    private static void putByteRus(String charStr, int code) {
        byte[] bytes = charStr.getBytes();
        if (bytes.length > 0) {
            KEY_CODES_RUS.put(charStr, code); // Берем первый байт кириллического символа
        }
    }

    public static boolean isCyrillic(char c) {
        return Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC;
    }
    public static boolean isCyrillic(byte b) {
        // Преобразуем byte в char, учитывая кодировку (по умолчанию UTF-8)
        char c = (char) (b & 0xFF); // Беззнаковое преобразование byte -> char
        return Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC;
    }

    // Дополнительные методы для работы с byte вместо String
    public static Integer getCode(byte b) {
        return KEY_CODES.get(b);
    }

    public static Integer getRusCode(byte b) {
        return KEY_CODES_RUS.get(b);
    }
}