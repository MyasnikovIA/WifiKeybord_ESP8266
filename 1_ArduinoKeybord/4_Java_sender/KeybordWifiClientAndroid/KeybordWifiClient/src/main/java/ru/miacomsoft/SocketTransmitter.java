package ru.miacomsoft;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static ru.miacomsoft.KeyCodes.KEY_CODES_RUS;
import static ru.miacomsoft.KeyCodes.isCyrillic;

public class SocketTransmitter extends JFrame {
    // Константы задержек для удобного редактирования
    private static final int BASE_DELAY = 100;
    private static final int SWITCH_LAYOUT_DELAY = 400;
    private static final int SPECIAL_CHAR_DELAY = 300;
    private static final int VIRTUAL_KEY_DELAY = 50;
    private static final int MIN_DELAY = 50;
    private static final int MAX_DELAY = 500;
    private static final int RANDOM_DELAY_RANGE = 300;

    private JTextField hostTextField;
    private JTextField portTextField;
    private JTextPane messageTextPane; // Изменено на JTextPane
    private JButton pauseButton;
    private JButton stopButton;
    private JButton startButton;
    private JButton connectButton;
    private JButton testButton;
    private JButton sendCustomButton;
    private JLabel lineNumberLabel;
    private JLabel statusLabel;
    private JCheckBox ideCheckBox;
    private JCheckBox virtualKeyboardCheckBox;
    private JLabel virtualKeyboardStatusLabel;
    private JButton toggleLayoutButton;
    private JLabel layoutStatusLabel;
    private JLabel timeRemainingLabel; // Новый элемент для отображения оставшегося времени

    private Socket socket;
    private OutputStream outputStream;
    private Thread transmissionThread;
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private AtomicBoolean isStopped = new AtomicBoolean(false);
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private AtomicBoolean isVirtualKeyboardActive = new AtomicBoolean(false);
    private boolean isEnglish = true;
    private boolean wasTransmissionPausedByVirtualKeyboard = false;

    // Для подсветки отправленных символов
    private int currentTransmissionPosition = 0;
    private int totalCharactersToSend = 0;
    private long transmissionStartTime = 0;
    private long lastUpdateTime = 0;

    public SocketTransmitter() {
        setTitle("Socket Transmitter");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 650);
        setLocationRelativeTo(null);

        initComponents();
        layoutComponents();

        setupGlobalKeyListener();
        setupTextPaneKeyListener(); // Добавляем слушатель для JTextPane

