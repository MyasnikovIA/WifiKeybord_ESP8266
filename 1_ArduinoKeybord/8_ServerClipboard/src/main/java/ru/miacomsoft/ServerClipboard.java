package ru.miacomsoft;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

public class ServerClipboard {
    private static List<MessageBlock> messageBlocks = new ArrayList<>();
    private static JPanel messagesPanel;
    private static JFrame frame;
    private static volatile boolean serverRunning = true;
    private static ExecutorService threadPool;
    private static int connectedClients = 0;
    private static JLabel clientsLabel;

    // Настройки озвучивания
    private static volatile boolean soundEnabled = true;
    private static int soundSpeed = 90;
    private static String soundVoice = "irina";
    private static AtomicBoolean isSpeaking = new AtomicBoolean(false);

    // Паттерн для распознавания команды SOUND
    private static final Pattern SOUND_PATTERN = Pattern.compile("^SOUND\\(([^):]+)(?::(\\d+))?\\):(.*)$", Pattern.DOTALL);

    // Новые переменные для кнопок
    private static JPanel verticalButtonPanel;
    private static List<CustomButtonData> customButtons = new ArrayList<>();
    private static final String BUTTONS_SAVE_FILE = "custom_buttons.json";
    private static boolean editMode = false;
    private static JCheckBox editModeCheckbox;

    // Класс для хранения данных кнопки
    private static class CustomButtonData {
        String buttonText;
        String tooltipText;
        String copyText;

        CustomButtonData(String buttonText, String tooltipText, String copyText) {
            this.buttonText = buttonText;
            this.tooltipText = tooltipText;
            this.copyText = copyText;
        }

        JSONObject toJSON() {
            JSONObject json = new JSONObject();
            json.put("buttonText", buttonText);
            json.put("tooltipText", tooltipText);
            json.put("copyText", copyText);
            return json;
        }

        static CustomButtonData fromJSON(JSONObject json) {
            return new CustomButtonData(
                    json.getString("buttonText"),
                    json.getString("tooltipText"),
                    json.getString("copyText")
            );
        }
    }

