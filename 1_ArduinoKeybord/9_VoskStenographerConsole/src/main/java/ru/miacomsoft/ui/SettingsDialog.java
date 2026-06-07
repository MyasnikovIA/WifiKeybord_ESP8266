package ru.miacomsoft.ui;

import ru.miacomsoft.config.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class SettingsDialog extends JDialog {
    private JTextField modelPathField;
    private JButton browseButton;
    private JButton saveButton;
    private JButton cancelButton;
    private SettingsManager settings;
    private ConsolePanel consolePanel;
    private boolean saved = false;

    public SettingsDialog(JFrame parent, SettingsManager settings, ConsolePanel consolePanel) {
        super(parent, "Настройки", true);
        this.settings = settings;
        this.consolePanel = consolePanel;
        initUI();
        loadCurrentSettings();
        setLocationRelativeTo(parent);
    }

    private void initUI() {
        setSize(650, 250);
        setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Метка
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("Путь к модели Vosk:"), gbc);

        // Поле ввода
        gbc.gridx = 1;
        gbc.weightx = 1;
        modelPathField = new JTextField();
        mainPanel.add(modelPathField, gbc);

        // Кнопка обзора
        gbc.gridx = 2;
        gbc.weightx = 0;
        browseButton = new JButton("Обзор...");
        browseButton.addActionListener(e -> browseForModel());
        mainPanel.add(browseButton, gbc);

        // Информация
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1;
        JLabel infoLabel = new JLabel(
                "<html><small>Модель должна содержать файлы: am.conf, conf/, ivector/, graph/, rescorer/, rnnlm/ и др.</small></html>");
        infoLabel.setForeground(Color.GRAY);
        mainPanel.add(infoLabel, gbc);

        // Пример пути
        gbc.gridy = 2;
        JLabel exampleLabel = new JLabel(
                "<html><small>Пример: /home/user/vosk-model-ru-0.10</small></html>");
        exampleLabel.setForeground(new Color(100, 100, 255));
        mainPanel.add(exampleLabel, gbc);

        add(mainPanel, BorderLayout.CENTER);

        // Кнопки внизу
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        saveButton = new JButton("Сохранить");
        saveButton.addActionListener(e -> saveSettings());
        cancelButton = new JButton("Отмена");
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void browseForModel() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Выберите папку с моделью Vosk");

        String currentPath = modelPathField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            fileChooser.setCurrentDirectory(new File(currentPath));
        }

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String selectedPath = fileChooser.getSelectedFile().getAbsolutePath();
            modelPathField.setText(selectedPath);
            if (consolePanel != null) {
                consolePanel.printInfo("Выбран путь к модели: " + selectedPath);
            }
        }
    }

    private void loadCurrentSettings() {
        modelPathField.setText(settings.getModelPath());
    }

    private void saveSettings() {
        String modelPath = modelPathField.getText().trim();
        if (modelPath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Пожалуйста, укажите путь к модели",
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File modelDir = new File(modelPath);
        if (!modelDir.exists() || !modelDir.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                    "Указанный путь не существует или не является папкой",
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }

        settings.setModelPath(modelPath);
        saved = true;
        if (consolePanel != null) {
            consolePanel.printSuccess("Путь к модели сохранен: " + modelPath);
        }
        dispose();
    }

    public boolean isSaved() {
        return saved;
    }
}