        setVisible(true);
    }

    private void initComponents() {
        hostTextField = new JTextField("192.168.4.1", 15);
        portTextField = new JTextField("8200", 5);

        connectButton = new JButton("Подключить");
        connectButton.addActionListener(e -> toggleConnection());

        testButton = new JButton("test");
        testButton.addActionListener(e -> toggleTest());

        sendCustomButton = new JButton("Отправить кастом");
        sendCustomButton.addActionListener(e -> sendCustomData());

        // Заменяем JTextArea на JTextPane
        messageTextPane = new JTextPane();
        messageTextPane.setFont(new Font("Monospaced", Font.PLAIN, 14));
        messageTextPane.setEditable(true);

        pauseButton = new JButton("Пауза");
        stopButton = new JButton("Стоп");
        startButton = new JButton("Старт");

        lineNumberLabel = new JLabel("Строка: 0");
        statusLabel = new JLabel("Не подключено");
        statusLabel.setForeground(Color.RED);

        timeRemainingLabel = new JLabel("Осталось: --:--");
        timeRemainingLabel.setForeground(new Color(0, 100, 200));
        timeRemainingLabel.setFont(timeRemainingLabel.getFont().deriveFont(Font.PLAIN, 11f));

        ideCheckBox = new JCheckBox("IDE");

        virtualKeyboardCheckBox = new JCheckBox("Виртуальная клавиатура");
        virtualKeyboardCheckBox.addActionListener(e -> toggleVirtualKeyboard());

        virtualKeyboardStatusLabel = new JLabel("Неактивна");
        virtualKeyboardStatusLabel.setForeground(Color.GRAY);
        virtualKeyboardStatusLabel.setFont(virtualKeyboardStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));

        toggleLayoutButton = new JButton("Rus/Lat");
        toggleLayoutButton.setToolTipText("Переключить раскладку виртуальной клавиатуры");
        toggleLayoutButton.addActionListener(e -> toggleKeyboardLayout());
        toggleLayoutButton.setEnabled(false);
        toggleLayoutButton.setFont(toggleLayoutButton.getFont().deriveFont(Font.BOLD, 11f));

        layoutStatusLabel = new JLabel("Lat");
        layoutStatusLabel.setForeground(new Color(0, 100, 200));
        layoutStatusLabel.setFont(layoutStatusLabel.getFont().deriveFont(Font.BOLD, 12f));
        layoutStatusLabel.setPreferredSize(new Dimension(40, 20));

        pauseButton.addActionListener(e -> togglePause());
        stopButton.addActionListener(e -> stopTransmission());
        startButton.addActionListener(e -> startTransmission());

        updateButtonStates(false, false);
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));

        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connectionPanel.add(new JLabel("Хост:"));
        connectionPanel.add(hostTextField);
        connectionPanel.add(new JLabel("Порт:"));
        connectionPanel.add(portTextField);
        connectionPanel.add(connectButton);
        connectionPanel.add(statusLabel);
        connectionPanel.add(testButton);
        connectionPanel.add(sendCustomButton);
        connectionPanel.add(ideCheckBox);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.add(startButton);
        buttonPanel.add(pauseButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(lineNumberLabel);
        buttonPanel.add(timeRemainingLabel);

        JPanel virtualKeyboardPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        virtualKeyboardPanel.setBorder(BorderFactory.createTitledBorder("Виртуальная клавиатура"));
        virtualKeyboardPanel.add(virtualKeyboardCheckBox);
        virtualKeyboardPanel.add(virtualKeyboardStatusLabel);
        virtualKeyboardPanel.add(Box.createHorizontalStrut(10));
        virtualKeyboardPanel.add(new JLabel("Раскладка:"));
        virtualKeyboardPanel.add(layoutStatusLabel);
        virtualKeyboardPanel.add(toggleLayoutButton);

        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.add(buttonPanel, BorderLayout.CENTER);
        controlPanel.add(virtualKeyboardPanel, BorderLayout.EAST);

        add(connectionPanel, BorderLayout.NORTH);
        add(new JScrollPane(messageTextPane), BorderLayout.CENTER); // JTextPane в JScrollPane
        add(controlPanel, BorderLayout.SOUTH);

        ((JComponent)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    // Добавляем специальный слушатель клавиш для JTextPane
    private void setupTextPaneKeyListener() {
        messageTextPane.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                // Если виртуальная клавиатура активна, пропускаем управляющие клавиши
                if (isVirtualKeyboardActive.get()) {
                    // Разрешаем только основные управляющие клавиши для редактирования текста
                    int keyCode = e.getKeyCode();

                    // Разрешаем: Backspace, Delete, стрелки, Home, End
                    boolean allowedKey =
                            keyCode == KeyEvent.VK_BACK_SPACE ||
                                    keyCode == KeyEvent.VK_DELETE ||
                                    keyCode == KeyEvent.VK_LEFT ||
                                    keyCode == KeyEvent.VK_RIGHT ||
                                    keyCode == KeyEvent.VK_UP ||
                                    keyCode == KeyEvent.VK_DOWN ||
                                    keyCode == KeyEvent.VK_HOME ||
                                    keyCode == KeyEvent.VK_END ||
                                    keyCode == KeyEvent.VK_PAGE_UP ||
                                    keyCode == KeyEvent.VK_PAGE_DOWN ||
                                    keyCode == KeyEvent.VK_ENTER ||
                                    keyCode == KeyEvent.VK_TAB ||
                                    e.isControlDown() || // Ctrl+буква
                                    e.isAltDown();       // Alt+буква

                    if (!allowedKey) {
                        e.consume(); // Блокируем другие клавиши при активной виртуальной клавиатуре
                    }
                }
            }

            @Override
            public void keyReleased(java.awt.event.KeyEvent e) {
                // При отпускании клавиши восстанавливаем нормальное поведение
                if (isVirtualKeyboardActive.get()) {
                    // Обновляем позицию курсора для подсветки
                    updateCursorPositionForHighlight();
                }
            }

            @Override
            public void keyTyped(java.awt.event.KeyEvent e) {
                // Если виртуальная клавиатура активна, блокируем обычный ввод символов
                if (isVirtualKeyboardActive.get()) {
                    e.consume();
                }
            }
        });
    }

    // Обновляем позицию курсора для правильной подсветки
    private void updateCursorPositionForHighlight() {
        SwingUtilities.invokeLater(() -> {
            try {
                int caretPos = messageTextPane.getCaretPosition();
                if (caretPos > currentTransmissionPosition) {
                    currentTransmissionPosition = caretPos;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // Метод для подсветки отправленного символа
    private void highlightSentCharacter(int position) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = messageTextPane.getStyledDocument();
                if (position >= 0 && position < doc.getLength()) {
                    // Создаем стиль для подсветки
                    Style style = messageTextPane.addStyle("SentStyle", null);
                    StyleConstants.setForeground(style, new Color(0, 150, 0)); // Зеленый цвет
                    StyleConstants.setBold(style, true);

                    // Применяем стиль к отправленному символу
                    doc.setCharacterAttributes(position, 1, style, false);

                    // Перемещаем курсор на следующий символ
                    messageTextPane.setCaretPosition(Math.min(position + 1, doc.getLength()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // Метод для сброса цвета всего текста в черный
    private void resetAllTextColor() {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = messageTextPane.getStyledDocument();
                Style style = messageTextPane.addStyle("DefaultStyle", null);
                StyleConstants.setForeground(style, Color.BLACK);
                StyleConstants.setBold(style, false);
                doc.setCharacterAttributes(0, doc.getLength(), style, false);

                // Сбрасываем позицию курсора
                currentTransmissionPosition = 0;
                messageTextPane.setCaretPosition(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // Метод для расчета и обновления оставшегося времени
    private void updateRemainingTime(int charsSent, int totalChars) {
        if (totalChars <= 0 || charsSent <= 0) {
            SwingUtilities.invokeLater(() -> {
                timeRemainingLabel.setText("Осталось: --:--");
            });
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (lastUpdateTime == 0) {
            lastUpdateTime = currentTime;
            return;
        }

        long elapsed = currentTime - transmissionStartTime;
        double timePerChar = (double) elapsed / charsSent;
        int remainingChars = totalChars - charsSent;
        long remainingTime = (long) (remainingChars * timePerChar);

        // Форматируем время в минуты:секунды
        long minutes = remainingTime / 60000;
        long seconds = (remainingTime % 60000) / 1000;

        String timeStr = String.format("%02d:%02d", minutes, seconds);

        SwingUtilities.invokeLater(() -> {
            timeRemainingLabel.setText("Осталось: " + timeStr);
        });

        lastUpdateTime = currentTime;
    }

    private void toggleVirtualKeyboard() {
        if (!isConnected.get()) {
            JOptionPane.showMessageDialog(this, "Сначала подключитесь к серверу!");
            virtualKeyboardCheckBox.setSelected(false);
            return;
        }

        boolean activated = virtualKeyboardCheckBox.isSelected();
        isVirtualKeyboardActive.set(activated);

        if (activated) {
            if (transmissionThread != null && transmissionThread.isAlive() && !isPaused.get()) {
                wasTransmissionPausedByVirtualKeyboard = true;
                isPaused.set(true);
                SwingUtilities.invokeLater(() -> {
                    pauseButton.setText("Продолжить");
                    statusLabel.setText("ПАУЗА (вирт. клавиатура)");
                    statusLabel.setForeground(Color.ORANGE);
                });
            } else if (isPaused.get() && !wasTransmissionPausedByVirtualKeyboard) {
                statusLabel.setText("ПАУЗА (вирт. клавиатура)");
                statusLabel.setForeground(Color.ORANGE);
            }

            virtualKeyboardStatusLabel.setText("Активна");
            virtualKeyboardStatusLabel.setForeground(new Color(0, 150, 0));

            toggleLayoutButton.setEnabled(true);
            updateLayoutStatus();

            updateButtonStates(true, transmissionThread != null && transmissionThread.isAlive());

        } else {
            virtualKeyboardStatusLabel.setText("Неактивна");
            virtualKeyboardStatusLabel.setForeground(Color.GRAY);

            toggleLayoutButton.setEnabled(false);

            if (transmissionThread != null && transmissionThread.isAlive() && isPaused.get()
                    && statusLabel.getText().equals("ПАУЗА (вирт. клавиатура)")) {
                statusLabel.setText("ПАУЗА");
                statusLabel.setForeground(Color.RED);
            }

            updateButtonStates(true, transmissionThread != null && transmissionThread.isAlive());
        }

        updateVirtualKeyboardUI();
    }

    private void toggleKeyboardLayout() {
        try {
            if (!isConnected.get()) {
                return;
            }

            if (isEnglish) {
                sendByte(126);
            } else {
                sendByte(96);
            }

            isEnglish = !isEnglish;
            updateLayoutStatus();

            Thread.sleep(SWITCH_LAYOUT_DELAY);

        } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Ошибка переключения раскладки: " + ex.getMessage(),
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    private void updateLayoutStatus() {
        SwingUtilities.invokeLater(() -> {
            if (isEnglish) {
                layoutStatusLabel.setText("Lat");
                layoutStatusLabel.setForeground(new Color(0, 100, 200));
                toggleLayoutButton.setText("Rus/Lat");
            } else {
                layoutStatusLabel.setText("Rus");
                layoutStatusLabel.setForeground(new Color(200, 0, 0));
                toggleLayoutButton.setText("Lat/Rus");
            }
        });
    }

    private void updateVirtualKeyboardUI() {
        boolean active = isVirtualKeyboardActive.get();

        if (active) {
            virtualKeyboardCheckBox.setBackground(new Color(220, 240, 220));
            virtualKeyboardCheckBox.setForeground(Color.BLACK);
        } else {
            virtualKeyboardCheckBox.setBackground(null);
            virtualKeyboardCheckBox.setForeground(null);
        }
    }

    private void setupGlobalKeyListener() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(new KeyEventDispatcher() {
                    @Override
                    public boolean dispatchKeyEvent(KeyEvent e) {
                        // Если виртуальная клавиатура активна и фокус в JTextPane
                        if (isVirtualKeyboardActive.get() && isConnected.get() && SocketTransmitter.this.isActive()) {
                            // Проверяем, имеет ли JTextPane фокус
                            boolean isTextPaneFocused = SwingUtilities.isDescendingFrom(
                                    KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(),
                                    messageTextPane);

                            // Если JTextPane имеет фокус, разрешаем управляющие клавиши
                            if (isTextPaneFocused) {
                                int keyCode = e.getKeyCode();

                                // Разрешаем управляющие клавиши для редактирования текста
                                boolean isEditingKey =
                                        keyCode == KeyEvent.VK_BACK_SPACE ||
                                                keyCode == KeyEvent.VK_DELETE ||
                                                keyCode == KeyEvent.VK_LEFT ||
                                                keyCode == KeyEvent.VK_RIGHT ||
                                                keyCode == KeyEvent.VK_UP ||
                                                keyCode == KeyEvent.VK_DOWN ||
                                                keyCode == KeyEvent.VK_HOME ||
                                                keyCode == KeyEvent.VK_END ||
                                                keyCode == KeyEvent.VK_PAGE_UP ||
                                                keyCode == KeyEvent.VK_PAGE_DOWN ||
                                                keyCode == KeyEvent.VK_ENTER ||
                                                keyCode == KeyEvent.VK_TAB ||
                                                e.isControlDown() || // Ctrl+буква (Ctrl+A, Ctrl+C и т.д.)
                                                e.isAltDown() ||     // Alt+буква
                                                (e.getModifiersEx() & (KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK)) != 0;

                                if (isEditingKey) {
                                    // Разрешаем стандартную обработку управляющих клавиш
                                    return false;
                                }

                                // Для всех других клавиш обрабатываем как виртуальную клавиатуру
                                if (e.getID() == KeyEvent.KEY_RELEASED) {
                                    handleVirtualKeyboardEvent(e);
                                    return true;
                                }
                                if (e.getID() == KeyEvent.KEY_PRESSED && keyCode == KeyEvent.VK_SPACE) {
                                    handleVirtualKeyboardEvent(e);
                                    return true;
                                }
                            } else {
                                // Если фокус не в JTextPane, обрабатываем все как виртуальную клавиатуру
                                if (e.getID() == KeyEvent.KEY_RELEASED) {
                                    handleVirtualKeyboardEvent(e);
                                    return true;
                                }
                                if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_SPACE) {
                                    handleVirtualKeyboardEvent(e);
                                    return true;
                                }
                            }
                            return false;
                        }

                        // ESC для деактивации виртуальной клавиатуры
                        if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                            if (isVirtualKeyboardActive.get()) {
                                SwingUtilities.invokeLater(() -> {
                                    virtualKeyboardCheckBox.setSelected(false);
                                    toggleVirtualKeyboard();
                                });
                                return true;
                            }
                        }

                        // ПРОБЕЛ для паузы/продолжения передачи (только когда виртуальная клавиатура ВЫКЛЮЧЕНА)
                        if (e.getID() == KeyEvent.KEY_PRESSED) {
                            if (e.getKeyCode() == KeyEvent.VK_SPACE &&
                                    SocketTransmitter.this.isActive() &&
                                    isConnected.get() &&
                                    transmissionThread != null &&
                                    transmissionThread.isAlive() &&
                                    !isVirtualKeyboardActive.get()) {

                                SwingUtilities.invokeLater(() -> togglePause());
                                return true;
                            }
                        }
                        return false;
                    }
                });
    }

    private void handleVirtualKeyboardEvent(KeyEvent e) {
        int keyCode = e.getKeyCode();
        boolean isSpecialKey = isSpecialActionKey(keyCode);

        if ((e.getID() == KeyEvent.KEY_RELEASED && !isSpecialKey) ||
                (e.getID() == KeyEvent.KEY_PRESSED && (isSpecialKey || keyCode == KeyEvent.VK_SPACE))) {

            try {
                if (!SocketTransmitter.this.isActive()) {
                    return;
                }

                // Проверяем, не находится ли фокус в JTextPane с нажатой управляющей клавишей
                boolean isTextPaneFocused = SwingUtilities.isDescendingFrom(
                        KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(),
                        messageTextPane);

                // Если фокус в JTextPane и это управляющая клавиша, пропускаем
                if (isTextPaneFocused &&
                        (keyCode == KeyEvent.VK_BACK_SPACE ||
                                keyCode == KeyEvent.VK_DELETE ||
                                keyCode == KeyEvent.VK_LEFT ||
                                keyCode == KeyEvent.VK_RIGHT ||
                                keyCode == KeyEvent.VK_UP ||
                                keyCode == KeyEvent.VK_DOWN ||
                                keyCode == KeyEvent.VK_HOME ||
                                keyCode == KeyEvent.VK_END ||
                                keyCode == KeyEvent.VK_PAGE_UP ||
                                keyCode == KeyEvent.VK_PAGE_DOWN ||
                                e.isControlDown() ||
                                e.isAltDown())) {
                    return;
                }

                if (keyCode == KeyEvent.VK_ESCAPE) {
                    SwingUtilities.invokeLater(() -> {
                        virtualKeyboardCheckBox.setSelected(false);
                        toggleVirtualKeyboard();
                    });
                    return;
                }

                if (keyCode == KeyEvent.VK_SPACE && e.getID() == KeyEvent.KEY_PRESSED) {
                    try {
                        sendByte(180);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(SocketTransmitter.this,
                                    "Ошибка отправки пробела: " + ex.getMessage(),
                                    "Ошибка",
                                    JOptionPane.ERROR_MESSAGE);
                        });
                    }
                    return;
                }

                if (isSpecialKey && e.getID() == KeyEvent.KEY_PRESSED) {
                    processVirtualKeyPress(e);
                    return;
                }

                if (!isSpecialKey && e.getID() == KeyEvent.KEY_RELEASED) {
                    processVirtualKeyRelease(e);
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(SocketTransmitter.this,
                            "Ошибка виртуальной клавиатуры: " + ex.getMessage(),
                            "Ошибка",
                            JOptionPane.ERROR_MESSAGE);
                });
            }
        }
    }

    private boolean isSpecialActionKey(int keyCode) {
        return keyCode == KeyEvent.VK_ENTER ||
                keyCode == KeyEvent.VK_BACK_SPACE ||
                keyCode == KeyEvent.VK_DELETE ||
                keyCode == KeyEvent.VK_UP ||
                keyCode == KeyEvent.VK_DOWN ||
                keyCode == KeyEvent.VK_LEFT ||
                keyCode == KeyEvent.VK_RIGHT ||
                keyCode == KeyEvent.VK_HOME ||
                keyCode == KeyEvent.VK_END ||
                keyCode == KeyEvent.VK_PAGE_UP ||
                keyCode == KeyEvent.VK_PAGE_DOWN ||
                keyCode == KeyEvent.VK_INSERT ||
                keyCode == KeyEvent.VK_TAB ||
                keyCode == KeyEvent.VK_CAPS_LOCK ||
                (keyCode >= KeyEvent.VK_F1 && keyCode <= KeyEvent.VK_F12) ||
                keyCode == KeyEvent.VK_NUM_LOCK;
    }

    private void processVirtualKeyRelease(KeyEvent e) throws IOException, InterruptedException {
        int keyCode = e.getKeyCode();
        char keyChar = e.getKeyChar();

        // Проверяем, не находится ли фокус в JTextPane
        boolean isTextPaneFocused = SwingUtilities.isDescendingFrom(
                KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(),
                messageTextPane);

        // Если фокус в JTextPane и это клавиша-модификатор, пропускаем
        if (isTextPaneFocused && (keyCode == KeyEvent.VK_SHIFT ||
                keyCode == KeyEvent.VK_CONTROL ||
                keyCode == KeyEvent.VK_ALT ||
                keyCode == KeyEvent.VK_WINDOWS)) {
            return;
        }

        System.out.println("Виртуальная клавиатура (отпускание): keyCode=" + keyCode +
                ", char=" + keyChar + ", shift=" + e.isShiftDown());

        if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) {
            char digit = (char)('0' + (keyCode - KeyEvent.VK_0));
            handleCharacter(digit, e.isShiftDown());
        }
        else if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) {
            char baseLetter = (char)('a' + (keyCode - KeyEvent.VK_A));
            char letter = e.isShiftDown() ? Character.toUpperCase(baseLetter) : baseLetter;
            handleCharacter(letter, e.isShiftDown());
        }
        else if (keyChar != KeyEvent.CHAR_UNDEFINED && keyChar >= 32) {
            handleCharacter(keyChar, e.isShiftDown());
        }
        else {
            char mappedChar = mapKeyCodeToChar(keyCode, e.isShiftDown());
            if (mappedChar != 0) {
                handleCharacter(mappedChar, e.isShiftDown());
            } else {
                handleKeyCode(keyCode, e.isShiftDown());
            }
        }

        Thread.sleep(VIRTUAL_KEY_DELAY);
    }

    private char mapKeyCodeToChar(int keyCode, boolean isShift) {
        switch (keyCode) {
            case KeyEvent.VK_SPACE: return ' ';
            case KeyEvent.VK_COMMA: return isShift ? '<' : ',';
            case KeyEvent.VK_PERIOD: return isShift ? '>' : '.';
            case KeyEvent.VK_SLASH: return isShift ? '?' : '/';
            case KeyEvent.VK_SEMICOLON: return isShift ? ':' : ';';
            case KeyEvent.VK_EQUALS: return isShift ? '+' : '=';
            case KeyEvent.VK_MINUS: return isShift ? '_' : '-';
            case KeyEvent.VK_BACK_QUOTE: return isShift ? '~' : '`';
            case KeyEvent.VK_QUOTE: return isShift ? '"' : '\'';
            case KeyEvent.VK_OPEN_BRACKET: return isShift ? '{' : '[';
            case KeyEvent.VK_CLOSE_BRACKET: return isShift ? '}' : ']';
            case KeyEvent.VK_BACK_SLASH: return isShift ? '|' : '\\';
            case KeyEvent.VK_MULTIPLY: return '*';
            case KeyEvent.VK_ADD: return '+';
            case KeyEvent.VK_SUBTRACT: return '-';
            case KeyEvent.VK_DIVIDE: return '/';
            case KeyEvent.VK_DECIMAL: return '.';
            case KeyEvent.VK_SEPARATOR: return ',';
            default: return 0;
        }
    }

    private void processVirtualKeyPress(KeyEvent e) throws IOException, InterruptedException {
        int keyCode = e.getKeyCode();
        char keyChar = e.getKeyChar();

        System.out.println("Виртуальная клавиатура (нажатие специальной клавиши): keyCode=" + keyCode +
                ", char=" + keyChar + ", isActionKey=" + e.isActionKey());

        if (keyCode == 127 || keyCode == KeyEvent.VK_DELETE) {
            sendByte(212);
        } else if (e.isActionKey() || isSpecialActionKey(keyCode)) {
            handleActionKey(keyCode);
        }

        Thread.sleep(VIRTUAL_KEY_DELAY);
    }

    private void handleActionKey(int keyCode) throws IOException {
        switch (keyCode) {
            case KeyEvent.VK_ENTER:
                sendByte(176);
                break;
            case KeyEvent.VK_BACK_SPACE:
                sendByte(178);
                break;
            case KeyEvent.VK_TAB:
                sendByte(179);
                break;
            case KeyEvent.VK_SPACE:
                sendByte(180);
                break;
            case KeyEvent.VK_CAPS_LOCK:
                sendByte(134);
                isEnglish = !isEnglish;
                updateLayoutStatus();
                break;
            case KeyEvent.VK_HOME:
                sendByte(210);
                break;
            case KeyEvent.VK_END:
                sendByte(213);
                break;
            case KeyEvent.VK_PAGE_UP:
                sendByte(211);
                break;
            case KeyEvent.VK_PAGE_DOWN:
                sendByte(214);
                break;
            case KeyEvent.VK_UP:
                sendByte(218);
                break;
            case KeyEvent.VK_DOWN:
                sendByte(217);
                break;
            case KeyEvent.VK_LEFT:
                sendByte(216);
                break;
            case KeyEvent.VK_RIGHT:
                sendByte(215);
                break;
            case KeyEvent.VK_INSERT:
                sendByte(209);
                break;
            case KeyEvent.VK_DELETE:
                sendByte(212);
                break;
            case KeyEvent.VK_F1: sendByte(194); break;
            case KeyEvent.VK_F2: sendByte(195); break;
            case KeyEvent.VK_F3: sendByte(196); break;
            case KeyEvent.VK_F4: sendByte(197); break;
            case KeyEvent.VK_F5: sendByte(198); break;
            case KeyEvent.VK_F6: sendByte(199); break;
            case KeyEvent.VK_F7: sendByte(200); break;
            case KeyEvent.VK_F8: sendByte(201); break;
            case KeyEvent.VK_F9: sendByte(202); break;
            case KeyEvent.VK_F10: sendByte(203); break;
            case KeyEvent.VK_F11: sendByte(204); break;
            case KeyEvent.VK_F12: sendByte(205); break;
            case KeyEvent.VK_NUM_LOCK: sendByte(219); break;
        }
    }

    private void handleCharacter(char keyChar, boolean isShift) throws IOException, InterruptedException {
        System.out.println("Обработка символа: '" + keyChar + "' (код: " + (int)keyChar + "), shift=" + isShift + ", английская раскладка=" + isEnglish);

        if (keyChar < 32) {
            handleControlCharacter(keyChar);
            return;
        }

        if (isLayoutSwitchChar(keyChar)) {
            if (keyChar == 'ё' || keyChar == 'Ё') {
                handleYoChar(keyChar);
            } else {
                sendByte(keyChar);
                isEnglish = !isEnglish;
                updateLayoutStatus();
                Thread.sleep(SWITCH_LAYOUT_DELAY);
            }
            return;
        }

        if (ideCheckBox.isSelected() && isSpecialCharacterForIDE(keyChar)) {
            handleSpecialCharacterForIDE(keyChar);
            return;
        }

        boolean isRussian = isCyrillic(keyChar);
        if (isRussian) {
            if (isEnglish) {
                switchLayout();
            }
            sendRussianChar(keyChar);
        } else {
            if (!isEnglish) {
                switchLayout();
            }

            char charToSend = keyChar;
            if (isShift && Character.isLetter(keyChar)) {
                charToSend = Character.toUpperCase(keyChar);
            }

            if (charToSend < 128) {
                sendByte(charToSend);
                System.out.println("Отправлен символ '" + charToSend + "' как код: " + (int)charToSend);
            } else {
                System.out.println("Пропущен символ '" + charToSend + "' с кодом: " + (int)charToSend + " (вне диапазона ASCII)");
            }
        }
    }

    private void handleControlCharacter(char controlChar) throws IOException {
        switch ((int)controlChar) {
            case 8:  // Backspace
                sendByte(178);
                System.out.println("Обработан Backspace как код: 178");
                break;
            case 9:  // Tab
                sendByte(179);
                System.out.println("Обработан Tab как код: 179");
                break;
            case 10: // Line Feed (Enter)
                sendByte(176);
                System.out.println("Обработан Enter как код: 176");
                break;
            case 13: // Carriage Return (Enter)
                sendByte(176);
                System.out.println("Обработан Enter как код: 176");
                break;
            case 27: // Escape
                break;
            case 127: // Delete (DEL)
                sendByte(212);
                System.out.println("Обработан Delete как код: 212");
                break;
            default:
                System.out.println("Необработанный управляющий символ: " + (int)controlChar);
                break;
        }
    }

    private void handleYoChar(char yoChar) throws IOException, InterruptedException {
        System.out.println("Обработка буквы Ё/ё: '" + yoChar + "'");

        if (yoChar == 'ё') {
            sendByte(96);
        } else if (yoChar == 'Ё') {
            sendByte(126);
        }

        if (isEnglish) {
            isEnglish = false;
            updateLayoutStatus();
        }
        Thread.sleep(SWITCH_LAYOUT_DELAY);
    }

    private void sendRussianChar(char russianChar) throws IOException {
        String charStr = String.valueOf(russianChar);
        if (KEY_CODES_RUS.containsKey(charStr)) {
            int code = KEY_CODES_RUS.get(charStr);
            sendByte(code);
            System.out.println("Отправлен русский символ '" + russianChar + "' как код: " + code);
        } else {
            System.out.println("Русский символ '" + russianChar + "' не найден в KEY_CODES_RUS");
        }
    }

    private void handleKeyCode(int keyCode, boolean isShift) throws IOException, InterruptedException {
        System.out.println("Обработка keyCode: " + keyCode + ", shift=" + isShift);

        switch (keyCode) {
            case KeyEvent.VK_SHIFT:
            case KeyEvent.VK_CONTROL:
            case KeyEvent.VK_ALT:
            case KeyEvent.VK_WINDOWS:
                break;

            case KeyEvent.VK_DECIMAL:
            case KeyEvent.VK_SEPARATOR:
                sendByte('.');
                break;

            case KeyEvent.VK_MULTIPLY:
                sendByte('*');
                break;

            case KeyEvent.VK_ADD:
                sendByte('+');
                break;

            case KeyEvent.VK_SUBTRACT:
                sendByte('-');
                break;

            case KeyEvent.VK_DIVIDE:
                sendByte('/');
                break;

            default:
                if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) {
                    char digit = (char)('0' + (keyCode - KeyEvent.VK_0));
                    sendByte(digit);
                }
                else if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) {
                    char letter = (char)('a' + (keyCode - KeyEvent.VK_A));
                    if (isShift) {
                        letter = Character.toUpperCase(letter);
                    }

                    boolean isRussian = !isEnglish;
                    if (isRussian) {
                        sendRussianCharFromEnglishKey(keyCode, isShift);
                    } else {
                        sendByte(letter);
                    }
                }
                break;
        }
    }

    private void sendRussianCharFromEnglishKey(int keyCode, boolean isShift) throws IOException {
        String englishChar;
        switch (keyCode) {
            case KeyEvent.VK_A: englishChar = isShift ? "Ф" : "ф"; break;
            case KeyEvent.VK_B: englishChar = isShift ? "И" : "и"; break;
            case KeyEvent.VK_C: englishChar = isShift ? "С" : "с"; break;
            case KeyEvent.VK_D: englishChar = isShift ? "В" : "в"; break;
            case KeyEvent.VK_E: englishChar = isShift ? "У" : "у"; break;
            case KeyEvent.VK_F: englishChar = isShift ? "А" : "а"; break;
            case KeyEvent.VK_G: englishChar = isShift ? "П" : "п"; break;
            case KeyEvent.VK_H: englishChar = isShift ? "Р" : "р"; break;
            case KeyEvent.VK_I: englishChar = isShift ? "Ш" : "ш"; break;
            case KeyEvent.VK_J: englishChar = isShift ? "О" : "о"; break;
            case KeyEvent.VK_K: englishChar = isShift ? "Л" : "л"; break;
            case KeyEvent.VK_L: englishChar = isShift ? "Д" : "д"; break;
            case KeyEvent.VK_M: englishChar = isShift ? "Ь" : "ь"; break;
            case KeyEvent.VK_N: englishChar = isShift ? "Т" : "т"; break;
            case KeyEvent.VK_O: englishChar = isShift ? "Щ" : "щ"; break;
            case KeyEvent.VK_P: englishChar = isShift ? "З" : "з"; break;
            case KeyEvent.VK_Q: englishChar = isShift ? "Й" : "й"; break;
            case KeyEvent.VK_R: englishChar = isShift ? "К" : "к"; break;
            case KeyEvent.VK_S: englishChar = isShift ? "Ы" : "ы"; break;
            case KeyEvent.VK_T: englishChar = isShift ? "Е" : "е"; break;
            case KeyEvent.VK_U: englishChar = isShift ? "Г" : "г"; break;
            case KeyEvent.VK_V: englishChar = isShift ? "М" : "м"; break;
            case KeyEvent.VK_W: englishChar = isShift ? "Ц" : "ц"; break;
            case KeyEvent.VK_X: englishChar = isShift ? "Ч" : "ч"; break;
            case KeyEvent.VK_Y: englishChar = isShift ? "Н" : "н"; break;
            case KeyEvent.VK_Z: englishChar = isShift ? "Я" : "я"; break;
            default: englishChar = "";
        }

        if (!englishChar.isEmpty() && KEY_CODES_RUS.containsKey(englishChar)) {
            int code = KEY_CODES_RUS.get(englishChar);
            sendByte(code);
            System.out.println("Отправлен русский символ '" + englishChar + "' как код: " + code);
        }
    }

    private void handleSpecialCharacterForIDE(char c) throws IOException, InterruptedException {
        System.out.println("Обработка спецсимвола для IDE: '" + c + "'");

        boolean isRussian = isCyrillic(c);
        if (isRussian) {
            if (isEnglish) {
                switchLayout();
            }
            sendRussianChar(c);
        } else {
            if (!isEnglish) {
                switchLayout();
            }
            sendByte(c);
        }

        Thread.sleep(SPECIAL_CHAR_DELAY);
        sendByte(212);
    }

    private boolean isLayoutSwitchChar(char c) {
        return c == '~' || c == 'Ё' || c == 'ё' || c == '`';
    }

    private void switchLayout() throws IOException, InterruptedException {
        System.out.println("Переключение раскладки с " + (isEnglish ? "английской" : "русской") + " на " + (!isEnglish ? "английскую" : "русскую"));

        if (isEnglish) {
            sendByte(96);
        } else {
            sendByte(96);
        }
        isEnglish = !isEnglish;
        updateLayoutStatus();
        Thread.sleep(SWITCH_LAYOUT_DELAY);
    }

    private boolean isSpecialCharacterForIDE(char c) {
        return c == '{' ||
                c == '\"' ||
                c == '\'' ||
                c == '[' ||
                c == '(';
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
            sendTestByte();
        }
    }

    private void sendCustomData() {
        if (!isConnected.get()) {
            JOptionPane.showMessageDialog(this, "Сначала подключитесь к серверу!");
            return;
        }

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
                            sendCustomText(input);
                        } else {
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
            sendByte(96);
            JOptionPane.showMessageDialog(this, "Тестовый байт (96) отправлен!");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Ошибка отправки тестового байта: " + ex.getMessage());
        }
    }

    public void sendCustomText(String text) throws IOException, InterruptedException {
        if (!isConnected.get()) {
            throw new IllegalStateException("Не подключено к серверу");
        }

        boolean wasEnglish = isEnglish;
        isEnglish = true;
        updateLayoutStatus();

        try {
            for (char c : text.toCharArray()) {
                if (isLayoutSwitchChar(c)) {
                    if (c == 'ё' || c == 'Ё') {
                        handleYoChar(c);
                    } else {
                        sendByte(c);
                        isEnglish = !isEnglish;
                        updateLayoutStatus();
                        Thread.sleep(SWITCH_LAYOUT_DELAY / 2);
                    }
                    continue;
                }

                if (ideCheckBox.isSelected() && isSpecialCharacterForIDE(c)) {
                    boolean isRussian = isCyrillic(c);
                    if (isRussian) {
                        if (isEnglish) {
                            switchLayout();
                        }
                        sendRussianChar(c);
                    } else {
                        if (!isEnglish) {
                            switchLayout();
                        }
                        sendByte(c);
                    }

                    Thread.sleep(SPECIAL_CHAR_DELAY);
                    sendByte(212);
                } else {
                    boolean isRussian = isCyrillic(c);
                    if (isRussian) {
                        if (isEnglish) {
                            switchLayout();
                        }
                        sendRussianChar(c);
                    } else {
                        if (!isEnglish) {
                            switchLayout();
                        }
                        sendByte(c);
                    }
                }
                Thread.sleep(BASE_DELAY);
            }

            if (!isEnglish && wasEnglish) {
                Thread.sleep(SWITCH_LAYOUT_DELAY / 2);
                switchLayout();
            }

            isEnglish = wasEnglish;
            updateLayoutStatus();

        } catch (Exception e) {
            isEnglish = wasEnglish;
            updateLayoutStatus();
            throw e;
        }
    }

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
                Thread.sleep(BASE_DELAY);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Неверный формат байта: " + str);
            }
        }
    }

    public void sendCustomBytes(byte[] bytes) throws IOException, InterruptedException {
        if (!isConnected.get()) {
            throw new IllegalStateException("Не подключено к серверу");
        }

        for (byte b : bytes) {
            sendByte(b & 0xFF);
            Thread.sleep(BASE_DELAY);
        }
    }

    public void sendSingleByte(int value) throws IOException {
        if (!isConnected.get()) {
            throw new IllegalStateException("Не подключено к серверу");
        }
        sendByte(value);
    }

    public boolean isConnected() {
        return isConnected.get();
    }

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
        isVirtualKeyboardActive.set(false);
        virtualKeyboardCheckBox.setSelected(false);
        updateVirtualKeyboardUI();
        wasTransmissionPausedByVirtualKeyboard = false;

        toggleLayoutButton.setEnabled(false);

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
            virtualKeyboardStatusLabel.setText("Неактивна");
            virtualKeyboardStatusLabel.setForeground(Color.GRAY);
            layoutStatusLabel.setText("Lat");
            layoutStatusLabel.setForeground(new Color(0, 100, 200));
            timeRemainingLabel.setText("Осталось: --:--");
            resetAllTextColor();
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

        String message = messageTextPane.getText();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Введите текст для отправки!");
            return;
        }

        if (isVirtualKeyboardActive.get()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Виртуальная клавиатура активна. Во время передачи текста нажатия клавиш будут игнорироваться. Продолжить?",
                    "Виртуальная клавиатура активна",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        isStopped.set(false);
        isPaused.set(false);
        isEnglish = true;
        updateLayoutStatus();

        // Сбрасываем позицию отправки и подсветку
        currentTransmissionPosition = 0;
        totalCharactersToSend = message.replaceAll("[\\p{C}&&[^\n]]", "").length();
        transmissionStartTime = System.currentTimeMillis();
        lastUpdateTime = 0;

        // Сбрасываем цвет всего текста в черный перед началом передачи
        resetAllTextColor();

        updateButtonStates(true, true);

        transmissionThread = new Thread(() -> {
            try {
                transmitMessage(message);
                SwingUtilities.invokeLater(() -> {
                    messageTextPane.setText("");
                    currentTransmissionPosition = 0;
                    timeRemainingLabel.setText("Осталось: 00:00");
                });
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
                    currentTransmissionPosition = 0;
                });
            }
        });

        transmissionThread.start();
    }

    private void transmitMessage(String message) throws InterruptedException, IOException {
        message = message.replaceAll("[\\p{C}&&[^\n]]", "");
        message = message.replaceAll("(?m)^[ \\t]+$", "");
        String[] lines = message.split("\n");
        int finalLineNum = 1;
        int charsSent = 0;

        SwingUtilities.invokeLater(() -> lineNumberLabel.setText("Строка: 1 из " + lines.length));

        for (char c : message.toCharArray()) {
            if (isStopped.get()) {
                break;
            }

            // ОСНОВНОЙ МЕХАНИЗМ ПАУЗЫ: если isPaused = true, то входим в цикл ожидания
            while (isPaused.get() && !isStopped.get()) {
                Thread.sleep(100);
            }

            if (isStopped.get()) {
                break;
            }

            System.out.println("Отправка символа: " + c + " (код: " + (int)c + ")");

            if (c == 10) {
                Thread.sleep(BASE_DELAY);
                sendByte(32);
                Thread.sleep(BASE_DELAY);
                finalLineNum += 1;
                int finalLineNum1 = finalLineNum;
                SwingUtilities.invokeLater(() -> lineNumberLabel.setText("Строка: " + finalLineNum1 + " из " + lines.length));
            }

            if (isLayoutSwitchChar(c)) {
                if (c == 'ё' || c == 'Ё') {
                    handleYoChar(c);
                } else {
                    sendByte(c);
                    isEnglish = !isEnglish;
                    updateLayoutStatus();
                    Thread.sleep(SWITCH_LAYOUT_DELAY);
                }
                continue;
            }

            if (ideCheckBox.isSelected() && isSpecialCharacterForIDE(c)) {
                boolean isRussian = isCyrillic(c);
                if (isRussian) {
                    if (isEnglish) {
                        switchLayout();
                    }
                    sendRussianChar(c);
                } else {
                    if (!isEnglish) {
                        switchLayout();
                    }
                    sendByte(c);
                }

                Thread.sleep(SPECIAL_CHAR_DELAY);
                sendByte(212);
                Thread.sleep(SPECIAL_CHAR_DELAY);
            } else {
                boolean isRussian = isCyrillic(c);
                if (isRussian) {
                    if (isEnglish) {
                        switchLayout();
                    }
                    sendRussianChar(c);
                } else {
                    if (!isEnglish) {
                        switchLayout();
                    }
                    sendByte(c);
                }
            }

            // Подсвечиваем отправленный символ
            highlightSentCharacter(currentTransmissionPosition);
            currentTransmissionPosition++;
            charsSent++;

            // Обновляем расчет оставшегося времени каждые 10 символов
            if (charsSent % 10 == 0) {
                updateRemainingTime(charsSent, totalCharactersToSend);
            }

            if (c == 10) {
                Thread.sleep(SPECIAL_CHAR_DELAY);
                sendByte(210);
                Thread.sleep(SPECIAL_CHAR_DELAY);
            }
        }

        if (!isEnglish) {
            Thread.sleep(SWITCH_LAYOUT_DELAY / 2);
            switchLayout();
        }

        // Финальное обновление времени
        SwingUtilities.invokeLater(() -> {
            timeRemainingLabel.setText("Осталось: 00:00");
        });
    }

    private void togglePause() {
        if (!isConnected.get() ||
                transmissionThread == null ||
                !transmissionThread.isAlive()) {
            return;
        }

        // Если виртуальная клавиатура активна и мы нажимаем паузу,
        // сначала деактивируем виртуальную клавиатуру
        if (isPaused.get() && isVirtualKeyboardActive.get()) {
            SwingUtilities.invokeLater(() -> {
                virtualKeyboardCheckBox.setSelected(false);
                toggleVirtualKeyboard();
            });
        }

        // Переключаем состояние паузы
        isPaused.set(!isPaused.get());
        pauseButton.setText(isPaused.get() ? "Продолжить" : "Пауза");

        SwingUtilities.invokeLater(() -> {
            if (isPaused.get()) {
                // Устанавливаем статус "ПАУЗА"
                statusLabel.setText("ПАУЗА");
                statusLabel.setForeground(Color.RED);
                statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));

                // Визуальный эффект мигания (опционально)
                new Thread(() -> {
                    try {
                        for (int i = 0; i < 3 && isPaused.get(); i++) {
                            statusLabel.setForeground(i % 2 == 0 ? Color.RED : Color.YELLOW);
                            Thread.sleep(300);
                        }
                    } catch (InterruptedException ignored) {}
                }).start();

            } else {
                // Возвращаем нормальный статус
                statusLabel.setText("Подключено");
                statusLabel.setForeground(new Color(0, 150, 0));
                statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN));
            }
        });
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

        virtualKeyboardCheckBox.setEnabled(connected);
        toggleLayoutButton.setEnabled(connected && isVirtualKeyboardActive.get());

        if (!transmissionRunning) {
            pauseButton.setText("Пауза");
        }

        hostTextField.setEnabled(!connected);
        portTextField.setEnabled(!connected);
        messageTextPane.setEditable(connected && !transmissionRunning);
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
            System.out.println("Отправлен байт: " + value + " (0x" + Integer.toHexString(value) + ")");
        }

        int delay = getRandomDelay(BASE_DELAY);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Transmission interrupted", e);
        }
    }

    private int getRandomDelay(int baseDelay) {
        int randomOffset = (int) (Math.random() * RANDOM_DELAY_RANGE) - (RANDOM_DELAY_RANGE / 2);
        int delay = baseDelay + randomOffset;

        if (delay < MIN_DELAY) {
            delay = MIN_DELAY;
        }

        if (delay > MAX_DELAY) {
            delay = MAX_DELAY;
        }

        return delay;
    }

    @Override
    public void dispose() {
        disconnect();
        super.dispose();
    }
}