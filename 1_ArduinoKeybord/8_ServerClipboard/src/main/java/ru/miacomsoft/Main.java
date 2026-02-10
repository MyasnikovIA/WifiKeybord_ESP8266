package ru.miacomsoft;

import javax.swing.*;


import static ru.miacomsoft.ServerClipboard.createAndShowGUI;
import static ru.miacomsoft.ServerClipboard.startWebServer;

public class Main {

    public static void main(String[] args) {
        int port = 8080;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Некорректный порт, используется порт по умолчанию: 8080");
            }
        }

        int finalPort = port;
        SwingUtilities.invokeLater(() -> {
            createAndShowGUI();
            new Thread(() -> startWebServer(finalPort)).start();
        });
    }

}
