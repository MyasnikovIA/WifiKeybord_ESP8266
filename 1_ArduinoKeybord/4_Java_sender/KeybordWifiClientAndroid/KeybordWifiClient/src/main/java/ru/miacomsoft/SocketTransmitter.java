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
    private JButton testButton;
    private JButton sendCustomButton;
    private JLabel lineNumberLabel;
    private JLabel statusLabel;
    private JCheckBox ideCheckBox; // Новый чекбокс

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

        testButton = new JButton("test");
        testButton.addActionListener(e -> toggleTest());

        sendCustomButton = new JButton("Отправить кастом");
        sendCustomButton.addActionListener(e -> sendCustomData());

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

        // Чекбокс IDE
        ideCheckBox = new JCheckBox("IDE");

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
        connectionPanel.add(testButton);
        connectionPanel.add(sendCustomButton);
        connectionPanel.add(ideCheckBox); // Добавляем чекбокс

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

    private void toggleTest() {
        if (isConnected.get()) {
            // Отправляем тестовый байт
            sendTestByte();
        }
    }

    private void sendCustomData() {
        if (!isConnected.get()) {
            JOptionPane.showMessageDialog(this, "Сначала подключитесь к серверу!");
            return;
        }

        // Создаем диалоговое окно для ввода кастомных данных
        JPanel panel = new JPanel(new GridLayout(0, 1));

        JRadioButton textRadio = new JRadioButton("Текст", true);
        JRadioButton bytesRadio = new JRadioButton("Байты (разделенные пробелами)");
        ButtonGroup group = new ButtonGroup();
        group.add(textRadio);
        group.add(bytesRadio);

        JTextArea inputArea = new JTextArea(5, 30);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);

        panel.add(new JLabel("Выберите тип данных:"));
        panel.add(textRadio);
        panel.add(bytesRadio);
        panel.add(new JLabel("Введите данные:"));
        panel.add(new JScrollPane(inputArea));

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Отправить кастомные данные", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String input = inputArea.getText().trim();
            if (!input.isEmpty()) {
                new Thread(() -> {
                    try {
                        if (textRadio.isSelected()) {
                            // Отправляем как текст
                            sendCustomText(input);
                        } else {
                            // Отправляем как байты
                            sendCustomBytes(input);
                        }
                        JOptionPane.showMessageDialog(this, "Данные успешно отправлены!");
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(this,
                                        "Ошибка отправки: " + ex.getMessage(),
                                        "Ошибка", JOptionPane.ERROR_MESSAGE));
                    }
                }).start();
            }
        }
    }

    private void sendTestByte() {
        try {
            sendByte(210);
            JOptionPane.showMessageDialog(this, "Тестовый байт (210) отправлен!");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Ошибка отправки тестового байта: " + ex.getMessage());
        }
    }

    // Публичный метод для отправки произвольного текста
    public void sendCustomText(String text) throws IOException, InterruptedException {
        if (!isConnected.get()) {
            throw new IllegalStateException("Не подключено к серверу");
        }

        // Сохраняем текущее состояние
        boolean wasEnglish = isEnglish;
        isEnglish = true; // Начинаем с английской раскладки

        try {
            // Отправляем текст посимвольно
            for (char c : text.toCharArray()) {
                // Обработка IDE режима для символа {
                if (ideCheckBox.isSelected() && isSpecialCharacterForIDE(c)) {
                    // Отправляем символ {
                    boolean isRussian = isCyrillic(c);
                    if (isRussian) {
                        if (isEnglish) {
                            isEnglish = false;
                            Thread.sleep(200);
                            sendByte(134); // CapsLock
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
                            sendByte(134); // CapsLock
                            Thread.sleep(200);
                        }
                        sendByte(c);
                    }

                    // Ждем 0.3 секунды
                    Thread.sleep(300);

                    // Отправляем кнопку Del (код 212 из KeyCodes.java)
                    sendByte(212);
                } else {
                    // Стандартная обработка символа
                    boolean isRussian = isCyrillic(c);
                    if (isRussian) {
                        if (isEnglish) {
                            isEnglish = false;
                            Thread.sleep(200);
                            sendByte(134); // CapsLock
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
                            sendByte(134); // CapsLock
                            Thread.sleep(200);
                        }
                        sendByte(c);
                    }
                }
                Thread.sleep(100);
            }

            // Возвращаем раскладку, если нужно
            if (!isEnglish && wasEnglish) {
                Thread.sleep(200);
                sendByte(134); // CapsLock
                Thread.sleep(200);
            }

            // Восстанавливаем состояние
            isEnglish = wasEnglish;

        } catch (Exception e) {
            // Восстанавливаем состояние при ошибке
            isEnglish = wasEnglish;
            throw e;
        }
    }

    // Публичный метод для отправки произвольных байт
    public void sendCustomBytes(String byteString) throws IOException, InterruptedException {
        if (!isConnected.get()) {
            throw new IllegalStateException("Не подключено к серверу");
        }

        String[] byteStrings = byteString.split("\\s+");
        for (String str : byteStrings) {
            try {
                int value = Integer.parseInt(str.trim());
                if (value < 0 || value > 255) {
                    throw new IllegalArgumentException("Байт должен быть в диапазоне 0-255: " + value);
                }
                sendByte(value);
                Thread.sleep(100);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Неверный формат байта: " + str);
            }
        }
    }

    // Публичный метод для отправки массива байт
    public void sendCustomBytes(byte[] bytes) throws IOException, InterruptedException {
        if (!isConnected.get()) {
            throw new IllegalStateException("Не подключено к серверу");
        }

        for (byte b : bytes) {
            sendByte(b & 0xFF); // Преобразуем в беззнаковый
            Thread.sleep(100);
        }
    }

    // Публичный метод для отправки одиночного байта
    public void sendSingleByte(int value) throws IOException {
        if (!isConnected.get()) {
            throw new IllegalStateException("Не подключено к серверу");
        }
        sendByte(value);
    }

    // Публичный метод для получения состояния подключения
    public boolean isConnected() {
        return isConnected.get();
    }

    // Публичный метод для принудительного подключения/отключения
    public void connect(String host, int port) throws IOException {
        if (isConnected.get()) {
            disconnect();
        }

        socket = new Socket(host, port);
        outputStream = socket.getOutputStream();
        isConnected.set(true);

        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Подключено");
            connectButton.setText("Отключить");
            updateButtonStates(true, false);
        });
    }

    public void disconnect() {
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

        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Не подключено");
            connectButton.setText("Подключить");
            updateButtonStates(false, false);
            lineNumberLabel.setText("Строка: 0");
        });
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
    private boolean isSpecialCharacterForIDE(char c) {
        // Расширенный список символов, после которых нужно нажать Del
        return c == '{' ||
                c == '\"' || // двойная кавычка
                c == '\'' || // одинарная кавычка
                c == '[' ||  // открывающая квадратная скобка
                c == '(';    // открывающая круглая скобка
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

            // Обработка IDE режима для символа {
            if (ideCheckBox.isSelected() && isSpecialCharacterForIDE(c)) {
                // Отправляем символ {
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

                // Ждем 0.3 секунды
                Thread.sleep(300);

                // Отправляем кнопку Del (код 212 из KeyCodes.java)
                sendByte(212);
                Thread.sleep(300);
            } else {
                // Стандартная обработка символа
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
            }

            if (c == 10) { // Home
                Thread.sleep(300);
                sendByte(210); // Hom
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
        sendCustomButton.setEnabled(connected && !transmissionRunning);
        testButton.setEnabled(connected);

        if (!transmissionRunning) {
            pauseButton.setText("Пауза");
        }

        hostTextField.setEnabled(!connected);
        portTextField.setEnabled(!connected);
        messageTextArea.setEnabled(connected && !transmissionRunning);
        ideCheckBox.setEnabled(connected && !transmissionRunning);
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