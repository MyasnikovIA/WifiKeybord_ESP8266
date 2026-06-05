package ru.miacomsoft.ui;

import ru.miacomsoft.TextPunctuator;
import ru.miacomsoft.audio.AudioDeviceInfo;
import ru.miacomsoft.recognition.RecognitionWorker;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TranscriptPanel extends JPanel {
    private String title;
    private String statusText;
    private Color accentColor;

    private JPanel entriesPanel;
    private JScrollPane scrollPane;
    private JButton clearButton;
    private JButton copySelectedButton;
    private JButton copyAllButton;
    private JLabel statusLabel;
    private JLabel recognitionStatusLabel;

    private List<TranscriptEntry> entries;
    private RecognitionWorker recognitionWorker;
    private boolean isSystemAudio;
    private TranscriptEntry currentPartialEntry;
    private ConsolePanel consolePanel;

    // Переменная для хранения записи, по которой был клик
    private TranscriptEntry clickedEntry;

    public TranscriptPanel(String title, String statusText, Color accentColor, ConsolePanel consolePanel) {
        this.title = title;
        this.statusText = statusText;
        this.accentColor = accentColor;
        this.consolePanel = consolePanel;
        this.entries = new ArrayList<>();
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accentColor, 2),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        // Заголовок
        TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(accentColor),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 14),
                accentColor
        );
        setBorder(titledBorder);

        // Панель с записями
        entriesPanel = new JPanel();
        entriesPanel.setLayout(new BoxLayout(entriesPanel, BoxLayout.Y_AXIS));
        entriesPanel.setBackground(Color.WHITE);

        scrollPane = new JScrollPane(entriesPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        add(scrollPane, BorderLayout.CENTER);

        // Нижняя панель с кнопками
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);

        // Верхняя панель статуса
        JPanel topStatusPanel = new JPanel(new BorderLayout());
        topStatusPanel.setBackground(Color.WHITE);

        statusLabel = new JLabel("⚪ " + statusText);
        statusLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        statusLabel.setForeground(Color.GRAY);
        topStatusPanel.add(statusLabel, BorderLayout.WEST);

        recognitionStatusLabel = new JLabel("");
        recognitionStatusLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        recognitionStatusLabel.setForeground(new Color(255, 140, 0));
        topStatusPanel.add(recognitionStatusLabel, BorderLayout.EAST);

        add(topStatusPanel, BorderLayout.NORTH);

        // Контекстное меню
        setupContextMenu();
    }

    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        panel.setBackground(new Color(240, 240, 240));

        clearButton = new JButton("🗑 Очистить стек");
        clearButton.addActionListener(e -> clearAllEntries());
        panel.add(clearButton);

        copySelectedButton = new JButton("📋 Копировать выделенные");
        copySelectedButton.addActionListener(e -> copySelectedToClipboard());
        panel.add(copySelectedButton);

        copyAllButton = new JButton("📄 Копировать все");
        copyAllButton.addActionListener(e -> copyAllToClipboard());
        panel.add(copyAllButton);

        return panel;
    }

    private void setupContextMenu() {
        JPopupMenu contextMenu = new JPopupMenu();

        JMenuItem deleteItem = new JMenuItem("Удалить запись");
        deleteItem.addActionListener(e -> {
            if (clickedEntry != null) {
                deleteSingleEntry(clickedEntry);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Не выбрана запись для удаления",
                        "Ошибка", JOptionPane.WARNING_MESSAGE);
            }
        });
        contextMenu.add(deleteItem);

        JMenuItem deleteSelectedItem = new JMenuItem("Удалить выбранные записи");
        deleteSelectedItem.addActionListener(e -> deleteSelectedEntries());
        contextMenu.add(deleteSelectedItem);

        contextMenu.addSeparator();

        JMenuItem checkToEndItem = new JMenuItem("Поставить галочки от выбранной до конца");
        checkToEndItem.addActionListener(e -> checkToEnd());
        contextMenu.add(checkToEndItem);

        JMenuItem uncheckAllItem = new JMenuItem("Снять выделение со всех строк");
        uncheckAllItem.addActionListener(e -> uncheckAll());
        contextMenu.add(uncheckAllItem);

        JMenuItem checkAllItem = new JMenuItem("Выделить все строки");
        checkAllItem.addActionListener(e -> checkAll());
        contextMenu.add(checkAllItem);

        contextMenu.addSeparator();

        JMenuItem copySelectedContextItem = new JMenuItem("Копировать выбранные");
        copySelectedContextItem.addActionListener(e -> copySelectedToClipboard());
        contextMenu.add(copySelectedContextItem);

        // Добавляем контекстное меню на каждую запись
        // и обрабатываем клики на панели entriesPanel
        entriesPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Находим компонент, на котором был клик
                Component component = entriesPanel.getComponentAt(e.getPoint());
                if (component instanceof TranscriptEntry) {
                    clickedEntry = (TranscriptEntry) component;
                } else {
                    clickedEntry = null;
                }

                if (e.isPopupTrigger()) {
                    contextMenu.show(entriesPanel, e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    // Находим компонент при отпускании
                    Component component = entriesPanel.getComponentAt(e.getPoint());
                    if (component instanceof TranscriptEntry) {
                        clickedEntry = (TranscriptEntry) component;
                    } else {
                        clickedEntry = null;
                    }
                    contextMenu.show(entriesPanel, e.getX(), e.getY());
                }
            }
        });

        // Также добавляем контекстное меню на каждую запись при её создании
    }

    private void deleteSingleEntry(TranscriptEntry entry) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Удалить выбранную запись?",
                "Подтверждение",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            int index = entries.indexOf(entry);
            if (index != -1) {
                // Проверяем, не удаляем ли мы текущую временную запись
                if (currentPartialEntry == entry) {
                    currentPartialEntry = null;
                }
                entries.remove(index);
                entriesPanel.remove(entry);
                entriesPanel.revalidate();
                entriesPanel.repaint();

                if (consolePanel != null) {
                    consolePanel.printInfo("Запись удалена");
                }
            }
        }
    }

    public void startRecognition(String modelPath, boolean isSystemAudio, AudioDeviceInfo device) {
        this.isSystemAudio = isSystemAudio;

        if (recognitionWorker != null && recognitionWorker.isRunning()) {
            if (consolePanel != null) {
                consolePanel.printWarning("Распознавание уже запущено");
            }
            return;
        }

        recognitionWorker = new RecognitionWorker(modelPath, 16000, this, isSystemAudio, device, consolePanel);
        recognitionWorker.start();

        statusLabel.setText("🟢 СТАТУС: Активно - " +
                (isSystemAudio ? "Запись системного звука" : "Запись: " + device.getName()));
        statusLabel.setForeground(new Color(46, 125, 50));
        recognitionStatusLabel.setText("⏳ Распознавание...");

        if (consolePanel != null) {
            consolePanel.printSuccess("Распознавание запущено: " +
                    (isSystemAudio ? "Системный звук" : device.getName()));
        }
    }

    public void stopRecognition() {
        if (recognitionWorker != null) {
            recognitionWorker.stop();
            recognitionWorker = null;
        }

        // Финализируем последнюю временную запись, если она есть
        if (currentPartialEntry != null && currentPartialEntry.isPartial()) {
            currentPartialEntry.finalizeText(currentPartialEntry.getFullText(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            currentPartialEntry = null;
        }

        statusLabel.setText("⚪ СТАТУС: Остановлен");
        statusLabel.setForeground(Color.GRAY);
        recognitionStatusLabel.setText("");
    }

    public void addPartialText(String partialText) {
        String finalPartialText = TextPunctuator.fixWindowsEncoding(partialText);
        SwingUtilities.invokeLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            // Если нет текущей временной записи, создаем её
            if (currentPartialEntry == null) {
                currentPartialEntry = new TranscriptEntry(timestamp, true);
                entries.add(currentPartialEntry);
                entriesPanel.add(currentPartialEntry);
                recognitionStatusLabel.setText("⏳ Распознается: " +
                        (finalPartialText.length() > 40 ? finalPartialText.substring(0, 40) + "..." : finalPartialText));
            }

            // Обновляем текст временной записи
            currentPartialEntry.updatePartialText(finalPartialText);

            entriesPanel.revalidate();
            entriesPanel.repaint();

            // Автопрокрутка
            SwingUtilities.invokeLater(() -> {
                JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
                verticalBar.setValue(verticalBar.getMaximum());
            });
        });
    }

    public void addFinalText(String text, String timestamp) {
        text = TextPunctuator.fixWindowsEncoding(text);
        String finalText = text;
        SwingUtilities.invokeLater(() -> {
            // Если есть временная запись, заменяем её на финальную
            if (currentPartialEntry != null) {
                int index = entries.indexOf(currentPartialEntry);
                if (index != -1) {
                    currentPartialEntry.finalizeText(finalText, timestamp);
                    currentPartialEntry = null;
                    recognitionStatusLabel.setText("✓ Распознано");

                    // Сбрасываем статус через 2 секунды
                    Timer timer = new Timer(2000, e -> {
                        recognitionStatusLabel.setText("");
                    });
                    timer.setRepeats(false);
                    timer.start();
                } else {
                    // Если по какой-то причине не нашли, создаем новую запись
                    TranscriptEntry entry = new TranscriptEntry(finalText, timestamp);
                    entries.add(entry);
                    entriesPanel.add(entry);
                }
            } else {
                // Создаем новую финальную запись
                TranscriptEntry entry = new TranscriptEntry(finalText, timestamp);
                entries.add(entry);
                entriesPanel.add(entry);
            }

            entriesPanel.revalidate();
            entriesPanel.repaint();

            // Автопрокрутка
            SwingUtilities.invokeLater(() -> {
                JScrollBar verticalBar = scrollPane.getVerticalScrollBar();
                verticalBar.setValue(verticalBar.getMaximum());
            });
        });
    }

    public void updatePartialText(String partialText, String timestamp) {
        addPartialText(partialText);
    }

    private void clearAllEntries() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Очистить весь стек записей?",
                "Подтверждение",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            entries.clear();
            currentPartialEntry = null;
            entriesPanel.removeAll();
            entriesPanel.revalidate();
            entriesPanel.repaint();
            recognitionStatusLabel.setText("");
        }
    }

    private void copySelectedToClipboard() {
        List<TranscriptEntry> selected = entries.stream()
                .filter(TranscriptEntry::isSelected)
                .collect(Collectors.toList());

        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Нет выделенных записей",
                    "Информация", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selected.size(); i++) {
            TranscriptEntry entry = selected.get(i);
            sb.append(entry.getFullText());
            if (i < selected.size() - 1) {
                sb.append("\n\n");
            }
        }

        copyToClipboard(sb.toString());
        JOptionPane.showMessageDialog(this,
                "Скопировано " + selected.size() + " записей",
                "Успех", JOptionPane.INFORMATION_MESSAGE);
    }

    private void copyAllToClipboard() {
        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Нет записей для копирования",
                    "Информация", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            TranscriptEntry entry = entries.get(i);
            if (!entry.isPartial()) { // Копируем только финальные записи
                sb.append(entry.getFullText());
                if (i < entries.size() - 1) {
                    sb.append("\n\n");
                }
            }
        }

        copyToClipboard(sb.toString());
        JOptionPane.showMessageDialog(this,
                "Скопировано записей",
                "Успех", JOptionPane.INFORMATION_MESSAGE);
    }

    private void copyToClipboard(String text) {
        StringSelection selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }

    private void deleteSelectedEntries() {
        List<TranscriptEntry> toDelete = entries.stream()
                .filter(TranscriptEntry::isSelected)
                .collect(Collectors.toList());

        if (toDelete.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Нет выбранных записей",
                    "Информация", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Удалить " + toDelete.size() + " выбранных записей?",
                "Подтверждение",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            // Проверяем, не удаляем ли мы текущую временную запись
            if (currentPartialEntry != null && toDelete.contains(currentPartialEntry)) {
                currentPartialEntry = null;
            }

            entries.removeAll(toDelete);
            for (TranscriptEntry entry : toDelete) {
                entriesPanel.remove(entry);
            }
            entriesPanel.revalidate();
            entriesPanel.repaint();
        }
    }

    private void checkToEnd() {
        boolean found = false;
        for (int i = 0; i < entries.size(); i++) {
            if (found) {
                entries.get(i).setSelected(true);
            } else if (entries.get(i).isSelected()) {
                found = true;
                entries.get(i).setSelected(true);
            }
        }
    }

    private void uncheckAll() {
        for (TranscriptEntry entry : entries) {
            entry.setSelected(false);
        }
    }

    private void checkAll() {
        for (TranscriptEntry entry : entries) {
            entry.setSelected(true);
        }
    }

    public boolean isRecognitionRunning() {
        return recognitionWorker != null && recognitionWorker.isRunning();
    }
}