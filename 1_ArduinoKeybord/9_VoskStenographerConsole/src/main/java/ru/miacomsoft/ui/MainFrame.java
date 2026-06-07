package ru.miacomsoft.ui;

import ru.miacomsoft.audio.AudioCapture;
import ru.miacomsoft.audio.AudioDeviceInfo;
import ru.miacomsoft.config.SettingsManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

public class MainFrame extends JFrame {
    private TranscriptPanel leftPanel;   // Системный звук (динамики)
    private TranscriptPanel rightPanel;  // Выбранное пользователем устройство
    private JComboBox<AudioDeviceInfo> deviceComboBox;
    private JButton startRightButton;
    private JButton stopRightButton;
    private JButton settingsButton;
    private JLabel selectedDeviceLabel;
    private SettingsManager settings;
    private AudioCapture audioCapture;
    private ConsolePanel consolePanel;
    private JSplitPane mainSplitPane;

    public MainFrame() {
        settings = SettingsManager.getInstance();
        audioCapture = new AudioCapture(16000);
        initUI();
        loadSettings();
        refreshDevices();

        // Выводим информацию в консоль
        consolePanel.printSuccess("Приложение запущено");
        consolePanel.printInfo("Версия: 1.0");
        consolePanel.printInfo("Путь к модели: " + settings.getModelPath());
    }

