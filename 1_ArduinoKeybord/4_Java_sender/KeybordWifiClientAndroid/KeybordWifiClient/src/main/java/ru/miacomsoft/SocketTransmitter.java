package ru.miacomsoft;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static ru.miacomsoft.KeyCodes.KEY_CODES_RUS;
import static ru.miacomsoft.KeyCodes.isCyrillic;

public class SocketTransmitter extends JFrame {
    private JTextField hostTextField;
    private JTextField portTextField;
    private JTextArea messageTextArea;
    private JButton pauseButton;
    private JButton stopButton;
    private JButton startButton;
    private JButton connectButton;
    private JLabel lineNumberLabel;
    private JLabel statusLabel;

    private Socket socket;
    private OutputStream outputStream;
    private Thread transmissionThread;
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private AtomicBoolean isStopped = new AtomicBoolean(false);
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private boolean isEnglish = true;

    public SocketTransmitter() {
        setTitle("Socket Transmitter");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        initComponents();
        layoutComponents();

        setVisible(true);
    }

    private void initComponents() {
        // Поля для хоста и порта
        hostTextField = new JTextField("192.168.4.1", 15);
        portTextField = new JTextField("8200", 5);

        connectButton = new JButton("Подключить");
        connectButton.addActionListener(e -> toggleConnection());

        // Текстовое поле для сообщения
        messageTextArea = new JTextArea();
        messageTextArea.setLineWrap(true);
        messageTextArea.setWrapStyleWord(true);

        // Кнопки управления
        pauseButton = new JButton("Пауза");
        stopButton = new JButton("Стоп");
        startButton = new JButton("Старт");

        lineNumberLabel = new JLabel("Строка: 0");
        statusLabel = new JLabel("Не подключено");

        // Обработчики кнопок
        pauseButton.addActionListener(e -> togglePause());
        stopButton.addActionListener(e -> stopTransmission());
        startButton.addActionListener(e -> startTransmission());

        // Начальное состояние кнопок
        updateButtonStates(false, false);
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));

        // Панель с подключением
        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connectionPanel.add(new JLabel("Хост:"));
        connectionPanel.add(hostTextField);
        connectionPanel.add(new JLabel("Порт:"));
        connectionPanel.add(portTextField);
        connectionPanel.add(connectButton);
        connectionPanel.add(statusLabel);

        // Панель с кнопками
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.add(startButton);
        buttonPanel.add(pauseButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(lineNumberLabel);

        // Основная компоновка
        add(connectionPanel, BorderLayout.NORTH);
        add(new JScrollPane(messageTextArea), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Отступы
        ((JComponent)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void toggleConnection() {
        if (isConnected.get()) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        String host = hostTextField.getText().trim();
        String portStr = portTextField.getText().trim();

        if (host.isEmpty() || portStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Введите хост и порт!");
            return;
        }

        try {
            int port = Integer.parseInt(portStr);
            socket = new Socket(host, port);
            outputStream = socket.getOutputStream();
            isConnected.set(true);

            statusLabel.setText("Подключено");
            connectButton.setText("Отключить");
            updateButtonStates(true, false);

            JOptionPane.showMessageDialog(this, "Успешно подключено к " + host + ":" + port);

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Неверный формат порта!");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Ошибка подключения: " + e.getMessage());
            statusLabel.setText("Ошибка подключения");
        }
    }

    private void disconnect() {
        isConnected.set(false);
        isStopped.set(true);

        if (transmissionThread != null && transmissionThread.isAlive()) {
            transmissionThread.interrupt();
        }

        try {
            if (outputStream != null) {
                outputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        statusLabel.setText("Не подключено");
        connectButton.setText("Подключить");
        updateButtonStates(false, false);
        lineNumberLabel.setText("Строка: 0");
    }

    private void startTransmission() {
        if (!isConnected.get()) {
            JOptionPane.showMessageDialog(this, "Сначала подключитесь к серверу!");
            return;
        }

        String message = messageTextArea.getText();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Введите текст для отправки!");
            return;
        }

        isStopped.set(false);
        isPaused.set(false);
        isEnglish = true;

        updateButtonStates(true, true);

        // Запуск потока передачи
        transmissionThread = new Thread(() -> {
            try {
                transmitMessage(message);
                SwingUtilities.invokeLater(() -> messageTextArea.setText(""));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Ошибка передачи: " + e.getMessage());
                    disconnect();
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    updateButtonStates(true, false);
                    lineNumberLabel.setText("Строка: 0");
                });
            }
        });

        transmissionThread.start();
    }

    private void transmitMessage(String message) throws InterruptedException, IOException {
        message = message.replaceAll("[\\p{C}&&[^\n]]", "");

        String[] lines = message.split("\n");
        int finalLineNum = 1;
        SwingUtilities.invokeLater(() -> lineNumberLabel.setText("Строка: 1 из " + lines.length));

        for (char c : message.toCharArray()) {
            if (isStopped.get()) {
                break;
            }

            // Ожидание если пауза
            while (isPaused.get() && !isStopped.get()) {
                Thread.sleep(100);
            }

            if (isStopped.get()) {
                break;
            }

            System.out.println(c);

            if (c == 10) { // Home
                Thread.sleep(100);
                sendByte(32);
                Thread.sleep(100);
                finalLineNum += 1;
                int finalLineNum1 = finalLineNum;
                SwingUtilities.invokeLater(() -> lineNumberLabel.setText("Строка: " + finalLineNum1 + " из " + lines.length));
            }

            boolean isRussian = isCyrillic(c);
            if (isRussian) {
                if (isEnglish) {
                    isEnglish = false;
                    Thread.sleep(200);
                    sendByte(134);
                    Thread.sleep(200);
                }
                if (KEY_CODES_RUS.containsKey(String.valueOf(c))) {
                    int symb = KEY_CODES_RUS.get(String.valueOf(c));
                    sendByte(symb);
                }
            } else {
                if (!isEnglish) {
                    isEnglish = true;
                    Thread.sleep(200);
                    sendByte(134);
                    Thread.sleep(200);
                }
                sendByte(c);
            }

            if (c == 10) { // Home
                Thread.sleep(300);
                sendByte(231);
                Thread.sleep(300);
            }
        }

        if (!isEnglish) {
            Thread.sleep(200);
            sendByte(134);
            Thread.sleep(200);
        }
    }

    private void togglePause() {
        isPaused.set(!isPaused.get());
        pauseButton.setText(isPaused.get() ? "Продолжить" : "Пауза");
    }

    private void stopTransmission() {
        isStopped.set(true);
        updateButtonStates(true, false);
    }

    private void updateButtonStates(boolean connected, boolean transmissionRunning) {
        connectButton.setEnabled(true);
        connectButton.setText(connected ? "Отключить" : "Подключить");

        startButton.setEnabled(connected && !transmissionRunning);
        pauseButton.setEnabled(connected && transmissionRunning);
        stopButton.setEnabled(connected && transmissionRunning);

        if (!transmissionRunning) {
            pauseButton.setText("Пауза");
        }

        hostTextField.setEnabled(!connected);
        portTextField.setEnabled(!connected);
        messageTextArea.setEnabled(connected && !transmissionRunning);
    }

    private void sendByte(int value) throws IOException {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Value must be in range 0-255--" + value + "  ");
        }
        byte b = (byte) (value & 0xFF);
        if (outputStream != null) {
            outputStream.write(new byte[]{b});
            outputStream.flush();
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Transmission interrupted", e);
        }
    }

    @Override
    public void dispose() {
        disconnect();
        super.dispose();
    }
}