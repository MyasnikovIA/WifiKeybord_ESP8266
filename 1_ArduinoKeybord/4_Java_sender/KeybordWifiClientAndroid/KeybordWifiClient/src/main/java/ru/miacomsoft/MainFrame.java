package ru.miacomsoft;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class MainFrame extends JFrame {
    private TransmissionModel model;

    // GUI компоненты
    private JTextField hostTextField;
    private JTextField portTextField;
    private JTextPane messageTextPane;
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
    private JLabel timeRemainingLabel;
    private JButton directKeyboardButton;
    private JLabel directKeyboardStatusLabel;
    private JButton switchLanguageButton;
    private JLabel languageSwitchStatusLabel;
    private Timer languageSwitchTimer;
    private JSlider speedSlider;
    private JLabel speedValueLabel;

    // Режимы
    private JRadioButton wifiModeRadio;
    private JRadioButton serialModeRadio;
    private JComboBox<String> serialPortCombo;
    private JComboBox<Integer> serialBaudCombo;
    private JButton refreshSerialButton;

    private JPanel wifiPanel;
    private JPanel serialPanel;

    public MainFrame() {
        model = new TransmissionModel();
        initComponents();  // Сначала инициализируем компоненты
        setupCallbacks();  // Потом настраиваем callbacks
        layoutComponents();
        setupGlobalKeyListener();
        setupTextPaneKeyListener();
        setVisible(true);
    }

    private void setupCallbacks() {
        model.setOnStatusUpdate(() -> {
            if (model.isPaused()) {
                statusLabel.setText("ПАУЗА");
                statusLabel.setForeground(Color.RED);
            } else if (model.isConnected()) {
                statusLabel.setText(model.isWifiMode() ? "Подключено (WiFi)" : "Подключено (Serial)");
                statusLabel.setForeground(new Color(0, 150, 0));
            } else {
                statusLabel.setText(model.isWifiMode() ? "Режим WiFi" : "Режим Serial");
                statusLabel.setForeground(Color.BLUE);
            }
        });

        model.setOnConnectionStateChange(() -> {
            boolean connected = model.isConnected();
            connectButton.setText(connected ? "Отключить" : "Подключить");
            updateButtonStates(connected, model.getTransmissionThread() != null && model.getTransmissionThread().isAlive());
            virtualKeyboardCheckBox.setEnabled(connected);
            toggleLayoutButton.setEnabled(connected && model.isVirtualKeyboardActive());
            switchLanguageButton.setEnabled(connected);

            wifiModeRadio.setEnabled(!connected);
            serialModeRadio.setEnabled(!connected);
            hostTextField.setEnabled(!connected && model.isWifiMode());
            portTextField.setEnabled(!connected && model.isWifiMode());
            serialPortCombo.setEnabled(!connected && !model.isWifiMode());
            serialBaudCombo.setEnabled(!connected && !model.isWifiMode());
            refreshSerialButton.setEnabled(!connected && !model.isWifiMode());
            messageTextPane.setEditable(connected && (model.getTransmissionThread() == null || !model.getTransmissionThread().isAlive()));
            ideCheckBox.setEnabled(connected && (model.getTransmissionThread() == null || !model.getTransmissionThread().isAlive()));

            if (!connected) {
                virtualKeyboardCheckBox.setSelected(false);
                virtualKeyboardStatusLabel.setText("Неактивна");
                virtualKeyboardStatusLabel.setForeground(Color.GRAY);
                layoutStatusLabel.setText("Lat");
                layoutStatusLabel.setForeground(new Color(0, 100, 200));
                timeRemainingLabel.setText("Осталось: --:--");
                resetAllTextColor();
            }
        });

        model.setOnLayoutChanged(() -> {
            if (model.isEnglish()) {
                layoutStatusLabel.setText("Lat");
                layoutStatusLabel.setForeground(new Color(0, 100, 200));
                toggleLayoutButton.setText("Rus/Lat");
            } else {
                layoutStatusLabel.setText("Rus");
                layoutStatusLabel.setForeground(new Color(200, 0, 0));
                toggleLayoutButton.setText("Lat/Rus");
            }
        });

        model.setOnLanguageSwitch(() -> {
            showLanguageSwitchNotification();
        });

        model.setOnVirtualKeyboardStateChange(() -> {
            boolean active = model.isVirtualKeyboardActive();
            if (active) {
                virtualKeyboardStatusLabel.setText("Активна");
                virtualKeyboardStatusLabel.setForeground(new Color(0, 150, 0));
                toggleLayoutButton.setEnabled(true);
            } else {
                virtualKeyboardStatusLabel.setText("Неактивна");
                virtualKeyboardStatusLabel.setForeground(Color.GRAY);
                toggleLayoutButton.setEnabled(false);
            }

            boolean directActive = model.isDirectKeyboardActive();
            if (directActive) {
                directKeyboardButton.setBackground(new Color(100, 255, 100));
                directKeyboardButton.setText("⌨️ Прямая клавиатура (Вкл)");
                directKeyboardStatusLabel.setText("Вкл");
                directKeyboardStatusLabel.setForeground(new Color(0, 180, 0));
            } else {
                directKeyboardButton.setBackground(new Color(255, 200, 100));
                directKeyboardButton.setText("⌨️ Прямая клавиатура");
                directKeyboardStatusLabel.setText("Выкл");
                directKeyboardStatusLabel.setForeground(Color.GRAY);
            }
        });

        model.setOnError(message -> {
            JOptionPane.showMessageDialog(this, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
        });

        model.setOnLineNumberUpdate(lineNum -> {
            lineNumberLabel.setText("Строка: " + lineNum);
        });

        model.setOnCharacterHighlight((position, length) -> {
            highlightSentCharacter(position);
        });

        model.setOnTimeRemainingUpdate(time -> {
            timeRemainingLabel.setText(time);
        });

        model.setOnAllTextReset(() -> {
            resetAllTextColor();
        });

        model.setOnButtonStatesUpdate(transmissionRunning -> {
            updateButtonStates(model.isConnected(), transmissionRunning);
            messageTextPane.setEditable(model.isConnected() && !transmissionRunning);
            ideCheckBox.setEnabled(model.isConnected() && !transmissionRunning);
        });

        // Синхронизация начального состояния IDE режима (теперь ideCheckBox не null)
        if (ideCheckBox != null) {
            model.setIdeMode(ideCheckBox.isSelected());
        }
    }

    private void initComponents() {
        hostTextField = new JTextField("192.168.4.1", 15);
        portTextField = new JTextField("8200", 5);

        connectButton = new JButton("Подключить");
        connectButton.addActionListener(e -> toggleConnection());

        testButton = new JButton("test");
        testButton.addActionListener(e -> sendTestByte());

        sendCustomButton = new JButton("Отправить кастом");
        sendCustomButton.addActionListener(e -> sendCustomData());

        messageTextPane = new JTextPane();
        messageTextPane.setFont(new Font("Monospaced", Font.PLAIN, 14));
        messageTextPane.setEditable(true);

        pauseButton = new JButton("Пауза");
        stopButton = new JButton("Стоп");
        startButton = new JButton("Старт");

        lineNumberLabel = new JLabel("Строка: 0");
        statusLabel = new JLabel("Режим WiFi");
        statusLabel.setForeground(Color.BLUE);

        timeRemainingLabel = new JLabel("Осталось: --:--");
        timeRemainingLabel.setForeground(new Color(0, 100, 200));
        timeRemainingLabel.setFont(timeRemainingLabel.getFont().deriveFont(Font.PLAIN, 11f));

        ideCheckBox = new JCheckBox("IDE");
        ideCheckBox.addActionListener(e -> {
            model.setIdeMode(ideCheckBox.isSelected());
        });

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

        pauseButton.addActionListener(e -> model.togglePause());
        stopButton.addActionListener(e -> model.stopTransmission());
        startButton.addActionListener(e -> startTransmission());

        directKeyboardButton = new JButton("⌨️ Прямая клавиатура");
        directKeyboardButton.setToolTipText("Включить/выключить прямую трансляцию клавиш");
        directKeyboardButton.setBackground(new Color(255, 200, 100));
        directKeyboardButton.addActionListener(e -> toggleDirectKeyboard());

        directKeyboardStatusLabel = new JLabel("Выкл");
        directKeyboardStatusLabel.setForeground(Color.GRAY);
        directKeyboardStatusLabel.setFont(directKeyboardStatusLabel.getFont().deriveFont(Font.BOLD, 11f));
        directKeyboardStatusLabel.setPreferredSize(new Dimension(35, 20));

        // Кнопка переключения языка
        switchLanguageButton = new JButton("Переключить язык");
        switchLanguageButton.setToolTipText("Переключить раскладку клавиатуры (Scroll Lock)");
        switchLanguageButton.setBackground(new Color(200, 200, 255));
        switchLanguageButton.addActionListener(e -> {
            if (model.isConnected()) {
                model.toggleLanguageFromGUI();
                showLanguageSwitchNotification();
            } else {
                JOptionPane.showMessageDialog(this, "Сначала подключитесь!");
            }
        });

        languageSwitchStatusLabel = new JLabel("");
        languageSwitchStatusLabel.setFont(languageSwitchStatusLabel.getFont().deriveFont(Font.BOLD, 11f));
        languageSwitchStatusLabel.setPreferredSize(new Dimension(100, 20));

        // Режимы
        wifiModeRadio = new JRadioButton("WiFi", true);
        serialModeRadio = new JRadioButton("Serial (COM-порт)");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(wifiModeRadio);
        modeGroup.add(serialModeRadio);

        wifiModeRadio.addActionListener(e -> {
            model.switchToWifiMode();
            wifiPanel.setVisible(true);
            serialPanel.setVisible(false);
            statusLabel.setText("Режим WiFi");
        });

        serialModeRadio.addActionListener(e -> {
            model.switchToSerialMode();
            wifiPanel.setVisible(false);
            serialPanel.setVisible(true);
            refreshSerialPorts();
            statusLabel.setText("Режим Serial");
        });

        serialPortCombo = new JComboBox<>();
        serialBaudCombo = new JComboBox<>(new Integer[]{9600, 19200, 38400, 57600, 115200});
        serialBaudCombo.setSelectedItem(115200);
        refreshSerialButton = new JButton("Обновить");
        refreshSerialButton.addActionListener(e -> refreshSerialPorts());

        updateButtonStates(false, false);
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));

        JPanel connectionPanel = new JPanel(new BorderLayout());

        // Панель выбора режима
        JPanel modeSelectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modeSelectionPanel.setBorder(BorderFactory.createTitledBorder("Режим подключения"));
        modeSelectionPanel.add(wifiModeRadio);
        modeSelectionPanel.add(serialModeRadio);

        // WiFi панель
        wifiPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        wifiPanel.add(new JLabel("Хост:"));
        wifiPanel.add(hostTextField);
        wifiPanel.add(new JLabel("Порт:"));
        wifiPanel.add(portTextField);

        // Serial панель
        serialPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        serialPanel.add(new JLabel("COM-порт:"));
        serialPanel.add(serialPortCombo);
        serialPanel.add(new JLabel("Скорость:"));
        serialPanel.add(serialBaudCombo);
        serialPanel.add(refreshSerialButton);
        serialPanel.setVisible(false);

        JPanel settingsPanel = new JPanel(new BorderLayout());
        settingsPanel.add(wifiPanel, BorderLayout.NORTH);
        settingsPanel.add(serialPanel, BorderLayout.SOUTH);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(modeSelectionPanel, BorderLayout.NORTH);
        topPanel.add(settingsPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(connectButton);
        bottomPanel.add(statusLabel);
        bottomPanel.add(testButton);
        bottomPanel.add(sendCustomButton);
        bottomPanel.add(ideCheckBox);

        connectionPanel.add(topPanel, BorderLayout.NORTH);
        connectionPanel.add(bottomPanel, BorderLayout.SOUTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.add(startButton);
        buttonPanel.add(pauseButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(lineNumberLabel);
        buttonPanel.add(timeRemainingLabel);

        // Добавляем контроль скорости
        setupSpeedControl();
        JPanel speedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        speedPanel.setBorder(BorderFactory.createTitledBorder("Скорость отправки"));
        speedPanel.add(new JLabel("⚡"));
        speedPanel.add(speedSlider);
        speedPanel.add(speedValueLabel);
        buttonPanel.add(speedPanel);


        JPanel virtualKeyboardPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        virtualKeyboardPanel.setBorder(BorderFactory.createTitledBorder("Виртуальная клавиатура"));
        virtualKeyboardPanel.add(virtualKeyboardCheckBox);
        virtualKeyboardPanel.add(virtualKeyboardStatusLabel);
        virtualKeyboardPanel.add(Box.createHorizontalStrut(10));
        virtualKeyboardPanel.add(new JLabel("Раскладка:"));
        virtualKeyboardPanel.add(layoutStatusLabel);
        virtualKeyboardPanel.add(toggleLayoutButton);
        virtualKeyboardPanel.add(Box.createHorizontalStrut(10));
        virtualKeyboardPanel.add(switchLanguageButton);
        virtualKeyboardPanel.add(languageSwitchStatusLabel);

        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.add(buttonPanel, BorderLayout.CENTER);
        controlPanel.add(virtualKeyboardPanel, BorderLayout.EAST);

        JPanel directKeyboardPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        directKeyboardPanel.setBorder(BorderFactory.createTitledBorder("Прямая клавиатура"));
        directKeyboardPanel.add(directKeyboardButton);
        directKeyboardPanel.add(directKeyboardStatusLabel);

        JPanel southWrapperPanel = new JPanel(new BorderLayout());
        southWrapperPanel.add(controlPanel, BorderLayout.CENTER);

        JPanel rightPanels = new JPanel(new GridLayout(2, 1, 5, 5));
        rightPanels.add(virtualKeyboardPanel);
        rightPanels.add(directKeyboardPanel);
        southWrapperPanel.add(rightPanels, BorderLayout.EAST);

        add(connectionPanel, BorderLayout.NORTH);
        add(new JScrollPane(messageTextPane), BorderLayout.CENTER);
        add(southWrapperPanel, BorderLayout.SOUTH);


        ((JComponent)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        setTitle("Socket/Serial Transmitter");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 650);
        setLocationRelativeTo(null);
    }

    private void refreshSerialPorts() {
        String[] ports = SerialTransmitter.getAvailablePorts();
        serialPortCombo.removeAllItems();
        for (String port : ports) {
            serialPortCombo.addItem(port);
        }
        if (ports.length == 0) {
            serialPortCombo.addItem("Нет доступных портов");
            serialPortCombo.setEnabled(false);
        } else {
            serialPortCombo.setEnabled(true);
        }
    }

    private void toggleConnection() {
        if (model.isConnected()) {
            model.disconnect();
        } else {
            if (model.isWifiMode()) {
                String host = hostTextField.getText().trim();
                String portStr = portTextField.getText().trim();
                if (host.isEmpty() || portStr.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Введите хост и порт!");
                    return;
                }
                try {
                    int port = Integer.parseInt(portStr);
                    model.connectWifi(host, port);
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this, "Неверный формат порта!");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Ошибка подключения: " + e.getMessage());
                }
            } else {
                String portName = (String) serialPortCombo.getSelectedItem();
                if (portName == null || portName.equals("Нет доступных портов")) {
                    JOptionPane.showMessageDialog(this, "Выберите COM-порт!");
                    return;
                }
                int baudRate = (Integer) serialBaudCombo.getSelectedItem();
                try {
                    model.connectSerial(portName, baudRate);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Ошибка подключения к COM-порту: " + e.getMessage());
                }
            }
        }
    }

    private void sendTestByte() {
        try {
            model.sendSingleByte(96);
            JOptionPane.showMessageDialog(this, "Тестовый байт (96) отправлен!");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка отправки: " + ex.getMessage());
        }
    }

    private void sendCustomData() {
        if (!model.isConnected()) {
            JOptionPane.showMessageDialog(this, "Сначала подключитесь!");
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
                            model.sendCustomText(input);
                        } else {
                            // Исправленный парсинг байтов
                            String[] byteStrings = input.split("\\s+");
                            byte[] bytes = new byte[byteStrings.length];
                            for (int i = 0; i < byteStrings.length; i++) {
                                try {
                                    int value = Integer.parseInt(byteStrings[i].trim());
                                    // Валидация диапазона
                                    if (value < 0 || value > 255) {
                                        throw new NumberFormatException("Значение должно быть в диапазоне 0-255: " + value);
                                    }
                                    bytes[i] = (byte) value;
                                } catch (NumberFormatException ex) {
                                   // SwingUtilities.invokeLater(() ->
                                   //         JOptionPane.showMessageDialog(this,
                                   //                 "Ошибка парсинга байта '" + byteStrings[i] + "': " + ex.getMessage(),
                                   //                 "Ошибка", JOptionPane.ERROR_MESSAGE));
                                    return;
                                }
                            }
                            model.sendCustomBytes(bytes);
                        }
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(this, "Данные успешно отправлены!"));
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(this, "Ошибка отправки: " + ex.getMessage()));
                    }
                }).start();
            }
        }
    }

    private void startTransmission() {
        String message = messageTextPane.getText();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Введите текст для отправки!");
            return;
        }

        if (model.isVirtualKeyboardActive()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Виртуальная клавиатура активна. Продолжить?",
                    "Виртуальная клавиатура активна",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) return;
        }

        model.startTransmission(message);
    }

    private void toggleVirtualKeyboard() {
        model.setVirtualKeyboardActive(virtualKeyboardCheckBox.isSelected());
    }

    private void toggleKeyboardLayout() {
        try {
            model.toggleKeyboardLayout();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка переключения раскладки: " + ex.getMessage());
        }
    }

    private void toggleDirectKeyboard() {
        boolean newState = !model.isDirectKeyboardActive();
        if (newState && !model.isVirtualKeyboardActive()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Для работы прямой клавиатуры необходимо включить виртуальную клавиатуру.\nВключить?",
                    "Включить виртуальную клавиатуру",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                virtualKeyboardCheckBox.setSelected(true);
                model.setVirtualKeyboardActive(true);
                model.setDirectKeyboardActive(true);
            }
        } else {
            model.setDirectKeyboardActive(newState);
        }
    }

    private void showLanguageSwitchNotification() {
        languageSwitchStatusLabel.setText("Переключено!");
        languageSwitchStatusLabel.setForeground(new Color(0, 150, 0));

        if (languageSwitchTimer != null && languageSwitchTimer.isRunning()) {
            languageSwitchTimer.stop();
        }

        languageSwitchTimer = new Timer(2000, e -> {
            languageSwitchStatusLabel.setText("");
        });
        languageSwitchTimer.setRepeats(false);
        languageSwitchTimer.start();

        Color originalColor = layoutStatusLabel.getForeground();
        layoutStatusLabel.setForeground(Color.ORANGE);
        Timer blinkTimer = new Timer(300, e -> {
            layoutStatusLabel.setForeground(originalColor);
        });
        blinkTimer.setRepeats(false);
        blinkTimer.start();
    }

    private void setupGlobalKeyListener() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {
                    // Обработка Scroll Lock для переключения языка
                    if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_SCROLL_LOCK) {
                        if (model.isConnected()) {
                            try {
                                model.switchLanguage();
                                showLanguageSwitchNotification();
                                return true;
                            } catch (Exception ex) {
                                System.err.println("Ошибка переключения языка: " + ex.getMessage());
                            }
                        }
                    }

                    if (model.isVirtualKeyboardActive() && model.isConnected() && MainFrame.this.isActive()) {
                        boolean isTextPaneFocused = SwingUtilities.isDescendingFrom(
                                KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(),
                                messageTextPane);

                        if (isTextPaneFocused) {
                            int keyCode = e.getKeyCode();
                            boolean isEditingKey = keyCode == KeyEvent.VK_BACK_SPACE ||
                                    keyCode == KeyEvent.VK_DELETE ||
                                    keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT ||
                                    keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN ||
                                    keyCode == KeyEvent.VK_HOME || keyCode == KeyEvent.VK_END ||
                                    keyCode == KeyEvent.VK_PAGE_UP || keyCode == KeyEvent.VK_PAGE_DOWN ||
                                    keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_TAB ||
                                    e.isControlDown() || e.isAltDown();

                            if (isEditingKey) return false;
                        }

                        if (e.getID() == KeyEvent.KEY_RELEASED ||
                                (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_SPACE)) {
                            try {
                                model.handleVirtualKeyEvent(e.getKeyCode(), e.getKeyChar(),
                                        e.isShiftDown(), e.getID() == KeyEvent.KEY_PRESSED,
                                        e.isActionKey());
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                            return true;
                        }
                    }

                    if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        if (model.isVirtualKeyboardActive()) {
                            SwingUtilities.invokeLater(() -> {
                                virtualKeyboardCheckBox.setSelected(false);
                                model.setVirtualKeyboardActive(false);
                            });
                            return true;
                        }
                    }

                    if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_SPACE &&
                            MainFrame.this.isActive() && model.isConnected() &&
                            model.getTransmissionThread() != null && model.getTransmissionThread().isAlive() &&
                            !model.isVirtualKeyboardActive()) {
                        SwingUtilities.invokeLater(() -> model.togglePause());
                        return true;
                    }
                    return false;
                });
    }

    private void updateButtonStates(boolean connected, boolean transmissionRunning) {
        startButton.setEnabled(connected && !transmissionRunning);
        pauseButton.setEnabled(connected && transmissionRunning);
        stopButton.setEnabled(connected && transmissionRunning);
        sendCustomButton.setEnabled(connected && !transmissionRunning);
        testButton.setEnabled(connected);
        switchLanguageButton.setEnabled(connected);

        if (!transmissionRunning) {
            pauseButton.setText("Пауза");
        } else if (model.isPaused()) {
            pauseButton.setText("Продолжить");
        } else {
            pauseButton.setText("Пауза");
        }
    }

    private void highlightSentCharacter(int position) {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = messageTextPane.getStyledDocument();
                if (position >= 0 && position < doc.getLength()) {
                    Style style = messageTextPane.addStyle("SentStyle", null);
                    StyleConstants.setForeground(style, new Color(0, 150, 0));
                    StyleConstants.setBold(style, true);
                    doc.setCharacterAttributes(position, 1, style, false);
                    messageTextPane.setCaretPosition(Math.min(position + 1, doc.getLength()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void resetAllTextColor() {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = messageTextPane.getStyledDocument();
                Style style = messageTextPane.addStyle("DefaultStyle", null);
                StyleConstants.setForeground(style, Color.BLACK);
                StyleConstants.setBold(style, false);
                doc.setCharacterAttributes(0, doc.getLength(), style, false);
                messageTextPane.setCaretPosition(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void setupTextPaneKeyListener() {
        messageTextPane.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (model.isVirtualKeyboardActive()) {
                    int keyCode = e.getKeyCode();
                    boolean allowedKey = keyCode == java.awt.event.KeyEvent.VK_BACK_SPACE ||
                            keyCode == java.awt.event.KeyEvent.VK_DELETE ||
                            keyCode == java.awt.event.KeyEvent.VK_LEFT || keyCode == java.awt.event.KeyEvent.VK_RIGHT ||
                            keyCode == java.awt.event.KeyEvent.VK_UP || keyCode == java.awt.event.KeyEvent.VK_DOWN ||
                            keyCode == java.awt.event.KeyEvent.VK_HOME || keyCode == java.awt.event.KeyEvent.VK_END ||
                            keyCode == java.awt.event.KeyEvent.VK_PAGE_UP || keyCode == java.awt.event.KeyEvent.VK_PAGE_DOWN ||
                            keyCode == java.awt.event.KeyEvent.VK_ENTER || keyCode == java.awt.event.KeyEvent.VK_TAB ||
                            e.isControlDown() || e.isAltDown();

                    if (!allowedKey) {
                        e.consume();
                    }
                }
            }

            @Override
            public void keyTyped(java.awt.event.KeyEvent e) {
                if (model.isVirtualKeyboardActive()) {
                    e.consume();
                }
            }
        });
    }

    private void setupSpeedControl() {
        speedSlider = new JSlider(JSlider.HORIZONTAL, TransmissionModel.MIN_DELAY, TransmissionModel.MAX_DELAY, TransmissionModel.BASE_DELAY);
        speedSlider.setMajorTickSpacing(50);
        speedSlider.setMinorTickSpacing(25);
        speedSlider.setPaintTicks(true);
        speedSlider.setPaintLabels(true);
        speedSlider.setSnapToTicks(true);

        // Создаем словарь для меток
        java.util.Hashtable<Integer, JLabel> labelTable = new java.util.Hashtable<>();
        labelTable.put(50, new JLabel("Быстро"));
        labelTable.put(100, new JLabel("Норм"));
        labelTable.put(200, new JLabel("Медл"));
        labelTable.put(300, new JLabel("Очень"));
        labelTable.put(500, new JLabel("Макс"));
        speedSlider.setLabelTable(labelTable);

        speedSlider.addChangeListener(e -> {
            int delay = speedSlider.getValue();
            model.setBaseDelay(delay);
            speedValueLabel.setText(delay + " мс");
        });

        speedValueLabel = new JLabel(TransmissionModel.BASE_DELAY + " мс");
        speedValueLabel.setFont(speedValueLabel.getFont().deriveFont(Font.BOLD, 11f));
    }
}