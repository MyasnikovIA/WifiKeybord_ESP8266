package ru.miacomsoft;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static ru.miacomsoft.KeyCodes.KEY_CODES_RUS;
import static ru.miacomsoft.KeyCodes.isCyrillic;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new SocketTransmitter();
        });
    }
}

