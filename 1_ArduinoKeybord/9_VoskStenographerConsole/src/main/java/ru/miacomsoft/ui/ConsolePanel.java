package ru.miacomsoft.ui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ConsolePanel extends JPanel {
    private JTextPane consoleTextPane;
    private JScrollPane scrollPane;
    private StyledDocument document;
    private JPanel resizeHandle;
    private int lastHeight = 150;
    private boolean isResizing = false;

    // Цвета для разных типов сообщений
    private static final Color COLOR_INFO = new Color(0, 150, 200);
    private static final Color COLOR_SUCCESS = new Color(0, 150, 0);
    private static final Color COLOR_WARNING = new Color(255, 140, 0);
    private static final Color COLOR_ERROR = new Color(197, 32, 32);
    private static final Color COLOR_DEBUG = new Color(128, 128, 128);
    private static final Color COLOR_DEFAULT = Color.BLACK;

    public ConsolePanel() {
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "📋 КОНСОЛЬ (лог событий)",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Monospaced", Font.BOLD, 12),
                Color.GRAY
        ));

        // Создаем текстовую панель с поддержкой стилей
        consoleTextPane = new JTextPane();
        consoleTextPane.setEditable(false);
        consoleTextPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleTextPane.setBackground(new Color(30, 30, 30));
        consoleTextPane.setForeground(Color.WHITE);

        document = consoleTextPane.getStyledDocument();

        // Настраиваем скроллинг
        scrollPane = new JScrollPane(consoleTextPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(scrollPane, BorderLayout.CENTER);

        // Добавляем панель для изменения размера
        resizeHandle = new JPanel();
        resizeHandle.setBackground(new Color(200, 200, 200));
        resizeHandle.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
        resizeHandle.setPreferredSize(new Dimension(getWidth(), 8));

        add(resizeHandle, BorderLayout.SOUTH);

        // Обработка изменения размера
        setupResizeListener();

        // Выводим приветственное сообщение
        printInfo("Консоль инициализирована. Система готова к работе.");
        printSuccess("Стенограф запущен");
    }

    private void setupResizeListener() {
        resizeHandle.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                isResizing = true;
                lastHeight = getHeight();
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                isResizing = false;
            }
        });

        resizeHandle.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (isResizing) {
                    Container parent = getParent();
                    if (parent != null) {
                        int newHeight = lastHeight - e.getY();
                        if (newHeight > 80 && newHeight < parent.getHeight() / 2) {
                            setPreferredSize(new Dimension(getWidth(), newHeight));
                            revalidate();
                            parent.revalidate();
                        }
                    }
                }
            }
        });
    }

    private void appendColoredText(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                String formattedText = String.format("[%s] %s\n", timestamp, text);

                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setForeground(attrs, color);
                StyleConstants.setFontFamily(attrs, "Monospaced");
                StyleConstants.setFontSize(attrs, 12);

                document.insertString(document.getLength(), formattedText, attrs);

                // Автопрокрутка вниз
                consoleTextPane.setCaretPosition(document.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    public void printInfo(String message) {
        appendColoredText("[INFO] " + message, COLOR_INFO);
    }

    public void printSuccess(String message) {
        appendColoredText("[SUCCESS] " + message, COLOR_SUCCESS);
    }

    public void printWarning(String message) {
        appendColoredText("[WARNING] " + message, COLOR_WARNING);
    }

    public void printError(String message) {
        appendColoredText("[ERROR] " + message, COLOR_ERROR);
    }

    public void printDebug(String message) {
        appendColoredText("[DEBUG] " + message, COLOR_DEBUG);
    }

    public void printAudioData(String message) {
        appendColoredText("[AUDIO] " + message, new Color(150, 100, 200));
    }

    public void printRecognition(String message) {
        appendColoredText("[RECOGNITION] " + message, new Color(100, 200, 255));
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            try {
                document.remove(0, document.getLength());
                printInfo("Консоль очищена");
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    public void setMaxHeight(int maxHeight) {
        setPreferredSize(new Dimension(getWidth(), maxHeight));
        revalidate();
    }
}