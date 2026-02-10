package ru.miacomsoft;

import java.util.HashMap;
import java.util.Map;

public class KeyConverter {
    private static final Map<Character, Character> RUS_TO_ENG_LOWER = new HashMap<>();
    private static final Map<Character, Character> RUS_TO_ENG_UPPER = new HashMap<>();
    private static final Map<Character, Character> ENG_TO_RUS_LOWER = new HashMap<>();
    private static final Map<Character, Character> ENG_TO_RUS_UPPER = new HashMap<>();

    static {
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

    /**
     * Конвертирует русский символ в соответствующий английский по раскладке клавиатуры
     * @param russianChar русский символ
     * @return английский символ или тот же символ, если конвертация не требуется
     */
    public static char convertRussianToEnglish(char russianChar) {
        // Проверяем на заглавные буквы
        if (RUS_TO_ENG_UPPER.containsKey(russianChar)) {
            return RUS_TO_ENG_UPPER.get(russianChar);
        }

        // Проверяем на строчные буквы
        if (RUS_TO_ENG_LOWER.containsKey(russianChar)) {
            return RUS_TO_ENG_LOWER.get(russianChar);
        }

        // Возвращаем тот же символ, если это не русская буква
        return russianChar;
    }

    /**
     * Конвертирует строку с русскими символами в английские по раскладке клавиатуры
     * @param russianString строка с русскими символами
     * @return строка с английскими символами
     */
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

    /**
     * Проверяет, является ли символ русской буквой
     * @param c символ для проверки
     * @return true если символ - русская буква
     */
    public static boolean isRussianLetter(char c) {
        return RUS_TO_ENG_LOWER.containsKey(c) || RUS_TO_ENG_UPPER.containsKey(c);
    }

    /**
     * Конвертирует английский символ в соответствующий русский по раскладке клавиатуры
     * @param englishChar английский символ
     * @return русский символ или тот же символ, если конвертация не требуется
     */
    public static char convertEnglishToRussian(char englishChar) {
        // Проверяем на заглавные буквы
        if (ENG_TO_RUS_UPPER.containsKey(englishChar)) {
            return ENG_TO_RUS_UPPER.get(englishChar);
        }

        // Проверяем на строчные буквы
        if (ENG_TO_RUS_LOWER.containsKey(englishChar)) {
            return ENG_TO_RUS_LOWER.get(englishChar);
        }

        // Возвращаем тот же символ, если это не английская буква из мапы
        return englishChar;
    }

    /**
     * Конвертирует строку с английскими символами в русские по раскладке клавиатуры
     * @param englishString строка с английскими символами
     * @return строка с русскими символами
     */
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
}