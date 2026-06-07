package ru.miacomsoft;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class TextPunctuator {
    private static final Pattern[] PUNCTUATION_RULES = {
            Pattern.compile("\\b(привет|здравствуйте)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(как дела|как жизнь|как ты)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(спасибо|благодарю)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(пока|до свидания)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(да|нет)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(однако|итак|следовательно)\\b", Pattern.CASE_INSENSITIVE),
    };

    private static final String[] PUNCTUATION_ADDITIONS = {
            "!",
            "?",
            "!",
            "!",
            ".",
            ","
    };

    public static String addPunctuation(String text) {
        if (text == null || text.isEmpty()) return text;

        String result = text;

        if (!result.matches(".*[.!?]$")) {
            result += ".";
        }

        result = result.replaceAll("\\s+,\\s+", ", ");
        result = result.replaceAll("\\s+\\.\\s+", ". ");
        result = result.replaceAll("\\s+\\?\\s+", "? ");
        result = result.replaceAll("\\s+!\\s+", "! ");

        if (result.length() > 0) {
            result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
        }

        return result;
    }

    public static String capitalizeSentences(String text) {
        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder sb = new StringBuilder();

        for (String sentence : sentences) {
            if (sentence.length() > 0) {
                sb.append(Character.toUpperCase(sentence.charAt(0)))
                        .append(sentence.substring(1))
                        .append(" ");
            }
        }

        return sb.toString().trim();
    }

    public static String fixWindowsEncoding(String text) {
        if (text == null || text.isEmpty()) return text;

        try {
            // Метод 1: Прямое преобразование из ISO-8859-1 в UTF-8
            byte[] isoBytes = text.getBytes(StandardCharsets.ISO_8859_1);
            String utf8Result = new String(isoBytes, StandardCharsets.UTF_8);

            // Проверяем, содержит ли результат русские буквы
            if (containsRussian(utf8Result)) {
                System.out.println("Method 1 (ISO->UTF-8) success: " + utf8Result);
                return utf8Result;
            }

            // Метод 2: Обратное преобразование
            byte[] utf8Bytes = text.getBytes(StandardCharsets.UTF_8);
            String result2 = new String(utf8Bytes, StandardCharsets.ISO_8859_1);
            if (containsRussian(result2)) {
                System.out.println("Method 2 (UTF->ISO) success: " + result2);
                return result2;
            }

            // Метод 3: Через Windows-1251
            byte[] winBytes = text.getBytes(Charset.forName("Windows-1251"));
            String result3 = new String(winBytes, StandardCharsets.UTF_8);
            if (containsRussian(result3)) {
                System.out.println("Method 3 (Win1251) success: " + result3);
                return result3;
            }

            // Метод 4: Прямое преобразование из UTF-8 в UTF-8
            String result4 = new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            if (containsRussian(result4)) {
                System.out.println("Method 4 (UTF->UTF) success: " + result4);
                return result4;
            }

            // Метод 5: Ручная замена проблемных последовательностей
            String result5 = text;
            result5 = result5.replace("РІРѕС‚", "вот");
            result5 = result5.replace("РёРЅС‚РµСЂРµСЃРЅРѕ", "интересно");
            result5 = result5.replace("РјРёРєСЂРѕС„РѕРЅ", "микрофон");
            result5 = result5.replace("СЂР°СЃРїРѕР·РЅР°РµС‚", "распознает");

            if (containsRussian(result5)) {
                System.out.println("Method 5 (manual) success: " + result5);
                return result5;
            }

            return text;
        } catch (Exception e) {
            e.printStackTrace();
            return text;
        }
    }

    private static boolean containsRussian(String text) {
        if (text == null || text.isEmpty()) return false;
        return text.matches(".*[а-яА-ЯёЁ].*");
    }
}