    public static void createAndShowGUI() {
        frame = new JFrame("Web Server - Message Receiver");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopServer();
                saveCustomButtonsToFile();
            }
        });
        frame.setSize(900, 600);

        // Верхняя панель с информацией
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        clientsLabel = new JLabel("Подключенные клиенты: 0");
        infoPanel.add(clientsLabel);

        JLabel portLabel = new JLabel("Порт: 8080");
        infoPanel.add(portLabel);

        JLabel soundLabel = new JLabel("Озвучивание: " + (soundEnabled ? "ВКЛ" : "ВЫКЛ"));
        infoPanel.add(soundLabel);

        // Панель для сообщений с вертикальным скроллингом
        messagesPanel = new JPanel();
        messagesPanel.setLayout(new BoxLayout(messagesPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(messagesPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // Вертикальная панель для кнопок слева
        verticalButtonPanel = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                // Устанавливаем минимальную ширину 50px, но панель может расширяться
                size.width = Math.max(50, size.width);
                return size;
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(50, 0);
            }
        };
        verticalButtonPanel.setLayout(new BoxLayout(verticalButtonPanel, BoxLayout.Y_AXIS));
        verticalButtonPanel.setBorder(BorderFactory.createTitledBorder(" "));
        verticalButtonPanel.setPreferredSize(new Dimension(50, 0));

        // Кнопка ADD для добавления новых кнопок
        JButton addButton = new JButton("+");
        addButton.setToolTipText("Добавить новую кнопку");
        addButton.addActionListener(e -> showAddButtonDialog());
        verticalButtonPanel.add(addButton);
        verticalButtonPanel.add(Box.createVerticalStrut(5));

        // Загружаем сохраненные кнопки из файла
        loadCustomButtonsFromFile();

        // Отображаем загруженные кнопки
        refreshVerticalButtons();

        // Создаем разделитель для изменения ширины панели кнопок
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(verticalButtonPanel);
        splitPane.setRightComponent(scrollPane);
        splitPane.setDividerLocation(150); // Начальная ширина панели кнопок
        splitPane.setDividerSize(8); // Размер разделителя
        splitPane.setOneTouchExpandable(true); // Добавляем кнопки для быстрого раскрытия

        // Настраиваем поведение разделителя
        splitPane.setContinuousLayout(true);
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                // При изменении положения разделителя обновляем размеры кнопок
                SwingUtilities.invokeLater(() -> {
                    for (Component comp : verticalButtonPanel.getComponents()) {
                        if (comp instanceof JButton) {
                            JButton button = (JButton) comp;
                            int panelWidth = verticalButtonPanel.getWidth();
                            int buttonWidth = Math.max(panelWidth - 20, 30); // Минимальная ширина 30px
                            button.setMaximumSize(new Dimension(buttonWidth, 30));
                            button.setPreferredSize(new Dimension(buttonWidth, 30));
                            button.setMinimumSize(new Dimension(30, 30));
                        }
                    }
                    verticalButtonPanel.revalidate();
                    verticalButtonPanel.repaint();
                });
            }
        });

        // Кнопки управления в нижней панели
        JButton copyTemplateButton = new JButton("1");
        copyTemplateButton.setToolTipText("Скопировать шаблон инструкций");
        copyTemplateButton.addActionListener(e -> ServerClipboard.copyTemplateText());

        JButton copyTemplateButton2 = new JButton("2");
        copyTemplateButton2.setToolTipText("Голосовое описание");
        copyTemplateButton2.addActionListener(e -> ServerClipboard.copyTemplateSendText());

        JButton copyTemplateButton3 = new JButton("3");
        copyTemplateButton3.setToolTipText("Скопировать шаблон Error");
        copyTemplateButton3.addActionListener(e -> ServerClipboard.copyTemplateErrorText());

        JButton clearAllButton = new JButton("Очистить всю историю");
        clearAllButton.addActionListener(e -> clearAllMessages());

        // Кнопка для склеивания всего списка
        JButton mergeAllButton = new JButton("Склеить весь список");
        mergeAllButton.setToolTipText("Скопировать все сообщения в буфер обмена (каждое с новой строки)");
        mergeAllButton.addActionListener(e -> mergeAllMessagesToClipboard());

        // Кнопка управления озвучиванием
        JButton toggleSoundButton = new JButton("Звук ВКЛ/ВЫКЛ");
        toggleSoundButton.addActionListener(e -> {
            soundEnabled = !soundEnabled;
            soundLabel.setText("Озвучивание: " + (soundEnabled ? "ВКЛ" : "ВЫКЛ"));
            if (!soundEnabled) {
                stopCurrentSpeech();
            }
            JOptionPane.showMessageDialog(frame,
                    "Озвучивание " + (soundEnabled ? "включено" : "выключено") +
                            "\nСкорость: " + soundSpeed +
                            "\nГолос: " + soundVoice,
                    "Настройки звука",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        // Кнопка настроек звука
        JButton soundSettingsButton = new JButton("Настройки звука");
        soundSettingsButton.addActionListener(e -> showSoundSettingsDialog());

        // Галочка для режима редактирования
        editModeCheckbox = new JCheckBox("Редактирование кнопок");
        editModeCheckbox.addActionListener(e -> {
            editMode = editModeCheckbox.isSelected();
        });

        // Панель для кнопок внизу
        JPanel bottomButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomButtonPanel.add(copyTemplateButton);
        bottomButtonPanel.add(copyTemplateButton2);
        bottomButtonPanel.add(copyTemplateButton3);
        bottomButtonPanel.add(mergeAllButton);
        bottomButtonPanel.add(clearAllButton);
        bottomButtonPanel.add(toggleSoundButton);
        bottomButtonPanel.add(soundSettingsButton);
        bottomButtonPanel.add(editModeCheckbox);

        // Основная панель с BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.add(infoPanel, BorderLayout.NORTH);
        mainPanel.add(splitPane, BorderLayout.CENTER);
        mainPanel.add(bottomButtonPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);

        // Добавляем слушатель для изменения размера фрейма
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // При изменении размера фрейма обновляем ширину кнопок
                updateButtonWidths();
            }
        });

        frame.setVisible(true);
    }
    // Метод для обновления ширины кнопок при изменении размера панели
    private static void updateButtonWidths() {
        if (verticalButtonPanel != null) {
            int panelWidth = verticalButtonPanel.getWidth();
            int buttonWidth = Math.max(panelWidth - 20, 30); // Минимальная ширина 30px

            for (Component comp : verticalButtonPanel.getComponents()) {
                if (comp instanceof JButton) {
                    JButton button = (JButton) comp;
                    button.setMaximumSize(new Dimension(buttonWidth, 30));
                    button.setPreferredSize(new Dimension(buttonWidth, 30));
                }
            }
            verticalButtonPanel.revalidate();
            verticalButtonPanel.repaint();
        }
    }
    // Метод для загрузки кнопок из файла
    private static void loadCustomButtonsFromFile() {
        File file = new File(BUTTONS_SAVE_FILE);
        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }

            JSONArray jsonArray = new JSONArray(jsonString.toString());
            customButtons.clear();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                customButtons.add(CustomButtonData.fromJSON(json));
            }

            System.out.println("Загружено " + customButtons.size() + " кнопок из файла");
        } catch (Exception e) {
            System.err.println("Ошибка загрузки кнопок из файла: " + e.getMessage());
        }
    }

    // Метод для сохранения кнопок в файл
    private static void saveCustomButtonsToFile() {
        try (FileWriter file = new FileWriter(BUTTONS_SAVE_FILE)) {
            JSONArray jsonArray = new JSONArray();

            for (CustomButtonData button : customButtons) {
                jsonArray.put(button.toJSON());
            }

            file.write(jsonArray.toString(2));
            file.flush();
            System.out.println("Сохранено " + customButtons.size() + " кнопок в файл");
        } catch (Exception e) {
            System.err.println("Ошибка сохранения кнопок в файл: " + e.getMessage());
        }
    }

    // Метод для обновления отображения вертикальных кнопок
    // Метод для обновления отображения вертикальных кнопок
    private static void refreshVerticalButtons() {
        // Удаляем все кнопки кроме ADD
        while (verticalButtonPanel.getComponentCount() > 1) {
            verticalButtonPanel.remove(1);
        }

        // Текущая ширина панели
        int panelWidth = verticalButtonPanel.getWidth();
        int buttonWidth = Math.max(panelWidth - 20, 30); // Минимальная ширина 30px

        // Добавляем сохраненные кнопки
        for (CustomButtonData buttonData : customButtons) {
            JButton button = new JButton(buttonData.buttonText);
            button.setToolTipText(buttonData.tooltipText);
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (editMode) {
                        showEditButtonDialog(buttonData, customButtons.indexOf(buttonData));
                    } else {
                        copyCustomText(buttonData.copyText);
                        addMessage(buttonData.copyText);
                    }
                }
            });

            // Устанавливаем размеры для динамического масштабирования
            button.setMaximumSize(new Dimension(buttonWidth, 30));
            button.setPreferredSize(new Dimension(buttonWidth, 30));
            button.setMinimumSize(new Dimension(30, 30));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);

            verticalButtonPanel.add(button);
            verticalButtonPanel.add(Box.createVerticalStrut(3));
        }

        verticalButtonPanel.revalidate();
        verticalButtonPanel.repaint();
    }

    // Диалог добавления новой кнопки
    private static void showAddButtonDialog() {
        JDialog dialog = new JDialog(frame, "Добавить новую кнопку", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(500, 400);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        // Название кнопки
        panel.add(new JLabel("Название кнопки:"), gbc);
        gbc.gridy++;
        JTextField buttonNameField = new JTextField(20);
        panel.add(buttonNameField, gbc);

        // Подсказка
        gbc.gridy++;
        panel.add(new JLabel("Подсказка (tooltip):"), gbc);
        gbc.gridy++;
        JTextField tooltipField = new JTextField(20);
        panel.add(tooltipField, gbc);

        // Текст для копирования
        gbc.gridy++;
        panel.add(new JLabel("Текст для копирования:"), gbc);
        gbc.gridy++;
        JTextArea textArea = new JTextArea(10, 30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, gbc);

        // Кнопки сохранения/отмены
        JPanel buttonPanel = new JPanel();
        JButton saveButton = new JButton("Сохранить");
        JButton cancelButton = new JButton("Отмена");

        saveButton.addActionListener(e -> {
            String buttonName = buttonNameField.getText().trim();
            String tooltip = tooltipField.getText().trim();
            String text = textArea.getText().trim();

            if (buttonName.isEmpty() || text.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        "Название кнопки и текст не могут быть пустыми!",
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            CustomButtonData newButton = new CustomButtonData(buttonName, tooltip, text);
            customButtons.add(newButton);
            saveCustomButtonsToFile();
            refreshVerticalButtons();
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    // Диалог редактирования кнопки
    private static void showEditButtonDialog(CustomButtonData buttonData, int index) {
        JDialog dialog = new JDialog(frame, "Редактировать кнопку", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(500, 400);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;

        // Название кнопки
        panel.add(new JLabel("Название кнопки:"), gbc);
        gbc.gridy++;
        JTextField buttonNameField = new JTextField(buttonData.buttonText, 20);
        panel.add(buttonNameField, gbc);

        // Подсказка
        gbc.gridy++;
        panel.add(new JLabel("Подсказка (tooltip):"), gbc);
        gbc.gridy++;
        JTextField tooltipField = new JTextField(buttonData.tooltipText, 20);
        panel.add(tooltipField, gbc);

        // Текст для копирования
        gbc.gridy++;
        panel.add(new JLabel("Текст для копирования:"), gbc);
        gbc.gridy++;
        JTextArea textArea = new JTextArea(buttonData.copyText, 10, 30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, gbc);

        // Кнопки сохранения/удаления/отмены
        JPanel buttonPanel = new JPanel();
        JButton saveButton = new JButton("Сохранить");
        JButton deleteButton = new JButton("Удалить");
        JButton cancelButton = new JButton("Отмена");

        saveButton.addActionListener(e -> {
            String buttonName = buttonNameField.getText().trim();
            String tooltip = tooltipField.getText().trim();
            String text = textArea.getText().trim();

            if (buttonName.isEmpty() || text.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        "Название кнопки и текст не могут быть пустыми!",
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            buttonData.buttonText = buttonName;
            buttonData.tooltipText = tooltip;
            buttonData.copyText = text;
            saveCustomButtonsToFile();
            refreshVerticalButtons();
            dialog.dispose();
        });

        deleteButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(dialog,
                    "Вы уверены, что хотите удалить эту кнопку?",
                    "Подтверждение удаления",
                    JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                customButtons.remove(index);
                saveCustomButtonsToFile();
                refreshVerticalButtons();
                dialog.dispose();
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(saveButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(cancelButton);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    // Метод для копирования кастомного текста
    private static void copyCustomText(String text) {
        copyTextToClipboard(text);
        System.out.println("Кастомный текст скопирован в буфер обмена");
    }

    // Метод для склеивания всех сообщений в буфер обмена
    private static void mergeAllMessagesToClipboard() {
        if (messageBlocks.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                    "История сообщений пуста",
                    "Склеивание списка",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        StringBuilder mergedText = new StringBuilder();

        for (int i = 0; i < messageBlocks.size(); i++) {
            MessageBlock block = messageBlocks.get(i);
            mergedText.append(block.text);

            // Добавляем новую строку после каждого блока, кроме последнего
            if (i < messageBlocks.size() - 1) {
                mergedText.append("\n");
            }
        }

        copyTextToClipboard(mergedText.toString());
    }

    // Диалог настроек звука
    private static void showSoundSettingsDialog() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));

        panel.add(new JLabel("Скорость речи:"));
        JSpinner speedSpinner = new JSpinner(new SpinnerNumberModel(soundSpeed, 50, 200, 5));
        panel.add(speedSpinner);

        panel.add(new JLabel("Голос:"));
        JComboBox<String> voiceCombo = new JComboBox<>(new String[]{"irina", "Anna", "marina", "pavel"});
        voiceCombo.setSelectedItem(soundVoice);
        panel.add(voiceCombo);

        panel.add(new JLabel("Автозапуск:"));
        JCheckBox autoCheckBox = new JCheckBox("Включено", soundEnabled);
        panel.add(autoCheckBox);

        int result = JOptionPane.showConfirmDialog(frame, panel, "Настройки озвучивания",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            soundSpeed = (Integer) speedSpinner.getValue();
            soundVoice = (String) voiceCombo.getSelectedItem();
            soundEnabled = autoCheckBox.isSelected();

            JOptionPane.showMessageDialog(frame,
                    "Настройки сохранены:\n" +
                            "Скорость: " + soundSpeed + "\n" +
                            "Голос: " + soundVoice + "\n" +
                            "Автозапуск: " + (soundEnabled ? "ВКЛ" : "ВЫКЛ"),
                    "Настройки обновлены",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // Метод для озвучивания текста
    public static void speakText(String text) {
        if (!soundEnabled || text == null || text.trim().isEmpty()) {
            return;
        }

        new Thread(() -> {
            if (isSpeaking.get()) {
                System.out.println("Озвучивание уже выполняется, пропускаем...");
                return;
            }
            isSpeaking.set(true);
            try {
                String command = "Govorilka_cp.exe";
                String voiceParam = "-E";
                String voice = soundVoice;
                String speedParam = "-s" + soundSpeed;

                // Очищаем текст от лишних символов
                String cleanText = text.replaceAll("[\\r\\n]+", " ").trim();

                ProcessBuilder processBuilder = new ProcessBuilder(command, voiceParam, voice, speedParam, cleanText);
                processBuilder.redirectErrorStream(true);

                System.out.println("Запуск озвучивания: " + command + " " + voiceParam + " " + voice + " " + speedParam + " \"" + cleanText + "\"");

                Process process = processBuilder.start();

                // Читаем вывод программы
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Govorilka: " + line);
                    }
                }

                int exitCode = process.waitFor();
                System.out.println("Озвучивание завершено с кодом: " + exitCode);

            } catch (IOException e) {
                System.err.println("Ошибка запуска Govorilka_cp.exe: " + e.getMessage());
                JOptionPane.showMessageDialog(frame,
                        "Не удалось запустить Govorilka_cp.exe\n" +
                                "Убедитесь, что программа находится в PATH или в текущей директории.\n" +
                                "Ошибка: " + e.getMessage(),
                        "Ошибка озвучивания",
                        JOptionPane.ERROR_MESSAGE);
            } catch (InterruptedException e) {
                System.err.println("Озвучивание прервано: " + e.getMessage());
                Thread.currentThread().interrupt();
            } finally {
                isSpeaking.set(false);
            }
        }).start();
    }

    // Метод для остановки текущего озвучивания
    private static void stopCurrentSpeech() {
        if (isSpeaking.get()) {
            try {
                // Для Windows - завершаем процесс Govorilka_cp.exe
                Runtime.getRuntime().exec("taskkill /F /IM Govorilka_cp.exe");
                System.out.println("Озвучивание остановлено");
            } catch (IOException e) {
                System.err.println("Ошибка при остановке озвучивания: " + e.getMessage());
            }
        }
    }

    // Метод для обработки команды SOUND - только озвучивание без добавления в историю
    private static void processSoundCommand(String text, InetAddress clientAddress) {
        // Удаляем префикс "SOUND:" (включая возможные пробелы после двоеточия)
        String content = text.replaceFirst("^SOUND:\\s*", "").trim();

        // Проверяем, что после удаления префикса остался текст
        if (content.isEmpty()) {
            System.out.println("Пустая команда SOUND от " + clientAddress);
            return;
        }

        System.out.println("Получена команда SOUND от " + clientAddress);
        System.out.println("Длина текста: " + content.length() + " символов");

        if (content.length() > 50) {
            System.out.println("Предпросмотр: " + content.substring(0, 50) + "...");
        }

        // Озвучиваем текст (без добавления в историю)
        speakText(content);

        // Не добавляем в историю сообщений, только логируем
        System.out.println("Текст озвучен (не добавлен в историю): " + content.substring(0, Math.min(100, content.length())) + "...");
    }

    // Новая функция для копирования произвольного текста в буфер обмена
    private static void copyTextToClipboard(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        StringSelection stringSelection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
        System.out.println("Текст скопирован в буфер обмена");
    }

    // Функция для копирования шаблонного текста
    private static void copyTemplateSendText() {
        String templateText ="Необходимо написать учебную программу на языке JAVA по текстовому описанию. Программа не должна содержать комментарии на русском язуке.\n" +
                "Все должно бьыть максимально локонично, и с применением минимального количества библиотек.\n" +
                "Алгоритм должен быть написан максимально продуктивным способом, чтобы количество операций (сложновть) было как можно меньше.\n" +
                "Сначала необходимо написать программу, а потом  очень короткое описание что она выполняет.\n" +
                "Далее будет писать голосовое описание:\n\n";
        addMessage(templateText);
        ServerClipboard.copyTextToClipboard(templateText);
    }


    // Функция для копирования шаблонного текста
    private static void copyTemplateErrorText() {
        String templateText ="В написанном ответе из предыдущего комментария есть ошибка которая выявилась при тесте.\n" +
                "Еще раз  изучи задание и в этот раз напиши код без ошибок, чтобы все тестов прошли успешно.\n" +
                "Сначала покажи исправленный код, а потом короткое рассуждение где была допущена ошибка.";
        addMessage(templateText);
        ServerClipboard.copyTextToClipboard(templateText);
    }
    // Функция для копирования шаблонного текста
    private static void copyTemplateText() {
        String templateText =
                "Далее буду отправлять задания которые выдают на собеседовании. \n" +
                        "Необходимо будет написать решение задачи на языке Java без каких либо комментариев, на основании шаблона кода. \n" +
                        "Все классы должны содержать гетеры и сеттера, и модификаторы\n" +
                        "При написании кода, расставь нормально пробелы.\n" +
                        "Если  решение задачи требует только удаление  комментария /* .... */ и замены нового кода, тогда нужно только показать фрагмент кода, который будет вставлен в замен блока комментария(+ список новых импортов в начало файла, если оно нужно), иначе показывать полностью решение (весь листинг решаемой задачи)\n" +
                        "Имена переменных нужно писать осмысленные в кэмэлкейсе с маленькой буквы.\n" +
                        "При написании нужно учитывать следующие  пункты:\n" +
                        "1) Сначала очень коротко сформулируйте решение вслух до набора кода\n" +
                        "2) Пишите читаемый код, не переусложните его\n" +
                        "3) Учитывайте основные краевые случаи, используйте тест‑кейсы\n" +
                        "4) При необходимости использовать String.split()\n" +
                        "Далее буду  присылать задания. Жди";

        ServerClipboard.copyTextToClipboard(templateText);
    }

    public static void startWebServer(int port) {
        threadPool = Executors.newFixedThreadPool(100);
        System.out.println("WebServer запущен на порту " + port);
        System.out.println("Доступные методы:");
        System.out.println("1. GET /?text=текст");
        System.out.println("2. POST / с текстом в теле");
        System.out.println("Поддерживаемые команды:");
        System.out.println("- SEND(IP:PORT):текст - отправить текст (добавляется в историю)");
        System.out.println("- SOUND(IP:PORT):текст - озвучить текст (НЕ добавляется в историю)");

        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                serverSocket.setReuseAddress(true);
                serverSocket.setSoTimeout(1000);

                while (serverRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        clientSocket.setSoTimeout(10000);
                        incrementConnectedClients();

                        threadPool.execute(new ClientHandler(clientSocket));

                    } catch (SocketTimeoutException e) {
                        continue;
                    } catch (IOException e) {
                        if (serverRunning) {
                            System.err.println("Ошибка при принятии соединения: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Не удалось запустить сервер: " + e.getMessage());
            } finally {
                if (threadPool != null && !threadPool.isShutdown()) {
                    threadPool.shutdown();
                }
                System.out.println("Сервер остановлен");
            }
        }).start();
    }

    private static synchronized void incrementConnectedClients() {
        connectedClients++;
        updateClientsLabel();
    }

    private static synchronized void decrementConnectedClients() {
        connectedClients--;
        if (connectedClients < 0) connectedClients = 0;
        updateClientsLabel();
    }

    private static void updateClientsLabel() {
        SwingUtilities.invokeLater(() -> {
            if (clientsLabel != null) {
                clientsLabel.setText("Подключенные клиенты: " + connectedClients);
            }
        });
    }

    private static void stopServer() {
        serverRunning = false;
        if (threadPool != null) {
            threadPool.shutdown();
        }
        System.out.println("Сервер остановлен");
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

                String requestLine = in.readLine();
                if (requestLine == null || requestLine.isEmpty()) {
                    sendErrorResponse(out, 400, "Bad Request");
                    return;
                }

                String[] requestParts = requestLine.split(" ");
                if (requestParts.length < 2) {
                    sendErrorResponse(out, 400, "Bad Request");
                    return;
                }

                String method = requestParts[0];
                String url = requestParts[1];

                int contentLength = 0;
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    }
                }

                String message = null;

                if ("GET".equalsIgnoreCase(method)) {
                    if (url.contains("?")) {
                        String query = url.substring(url.indexOf('?') + 1);
                        String[] params = query.split("&");
                        for (String param : params) {
                            if (param.startsWith("text=")) {
                                try {
                                    message = URLDecoder.decode(param.substring(5), "UTF-8");
                                } catch (UnsupportedEncodingException e) {
                                    message = param.substring(5);
                                }
                                break;
                            }
                        }
                    }
                } else if ("POST".equalsIgnoreCase(method)) {
                    if (contentLength > 0) {
                        StringBuilder body = new StringBuilder();
                        char[] buffer = new char[contentLength];
                        int read = in.read(buffer, 0, contentLength);
                        if (read > 0) {
                            body.append(buffer, 0, read);
                            message = body.toString().trim();
                        }
                    }
                } else if ("OPTIONS".equalsIgnoreCase(method)) {
                    sendCorsHeaders(out);
                    out.println();
                    out.flush();
                    return;
                }

                if (message != null && !message.isEmpty()) {
                    String finalMessage = message;
                    InetAddress clientAddress = clientSocket.getInetAddress();

                    // Проверяем, является ли сообщение командой SOUND
                    if (finalMessage.startsWith("SOUND:")) {
                        // Только озвучивание, без добавления в историю
                        processSoundCommand(finalMessage, clientAddress);
                    } else {
                        // Обычное сообщение (SEND или другой текст) - добавляем в историю
                        SwingUtilities.invokeLater(() -> addMessage(finalMessage));
                        System.out.println("Получено сообщение от " + clientAddress + ": " + message);
                    }
                }

                sendCorsHeaders(out);
                out.println("Content-Type: text/plain; charset=utf-8");
                out.println("Access-Control-Allow-Origin: *");
                out.println("Access-Control-Allow-Methods: GET, POST, OPTIONS");
                out.println("Access-Control-Allow-Headers: Content-Type, Accept");
                out.println();
                out.println("OK");
                out.flush();

            } catch (IOException e) {
                System.err.println("Ошибка обработки запроса от " +
                        clientSocket.getInetAddress() + ": " + e.getMessage());
            } finally {
                try {
                    if (!clientSocket.isClosed()) {
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    System.err.println("Ошибка закрытия сокета: " + e.getMessage());
                }
                decrementConnectedClients();
            }
        }

        private void sendCorsHeaders(PrintWriter out) {
            out.println("HTTP/1.1 200 OK");
            out.println("Access-Control-Allow-Origin: *");
            out.println("Access-Control-Allow-Methods: GET, POST, OPTIONS");
            out.println("Access-Control-Allow-Headers: Content-Type");
            out.println("Access-Control-Max-Age: 86400");
        }

        private void sendErrorResponse(PrintWriter out, int code, String message) {
            out.println("HTTP/1.1 " + code + " " + message);
            sendCorsHeaders(out);
            out.println("Content-Type: text/plain; charset=utf-8");
            out.println("Access-Control-Allow-Origin: *");
            out.println("Access-Control-Allow-Methods: GET, POST, OPTIONS");
            out.println("Access-Control-Allow-Headers: Content-Type, Accept");
            out.println();
            out.println("Error: " + message);
            out.flush();
        }
    }

    private static void addMessage(String text) {
        for (MessageBlock block : messageBlocks) {
            if (block.text.equals(text)) {
                System.out.println("Сообщение уже существует: " + text);
                return;
            }
        }

        MessageBlock block = new MessageBlock(text);
        messageBlocks.add(block);

        messagesPanel.add(block.getPanel());
        messagesPanel.revalidate();
        messagesPanel.repaint();

        JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, messagesPanel);
        if (scrollPane != null) {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        }
    }

    private static void clearAllMessages() {
        messageBlocks.clear();
        messagesPanel.removeAll();
        messagesPanel.revalidate();
        messagesPanel.repaint();
    }

    private static class MessageBlock {
        private JPanel panel;
        private JTextArea textArea;
        private JLabel copyIndicator;
        private String text;
        private JLabel timestampLabel;

        public MessageBlock(String text) {
            this.text = text;
            createUI();
        }

        private void createUI() {
            panel = new JPanel(new BorderLayout(5, 0));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 200, 200)),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ));

            JPanel leftPanel = new JPanel(new BorderLayout());
            leftPanel.setPreferredSize(new Dimension(120, 80));

            JButton deleteButton = new JButton("X");
            deleteButton.setMargin(new Insets(2, 5, 2, 5));
            deleteButton.setForeground(Color.RED);
            deleteButton.setPreferredSize(new Dimension(40, 25));
            deleteButton.addActionListener(e -> removeMessage());

            timestampLabel = new JLabel(new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));
            timestampLabel.setFont(new Font("Arial", Font.PLAIN, 10));
            timestampLabel.setForeground(Color.GRAY);
            timestampLabel.setHorizontalAlignment(SwingConstants.CENTER);

            leftPanel.add(deleteButton, BorderLayout.NORTH);
            leftPanel.add(timestampLabel, BorderLayout.SOUTH);

            textArea = new JTextArea(text);
            textArea.setRows(3);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setEditable(false);
            textArea.setMargin(new Insets(5, 5, 5, 5));

            textArea.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 1) {
                        copyToClipboard();
                        showCopyIndicator();
                    }
                }
            });

            JScrollPane textScroll = new JScrollPane(textArea);
            textScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            textScroll.setPreferredSize(new Dimension(400, 80));

            JPanel rightPanel = new JPanel(new BorderLayout());
            rightPanel.setPreferredSize(new Dimension(50, 80));

            copyIndicator = new JLabel("✓");
            copyIndicator.setFont(new Font("Arial", Font.BOLD, 16));
            copyIndicator.setForeground(new Color(0, 150, 0));
            copyIndicator.setVisible(false);
            copyIndicator.setHorizontalAlignment(SwingConstants.CENTER);

            rightPanel.add(copyIndicator, BorderLayout.NORTH);

            panel.add(leftPanel, BorderLayout.WEST);
            panel.add(textScroll, BorderLayout.CENTER);
            panel.add(rightPanel, BorderLayout.EAST);

            panel.setToolTipText("Кликните на текст для копирования в буфер обмена");
        }

        private void showCopyIndicator() {
            copyIndicator.setVisible(true);
            Timer timer = new Timer(2000, e -> copyIndicator.setVisible(false));
            timer.setRepeats(false);
            timer.start();
        }

        private void removeMessage() {
            messageBlocks.remove(this);
            messagesPanel.remove(panel);
            messagesPanel.revalidate();
            messagesPanel.repaint();
        }

        private void copyToClipboard() {
            StringSelection stringSelection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
            System.out.println("Текст скопирован в буфер обмена: " + text.substring(0, Math.min(50, text.length())) + "...");
        }

        public JPanel getPanel() {
            return panel;
        }
    }
}