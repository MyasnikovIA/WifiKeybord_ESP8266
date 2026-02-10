package ru.miscomcoft;

import com.fazecast.jSerialComm.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

import static ru.miscomcoft.KeyCodes.KEY_CODES_RUS;
import static ru.miscomcoft.KeyCodes.isCyrillic;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String message = "" +
                "using System;\n" +
                "\n" +
                "class StatsCalculator\n" +
                "{\n" +
                "    public static string Process(string input)\n" +
                "    {\n" +
                "        // Разделяем строку на массив чисел\n" +
                "        string[] numbers = input.Split(' ', StringSplitOptions.RemoveEmptyEntries);\n" +
                "        \n" +
                "        int positiveCount = 0;\n" +
                "        int negativeCount = 0;\n" +
                "        int zeroCount = 0;\n" +
                "        \n" +
                "        // Проходим по всем числам и считаем категории\n" +
                "        foreach (string numberStr in numbers)\n" +
                "        {\n" +
                "            if (int.TryParse(numberStr, out int number))\n" +
                "            {\n" +
                "                if (number > 0)\n" +
                "                {\n" +
                "                    positiveCount++;\n" +
                "                }\n" +
                "                else if (number < 0)\n" +
                "                {\n" +
                "                    negativeCount++;\n" +
                "                }\n" +
                "                else\n" +
                "                {\n" +
                "                    zeroCount++;\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "        \n" +
                "        // Форматируем результат согласно требованиям\n" +
                "        return $\"выше нуля: {positiveCount}, ниже нуля: {negativeCount}, равна нулю: {zeroCount}\";\n" +
                "    }\n" +
                "}";

        message = message.replaceAll("[\\p{C}&&[^\n]]", "");
        // message = message.replaceAll("(?m)^\\s+", "");
        SerialPort port2 = SerialPort.getCommPort("COM24");
        port2.openPort();
        port2.setComPortParameters(9600, 8, 1, 0);

        boolean isEnglish = true;
        boolean isPaused = false;
        boolean isRussian = false;

        System.out.println("Press Enter to start/pause transmission...");
        // reader.readLine(); // Wait for initial Enter press to start
        for (char c : message.toCharArray()) {
            // Check for Enter press in console without blocking
            if (System.in.available() > 0) {
                String input = reader.readLine();
                if (input.isEmpty()) {
                    isPaused = !isPaused;
                    System.out.println("Transmission " + (isPaused ? "paused" : "resumed") +
                            ". Press Enter to " + (isPaused ? "resume" : "pause") + "...");
                    if (isPaused) {
                        // Wait for Enter to resume
                        while (isPaused) {
                            String pauseInput = reader.readLine();
                            if (pauseInput.isEmpty()) {
                                isPaused = false;
                                System.out.println("Transmission resumed. Press Enter to pause...");
                            }
                        }
                    }
                }
            }

            if (isPaused) {
                continue;
            }
            System.out.println(c);
            if (c == 10) { // Home
                Thread.sleep(100);
                port2.writeBytes(new byte[]{(byte) 32}, 1);
                Thread.sleep(100);
            }
            isRussian = isCyrillic(c);
            if (isRussian) {
                if (isEnglish) {
                    isEnglish = false;
                    Thread.sleep(200);
                    port2.writeBytes(new byte[]{(byte) 134}, 1);
                    Thread.sleep(200);
                }
                if (KEY_CODES_RUS.containsKey(String.valueOf(c))) {
                    int symb = KEY_CODES_RUS.get(String.valueOf(c));
                    sendByte(port2, symb);
                }
            } else {
                if (!isEnglish) {
                    isEnglish = true;
                    Thread.sleep(200);
                    port2.writeBytes(new byte[]{(byte) 134}, 1);
                    Thread.sleep(200);
                }
                sendByte(port2, c);
            }
            if (c == 10) { // Home
                Thread.sleep(300);
                port2.writeBytes(new byte[]{(byte) 231}, 1);
                Thread.sleep(300);
            }
        }

        if (!isEnglish) {
            if (isEnglish) {
                isEnglish = false;
                Thread.sleep(200);
                port2.writeBytes(new byte[]{(byte) 134}, 1);
                Thread.sleep(200);
            }
        }
        port2.closePort();
        reader.close();
    }

    public static void sendByte(SerialPort port, int value) throws InterruptedException {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Value must be in range 0-255--" + value + "  ");
        }
        byte b = (byte) (value & 0xFF);
        port.writeBytes(new byte[]{b}, 1);
        Thread.sleep(100);
    }
}