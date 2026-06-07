package ru.miacomsoft.ui;

import javax.swing.*;
import java.awt.*;

public class TranscriptEntry extends JPanel {
    private JCheckBox checkBox;
    private JTextArea textArea;
    private JLabel timeLabel;
    private JLabel statusLabel;
    private boolean isPartial;
    private String finalText;
    private String partialText;
    private String timestamp;

    public TranscriptEntry(String text, String timestamp) {
        this.finalText = text;
        this.isPartial = false;
        this.timestamp = timestamp;
        initUI(timestamp, text, false);
    }

    public TranscriptEntry(String timestamp, boolean isPartial) {
        this.isPartial = isPartial;
        this.partialText = "";
        this.timestamp = timestamp;
        initUI(timestamp, "", isPartial);
    }

    private void initUI(String timestamp, String text, boolean isPartialEntry) {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        setBackground(Color.WHITE);

        // Верхняя панель с чекбоксом и временем
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(Color.WHITE);

        checkBox = new JCheckBox();
        checkBox.setBackground(Color.WHITE);
        topPanel.add(checkBox, BorderLayout.WEST);

        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        timePanel.setBackground(Color.WHITE);

        if (isPartialEntry) {
            statusLabel = new JLabel("⏳ Распознается...");
            statusLabel.setFont(new Font("Monospaced", Font.ITALIC, 10));
            statusLabel.setForeground(new Color(255, 140, 0));
            timePanel.add(statusLabel);
        }

        timeLabel = new JLabel(timestamp);
        timeLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        timeLabel.setForeground(Color.GRAY);
        timePanel.add(timeLabel);

        topPanel.add(timePanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // Текстовая область
        textArea = new JTextArea();
        textArea.setFont(new Font("Arial", Font.PLAIN, 13));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        textArea.setBackground(Color.WHITE);
        textArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        if (isPartialEntry) {
            textArea.setForeground(new Color(100, 100, 100));
            textArea.setText("⏳ Распознавание речи...");
        } else {
            textArea.setText(text);
            textArea.setForeground(Color.BLACK);
        }

        add(textArea, BorderLayout.CENTER);

        // Устанавливаем предпочтительный размер
        setMaximumSize(new Dimension(Integer.MAX_VALUE, calculatePreferredHeight()));
    }

    public void updatePartialText(String text) {
        this.partialText = text;
        this.isPartial = true;
        if (text != null && !text.isEmpty()) {
            textArea.setText(text);
            textArea.setForeground(new Color(100, 100, 100));
            if (statusLabel != null) {
                statusLabel.setText("⏳ Распознается...");
            }
            // Меняем фон для визуального отличия временной записи
            setBackground(new Color(255, 255, 240));
            // Обновляем размер
            setMaximumSize(new Dimension(Integer.MAX_VALUE, calculatePreferredHeight()));
            revalidate();
        }
    }

    public void finalizeText(String text, String newTimestamp) {
        this.finalText = text;
        this.isPartial = false;
        this.partialText = null;
        this.timestamp = newTimestamp;

        timeLabel.setText(newTimestamp);
        textArea.setText(text);
        textArea.setForeground(Color.BLACK);

        // Убираем статусную метку
        if (statusLabel != null) {
            statusLabel.setText("✓ Распознано");
            statusLabel.setForeground(new Color(0, 150, 0));
            // Через 2 секунды убираем статус
            Timer timer = new Timer(2000, e -> {
                if (statusLabel != null) {
                    statusLabel.setText("");
                }
            });
            timer.setRepeats(false);
            timer.start();
        }

        // Возвращаем нормальный фон
        setBackground(Color.WHITE);

        // Обновляем размер
        setMaximumSize(new Dimension(Integer.MAX_VALUE, calculatePreferredHeight()));
        revalidate();
        repaint();
    }

    private int calculatePreferredHeight() {
        if (textArea == null) {
            return 80;
        }

        FontMetrics fm = textArea.getFontMetrics(textArea.getFont());
        String text = textArea.getText();

        int maxWidth = textArea.getWidth();
        if (maxWidth <= 0) {
            maxWidth = 500;
        }

        // Создаем временный объект для расчета
        JTextArea tempArea = new JTextArea(text);
        tempArea.setFont(textArea.getFont());
        tempArea.setSize(maxWidth, Integer.MAX_VALUE);
        tempArea.setLineWrap(true);
        tempArea.setWrapStyleWord(true);

        int preferredHeight = tempArea.getPreferredSize().height;
        int height = Math.min(300, preferredHeight + 60);

        return Math.max(80, height);
    }

    public boolean isSelected() {
        return checkBox.isSelected();
    }

    public void setSelected(boolean selected) {
        checkBox.setSelected(selected);
    }

    public boolean isPartial() {
        return isPartial;
    }

    public String getFullText() {
        if (finalText != null && !finalText.isEmpty()) {
            return finalText;
        }
        if (partialText != null && !partialText.isEmpty()) {
            return partialText;
        }
        return textArea.getText();
    }

    public void setFinalText(String text) {
        this.finalText = text;
        this.isPartial = false;
        textArea.setText(text);
        textArea.setForeground(Color.BLACK);
    }
}