    private void initUI() {
        setTitle("Стенограф - Распознавание речи");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setLocationRelativeTo(null);

        // Создаем главную панель с вертикальным сплитом
        mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        mainSplitPane.setResizeWeight(0.7);
        mainSplitPane.setDividerSize(8);

        // Верхняя панель с двумя колонками
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        // Верхняя панель с настройками
        JPanel controlPanel = createTopPanel();
        topPanel.add(controlPanel, BorderLayout.NORTH);

        // Центральная панель с двумя колонками
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 15, 0));

        // Создаем консольную панель
        consolePanel = new ConsolePanel();

        leftPanel = new TranscriptPanel("СИСТЕМНЫЙ ЗВУК (динамики)",
                "Захват звука из динамиков", Color.decode("#2E7D32"), consolePanel);
        rightPanel = new TranscriptPanel("ВЫБРАННЫЙ ИСТОЧНИК",
                "Выберите устройство и нажмите СТАРТ", Color.decode("#1565C0"), consolePanel);

        centerPanel.add(leftPanel);
        centerPanel.add(rightPanel);
        topPanel.add(centerPanel, BorderLayout.CENTER);

        mainSplitPane.setTopComponent(topPanel);
        mainSplitPane.setBottomComponent(consolePanel);
        consolePanel.setPreferredSize(new Dimension(getWidth(), 200));

        add(mainSplitPane);

        // Обработка закрытия окна
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                shutdownAll();
            }
        });
    }

    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout(10, 5));
        topPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        // Левая часть - управление системным звуком
        JPanel leftControl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftControl.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(46, 125, 50)),
                "СИСТЕМНЫЙ ЗВУК",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 11),
                new Color(46, 125, 50)
        ));

        JButton startLeftButton = new JButton("🎤 СТАРТ (динамики)");
        startLeftButton.setBackground(new Color(46, 125, 50));
        startLeftButton.setForeground(Color.DARK_GRAY);
        startLeftButton.setFont(new Font("Arial", Font.BOLD, 12));
        startLeftButton.setFocusPainted(false);
        startLeftButton.setPreferredSize(new Dimension(200, 35));
        startLeftButton.addActionListener(e -> startLeftCapture());
        leftControl.add(startLeftButton);

        JButton stopLeftButton = new JButton("⏹️ СТОП");
        stopLeftButton.setBackground(new Color(197, 32, 32));
        stopLeftButton.setForeground(Color.DARK_GRAY);
        stopLeftButton.setFont(new Font("Arial", Font.BOLD, 12));
        stopLeftButton.setFocusPainted(false);
        stopLeftButton.setPreferredSize(new Dimension(120, 35));
        stopLeftButton.addActionListener(e -> stopLeftCapture());
        leftControl.add(stopLeftButton);

        // Правая часть - выбор устройства
        JPanel rightControl = new JPanel(new BorderLayout(10, 5));
        rightControl.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(21, 101, 192)),
                "ВЫБОР ИСТОЧНИКА",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 11),
                new Color(21, 101, 192)
        ));

        // Верхняя панель выбора устройства
        JPanel selectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        selectionPanel.add(new JLabel("Устройство ввода:"));

        deviceComboBox = new JComboBox<>();
        deviceComboBox.setPreferredSize(new Dimension(400, 30));
        deviceComboBox.addActionListener(e -> {
            AudioDeviceInfo selected = (AudioDeviceInfo) deviceComboBox.getSelectedItem();
            if (selected != null && selected.getId() > 0) {
                selectedDeviceLabel.setText("✅ Выбрано: " + selected.getName());
                consolePanel.printInfo("Выбрано устройство: " + selected.getName());
                startRightButton.setEnabled(true);
            } else if (selected != null && selected.getId() == 0) {
                startRightButton.setEnabled(false);
            }
        });
        selectionPanel.add(deviceComboBox);

        JButton refreshButton = new JButton("🔄 Обновить список");
        refreshButton.addActionListener(e -> {
            refreshDevices();
            consolePanel.printInfo("Список устройств обновлен");
        });
        selectionPanel.add(refreshButton);

        rightControl.add(selectionPanel, BorderLayout.NORTH);

        // Панель с информацией о выбранном устройстве
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        selectedDeviceLabel = new JLabel("⚪ Устройство не выбрано");
        selectedDeviceLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        selectedDeviceLabel.setForeground(Color.GRAY);
        infoPanel.add(selectedDeviceLabel);
        rightControl.add(infoPanel, BorderLayout.CENTER);

        // Панель с кнопками управления
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        startRightButton = new JButton("▶ СТАРТ распознавание");
        startRightButton.setBackground(new Color(21, 101, 192));
        startRightButton.setForeground(Color.DARK_GRAY);
        startRightButton.setFont(new Font("Arial", Font.BOLD, 12));
        startRightButton.setFocusPainted(false);
        startRightButton.setPreferredSize(new Dimension(200, 35));
        startRightButton.setEnabled(false);
        startRightButton.addActionListener(e -> startRightCapture());
        buttonPanel.add(startRightButton);

        stopRightButton = new JButton("⏹️ СТОП");
        stopRightButton.setBackground(new Color(197, 32, 32));
        stopRightButton.setForeground(Color.DARK_GRAY);
        stopRightButton.setFont(new Font("Arial", Font.BOLD, 12));
        stopRightButton.setFocusPainted(false);
        stopRightButton.setPreferredSize(new Dimension(120, 35));
        stopRightButton.addActionListener(e -> stopRightCapture());
        buttonPanel.add(stopRightButton);

        rightControl.add(buttonPanel, BorderLayout.SOUTH);

        // Кнопка настроек
        settingsButton = new JButton("⚙ НАСТРОЙКИ");
        settingsButton.setBackground(new Color(100, 100, 100));
        settingsButton.setForeground(Color.DARK_GRAY);
        settingsButton.setFont(new Font("Arial", Font.BOLD, 12));
        settingsButton.setFocusPainted(false);
        settingsButton.setPreferredSize(new Dimension(150, 35));
        settingsButton.addActionListener(e -> showSettingsDialog());

        topPanel.add(leftControl, BorderLayout.WEST);
        topPanel.add(rightControl, BorderLayout.CENTER);
        topPanel.add(settingsButton, BorderLayout.EAST);

        return topPanel;
    }

    private void refreshDevices() {
        deviceComboBox.removeAllItems();
        List<AudioDeviceInfo> devices = audioCapture.listMicrophones();

        if (devices.isEmpty()) {
            deviceComboBox.addItem(new AudioDeviceInfo(0, "❌ Нет доступных устройств", null, false));
            startRightButton.setEnabled(false);
            selectedDeviceLabel.setText("❌ Устройства не найдены");
            consolePanel.printWarning("Нет доступных аудиоустройств");
        } else {
            for (AudioDeviceInfo device : devices) {
                deviceComboBox.addItem(device);
                consolePanel.printInfo("Найдено устройство [" + device.getId() + "]: " + device.getName() +
                        (device.isBusy() ? " (ЗАНЯТО)" : " (СВОБОДНО)"));
            }
            consolePanel.printSuccess("Найдено " + devices.size() + " аудиоустройств");
            selectedDeviceLabel.setText("⚡ Выберите устройство из списка");
        }
    }

    private void startLeftCapture() {
        String modelPath = settings.getModelPath();
        if (!validateModelPath(modelPath)) return;

        if (leftPanel.isRecognitionRunning()) {
            consolePanel.printWarning("Распознавание системного звука уже запущено");
            return;
        }

        consolePanel.printInfo("▶ Запуск распознавания системного звука (динамики)");
        leftPanel.startRecognition(modelPath, true, null);
    }

    private void stopLeftCapture() {
        if (!leftPanel.isRecognitionRunning()) {
            consolePanel.printWarning("Распознавание системного звука не запущено");
            return;
        }
        consolePanel.printInfo("⏹️ Остановка распознавания системного звука");
        leftPanel.stopRecognition();
    }

    private void startRightCapture() {
        AudioDeviceInfo selected = (AudioDeviceInfo) deviceComboBox.getSelectedItem();
        if (selected == null || selected.getId() == 0) {
            JOptionPane.showMessageDialog(this,
                    "Пожалуйста, выберите устройство из списка",
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            consolePanel.printError("Не выбрано устройство для распознавания");
            return;
        }

        if (rightPanel.isRecognitionRunning()) {
            consolePanel.printWarning("Распознавание с выбранного устройства уже запущено");
            return;
        }

        String modelPath = settings.getModelPath();
        if (!validateModelPath(modelPath)) return;

        consolePanel.printInfo("▶ Запуск распознавания с устройства: " + selected.getName());
        consolePanel.printInfo("  ID устройства: " + selected.getId());
        rightPanel.startRecognition(modelPath, false, selected);
    }

    private void stopRightCapture() {
        if (!rightPanel.isRecognitionRunning()) {
            consolePanel.printWarning("Распознавание с выбранного устройства не запущено");
            return;
        }
        consolePanel.printInfo("⏹️ Остановка распознавания с выбранного устройства");
        rightPanel.stopRecognition();
    }

    private boolean validateModelPath(String modelPath) {
        if (modelPath == null || modelPath.isEmpty()) {
            String errorMsg = "Модель не выбрана! Укажите путь к модели в настройках.";
            JOptionPane.showMessageDialog(this, errorMsg, "Ошибка", JOptionPane.ERROR_MESSAGE);
            consolePanel.printError(errorMsg);
            return false;
        }

        File modelDir = new File(modelPath);
        if (!modelDir.exists() || !modelDir.isDirectory()) {
            String errorMsg = "Модель не найдена по пути: " + modelPath;
            JOptionPane.showMessageDialog(this, errorMsg, "Ошибка", JOptionPane.ERROR_MESSAGE);
            consolePanel.printError(errorMsg);
            return false;
        }

        consolePanel.printSuccess("✅ Модель найдена: " + modelPath);
        return true;
    }

    private void showSettingsDialog() {
        SettingsDialog dialog = new SettingsDialog(this, settings, consolePanel);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            settings.saveSettings();
            consolePanel.printSuccess("Настройки сохранены. Путь к модели: " + settings.getModelPath());
        }
    }

    private void loadSettings() {
        settings.loadSettings();
        consolePanel.printInfo("Загружены настройки из: " +
                System.getProperty("user.home") + File.separator + ".stenographer.conf");
    }

    private void shutdownAll() {
        consolePanel.printInfo("Завершение работы приложения...");
        leftPanel.stopRecognition();
        rightPanel.stopRecognition();
        settings.saveSettings();
        consolePanel.printSuccess("Приложение остановлено");
        consolePanel.printInfo("До свидания!");
    }
}