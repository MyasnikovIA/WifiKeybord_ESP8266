package ru.miacomsoft;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ChromeLauncherApp extends JFrame {
    private static final String DEFAULT_URL = "https://chat.deepseek.com/";
    private JTextField urlField;
    private Map<String, JCheckBox> buttonCheckboxes = new HashMap<>();

    // Список путей к Chrome
    private String[] chromePaths = {
            "C:\\GoogleChromePortable\\GoogleChromePortable_139.0.7258.67\\GoogleChromePortable.exe",
            "C:\\GoogleChromePortable\\GoogleChromePortable_140.0.7339.186\\GoogleChromePortable.exe",
            "C:\\GoogleChromePortable\\GoogleChromePortable64_139.0.7258.128\\GoogleChromePortable.exe",
            "C:\\GoogleChromePortable\\GoogleChromePortableDev_140.0.7339.186\\GoogleChromePortable.exe",
            "C:\\GoogleChromePortable\\GoogleChromePortableDev_141.0.7367.4\\GoogleChromePortable.exe",
            "C:\\GoogleChromePortable\\GoogleChromePortable64_001\\GoogleChromePortable.exe",
            "C:\\GoogleChromePortable\\GoogleChromePortableDev_140.0.7312.0\\GoogleChromePortable.exe"
    };

    public ChromeLauncherApp() {
        setTitle("Chrome Launcher");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 420);
        setLocationRelativeTo(null);
        initUI();
    }

    private void initUI() {
        // Основная панель с BorderLayout
        setLayout(new BorderLayout());

        // Верхняя панель с URL
        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Метка
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        topPanel.add(new JLabel("URL (fixed):"), gbc);

        // Поле для ввода URL (только для отображения)
        urlField = new JTextField(DEFAULT_URL, 40);
        urlField.setEditable(false); // Делаем поле нередактируемым
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        topPanel.add(urlField, gbc);

        // Панель для кнопок браузеров
        JPanel browsersPanel = new JPanel();
        browsersPanel.setLayout(new BoxLayout(browsersPanel, BoxLayout.Y_AXIS));
        browsersPanel.setBorder(BorderFactory.createTitledBorder("Launch Chrome Browsers"));

        // Создаем кнопки для каждого браузера
        for (String chromePath : chromePaths) {
            JPanel browserRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

            // Создаем чекбокс (галочка)
            JCheckBox checkBox = new JCheckBox();
            checkBox.setEnabled(false); // Делаем недоступным для кликов пользователя
            buttonCheckboxes.put(chromePath, checkBox);

            // Создаем кнопку с именем браузера
            String buttonName = extractBrowserName(chromePath);
            JButton browserButton = new JButton(buttonName);
            browserButton.setFont(new Font("Arial", Font.PLAIN, 12));
            browserButton.setToolTipText(chromePath);

            // Обработчик для кнопки браузера
            browserButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    launchChromeWithPath(chromePath);
                    // Устанавливаем галочку после успешного запуска
                    checkBox.setSelected(true);
                }
            });

            browserRow.add(checkBox);
            browserRow.add(browserButton);
            // browserRow.add(new JLabel("Path: " + chromePath));

            browsersPanel.add(browserRow);
            browsersPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        }

        // Добавляем скроллинг для панели с браузерами
        JScrollPane scrollPane = new JScrollPane(browsersPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Панель информации
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        //JLabel infoLabel = new JLabel("URL is fixed to: " + DEFAULT_URL);
        //infoLabel.setFont(new Font("Arial", Font.BOLD, 12));
        //infoPanel.add(infoLabel);

        // Кнопка для сброса всех галочек
        JButton resetButton = new JButton("Reset All Checkmarks");
        resetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetAllCheckboxes();
            }
        });
        infoPanel.add(resetButton);

        // Добавляем все панели в окно
        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(infoPanel, BorderLayout.SOUTH);
    }

    private String extractBrowserName(String path) {
        // Извлекаем имя браузера из пути
        String[] parts = path.split("\\\\");
        if (parts.length >= 3) {
            return parts[2]; // Например: GoogleChromePortable_139.0.7258.67
        }
        return "Chrome Browser";
    }

    private void launchChromeWithPath(String chromePath) {
        try {
            // Команда для запуска Chrome с фиксированным URL
            ProcessBuilder processBuilder = new ProcessBuilder(
                    chromePath,
                    "--app=" + DEFAULT_URL,
                    "--new-window"
            );

            Process process = processBuilder.start();

            // Можно раскомментировать для отладки
            /*
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("Chrome OUT: " + line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
            */

           //JOptionPane.showMessageDialog(this,
           //        "Chrome launched successfully!\nPath: " + chromePath,
           //        "Success",
           //        JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to launch Chrome: " + ex.getMessage() +
                            "\nPath: " + chromePath,
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    private void resetAllCheckboxes() {
        int result = JOptionPane.showConfirmDialog(this,
                "Reset all checkmarks?",
                "Confirm Reset",
                JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            for (JCheckBox checkBox : buttonCheckboxes.values()) {
                checkBox.setSelected(false);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChromeLauncherApp app = new ChromeLauncherApp();
            app.setVisible(true);
        });
    }
}