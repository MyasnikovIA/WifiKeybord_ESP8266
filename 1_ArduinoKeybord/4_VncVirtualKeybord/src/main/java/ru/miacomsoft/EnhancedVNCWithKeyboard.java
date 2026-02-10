package ru.miacomsoft;

import javax.crypto.*;
import javax.crypto.spec.*;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.fazecast.jSerialComm.*;
import java.io.OutputStream;

import static ru.miacomsoft.KeyCodes.*;

public class EnhancedVNCWithKeyboard {
    // Конфигурация VNC по умолчанию
    private static final String DEFAULT_HOST = "192.168.15.123";
    private static final int DEFAULT_PORT = 5900;
    private static final String DEFAULT_PASSWORD = "XXXXXXXXXX";

    private static JFrame mainFrame;
    private static JFrame vncFrame;
    private static volatile boolean connected = false;
    private static volatile boolean vncRunning = false;

    // VNC компоненты
    private static Socket socket;
    private static DataInputStream in;
    private static DataOutputStream out;
    private static BufferedImage screenImage;
    private static ScheduledExecutorService executor;
    private static int screenWidth = 1024;
    private static int screenHeight = 768;
    private static double scale = 1.0;
    private static int offsetX = 0;
    private static int offsetY = 0;

    // Состояние мыши
    private static int mouseButtons = 0;
    private static Point lastMousePosition = new Point(0, 0);
    private static boolean mouseSyncEnabled = true;

    // Для выделения области - КРАСНЫЙ КВАДРАТ
    private static Point selectionStart = null;
    private static Point selectionEnd = null;
    private static boolean isSelecting = false;
    private static boolean selectionMode = false;
    private static Rectangle selectionRect = null;

    // Панель управления VNC
    private static JLabel statusLabel;
    private static JLabel fpsLabel;
    private static JLabel resolutionLabel;
    private static JLabel mousePositionLabel;

    // Для подключения к COM-порту
    private static JComboBox<String> portComboBox;
    private static JComboBox<String> baudRateComboBox;
    private static JButton connectButton;
    private static JLabel connectionStatusLabel;
    private static SerialPort serialPort;
    private static OutputStream serialOutputStream;

    // Для текстового поля и управления передачей
    private static JTextPane messageTextPane;
    private static JButton startButton;
    private static JButton pauseButton;
    private static JButton stopButton;
    private static JButton toggleLayoutButton;
    private static JLabel lineNumberLabel;
    private static JLabel timeRemainingLabel;
    private static JLabel layoutStatusLabel;

    // Галочка "IDE"
    private static JCheckBox ideCheckBox;

    // Кнопка подключения VNC (перенесена в панель управления передачей)
    private static JButton vncConnectBtn;
    private static JButton vncDisconnectBtn;

    // Split pane для разделения VNC и текста
    private static JSplitPane splitPane;
    private static JLabel videoLabel;

    // Для передачи данных
    private static Thread transmissionThread;
    private static boolean isPaused = false;
    private static boolean isStopped = false;
    private static boolean isSerialConnected = false;
    private static boolean isEnglish = true;
    private static int currentTransmissionPosition = 0;
    private static int totalCharactersToSend = 0;
    private static long transmissionStartTime = 0;
    private static long lastUpdateTime = 0;

    // Константы задержек
    private static final int BASE_DELAY = 100;
    private static final int SWITCH_LAYOUT_DELAY = 400;
    private static final int SPECIAL_CHAR_DELAY = 300;
    private static final int VIRTUAL_KEY_DELAY = 50;

    // Флаг для отслеживания, находится ли видео в фокусе
    private static boolean videoHasFocus = false;

    // Статистика VNC
    private static AtomicInteger fps = new AtomicInteger(0);
    private static long lastFpsUpdate = System.currentTimeMillis();
    private static AtomicInteger frameCount = new AtomicInteger(0);
    private static long totalBytesReceived = 0;

    // Для отрисовки красного квадрата выделения
    private static BufferedImage currentDisplayImage = null;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            createAndShowGUI();
        });
    }

    private static void createAndShowGUI() {
        // Создаем главное окно управления
        mainFrame = new JFrame("Управление VNC трансляцией и COM-портом");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLayout(new BorderLayout());

        // Панель информации и состояния
        JPanel infoPanel = new JPanel(new GridLayout(1, 4, 5, 5));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        statusLabel = new JLabel("Готов к работе");
        fpsLabel = new JLabel("FPS: 0");
        resolutionLabel = new JLabel("Разрешение: --");
        mousePositionLabel = new JLabel("Мышь: [0,0] | Синхр: ВКЛ");

        infoPanel.add(statusLabel);
        infoPanel.add(fpsLabel);
        infoPanel.add(resolutionLabel);
        infoPanel.add(mousePositionLabel);

        // Панель управления
        JPanel controlPanel = new JPanel(new FlowLayout());

        JButton helpBtn = new JButton("Справка (F1)");
        helpBtn.addActionListener(e -> showHelpDialog());

        controlPanel.add(helpBtn);

        // Собираем главное окно
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(infoPanel, BorderLayout.NORTH);
        mainPanel.add(controlPanel, BorderLayout.CENTER);

        mainFrame.add(mainPanel, BorderLayout.CENTER);
        mainFrame.setSize(600, 150);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);

        // Добавляем горячие клавиши
        setupGlobalKeyBindings(mainFrame);

        // Запускаем VNC окно
        startVNCDisplay();
    }

    private static void startVNCDisplay() {
        SwingUtilities.invokeLater(() -> {
            createVNCWindow();
        });
    }

    private static void createVNCWindow() {
        vncFrame = new JFrame("VNC трансляция с клавиатурным вводом");
        vncFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        vncFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnectVNC();
            }
        });

        vncFrame.setLayout(new BorderLayout());

        // Создаем панель управления передачей (верхняя панель)
        JPanel controlPanel = createControlPanel();

        // Создаем левую панель для настроек VNC (пустая - кнопки удалены)
        JPanel leftPanel = createLeftPanel();

        // Создаем панель для VNC видео (правая панель)
        JPanel vncVideoPanel = new JPanel(new BorderLayout());
        vncVideoPanel.setBorder(BorderFactory.createTitledBorder("VNC трансляция"));
        vncVideoPanel.setMinimumSize(new Dimension(400, 200));

        // Создаем компонент для отображения VNC
        videoLabel = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                // Рисуем красный квадрат выделения, если идет выделение
                if (selectionMode && isSelecting && selectionStart != null && selectionEnd != null) {
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setColor(Color.RED);
                    g2d.setStroke(new BasicStroke(2));

                    int x = Math.min(selectionStart.x, selectionEnd.x);
                    int y = Math.min(selectionStart.y, selectionEnd.y);
                    int width = Math.abs(selectionEnd.x - selectionStart.x);
                    int height = Math.abs(selectionEnd.y - selectionStart.y);

                    g2d.drawRect(x, y, width, height);

                    // Добавляем текст с размерами
                    g2d.setFont(new Font("Arial", Font.BOLD, 14));
                    String sizeText = width + "x" + height;
                    g2d.drawString(sizeText, x + 5, y + height - 5);

                    g2d.dispose();
                }
            }
        };
        videoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        videoLabel.setPreferredSize(new Dimension(screenWidth, screenHeight));

        // Добавляем слушатели мыши для VNC управления и выделения области
        addMouseListenersToVideoLabel(videoLabel);

        // УБРАНО: Фокус и прямая передача через COM-порт
        // Добавляем только базовый обработчик для получения фокуса
        videoLabel.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                videoHasFocus = true;
                videoLabel.setBorder(BorderFactory.createLineBorder(Color.BLUE, 2));
                updateStatus("VNC в фокусе - клавиши отправляются на удаленный ПК");
            }

            @Override
            public void focusLost(FocusEvent e) {
                videoHasFocus = false;
                videoLabel.setBorder(null);
                updateStatus("Клавиатурный ввод в VNC отключен");
            }
        });

        // Добавляем слушатель кликов для активации фокуса
        videoLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                videoLabel.requestFocusInWindow();
            }
        });

        // ИСПРАВЛЕННЫЙ обработчик клавиш: ТОЛЬКО отправка в VNC
        videoLabel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // F9 - переключение между режимом мыши и выделением области
                if (e.getKeyCode() == KeyEvent.VK_F9) {
                    if (mouseSyncEnabled) {
                        // Включаем режим выделения области
                        mouseSyncEnabled = false;
                        selectionMode = true;
                        updateStatus("Режим выделения области включен (F9 для возврата)");
                    } else {
                        // Включаем режим мыши
                        mouseSyncEnabled = true;
                        selectionMode = false;
                        clearSelection();
                        updateStatus("Режим мыши включен (F9 для выделения)");
                    }
                    updateMousePositionLabel();
                    e.consume();
                }
                // Отправка клавиши в VNC (всегда, если подключено)
                else if (connected) {
                    sendVNCKeyEvent(e.getKeyCode(), true);
                    e.consume(); // Предотвращаем обработку по умолчанию
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                // F9 уже обработан в keyPressed
                if (e.getKeyCode() == KeyEvent.VK_F9) {
                    e.consume();
                }
                // Отправка отпускания клавиши в VNC
                else if (connected) {
                    sendVNCKeyEvent(e.getKeyCode(), false);
                    e.consume(); // Предотвращаем обработку по умолчанию
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {
                // Обработка символов для VNC
                if (connected) {
                    char keyChar = e.getKeyChar();
                    // Для обычных символов используем коды клавиш из keyPressed/keyReleased
                    // Этот метод оставлен для совместимости
                    e.consume(); // Предотвращаем обработку по умолчанию
                }
            }
        });

        videoLabel.setFocusable(true);
        vncVideoPanel.add(videoLabel, BorderLayout.CENTER);

        // Создаем горизонтальный разделитель между левой и правой панелями
        JSplitPane horizontalSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        horizontalSplitPane.setLeftComponent(leftPanel);
        horizontalSplitPane.setRightComponent(vncVideoPanel);
        horizontalSplitPane.setDividerLocation(200);
        horizontalSplitPane.setResizeWeight(0.2);
        horizontalSplitPane.setOneTouchExpandable(true);

        // Создаем вертикальный разделитель между панелью управления и видео
        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(controlPanel);
        splitPane.setBottomComponent(horizontalSplitPane);
        splitPane.setDividerLocation(200);
        splitPane.setResizeWeight(0.3);
        splitPane.setOneTouchExpandable(true);

        vncFrame.add(splitPane, BorderLayout.CENTER);
        vncFrame.pack();
        vncFrame.setSize(1024, 768);
        vncFrame.setLocationRelativeTo(null);
        vncFrame.setVisible(true);
    }

    private static void addMouseListenersToVideoLabel(JLabel videoLabel) {
        videoLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (selectionMode && e.getButton() == MouseEvent.BUTTON1) {
                    // Начало выделения области
                    selectionStart = e.getPoint();
                    selectionEnd = e.getPoint();
                    isSelecting = true;
                    updateStatus("Начало выделения области");
                    videoLabel.repaint(); // Принудительная перерисовка для отображения квадрата
                } else if (connected && mouseSyncEnabled && !selectionMode) {
                    // Отправка события мыши в VNC
                    handleVNCMousePress(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (selectionMode && isSelecting && e.getButton() == MouseEvent.BUTTON1) {
                    // Завершение выделения области
                    selectionEnd = e.getPoint();
                    isSelecting = false;
                    updateSelectionRect();

                    // Автоматическое копирование в буфер обмена
                    copySelectionToClipboard();

                    // Автоматически выключаем режим выделения после копирования
                    selectionMode = false;
                    updateStatus("Область скопирована в буфер. Режим выделения выключен.");

                    // Очищаем выделение через 500 мс
                    Timer clearTimer = new Timer(500, evt -> clearSelection());
                    clearTimer.setRepeats(false);
                    clearTimer.start();
                } else if (connected && mouseSyncEnabled && !selectionMode) {
                    // Отправка события мыши в VNC
                    handleVNCMouseRelease(e);
                }
            }
        });

        videoLabel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (selectionMode && isSelecting && selectionStart != null) {
                    // Обновление выделения при движении мыши
                    selectionEnd = e.getPoint();
                    updateSelectionRect();
                    updateMousePosition(e);
                    videoLabel.repaint(); // Принудительная перерисовка для обновления квадрата
                } else if (connected && mouseSyncEnabled && !selectionMode) {
                    // Отправка движения мыши в VNC
                    handleVNCMouseMove(e);
                    updateMousePosition(e);
                } else if (connected) {
                    // Только обновление позиции мыши без отправки в VNC
                    updateMousePosition(e);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (selectionMode && isSelecting && selectionStart != null) {
                    // Перетаскивание для выделения
                    selectionEnd = e.getPoint();
                    updateSelectionRect();
                    updateMousePosition(e);
                    videoLabel.repaint(); // Принудительная перерисовка для обновления квадрата
                } else if (connected && mouseSyncEnabled && !selectionMode) {
                    // Отправка движения мыши в VNC
                    handleVNCMouseMove(e);
                    updateMousePosition(e);
                } else if (connected) {
                    // Только обновление позиции мыши без отправки в VNC
                    updateMousePosition(e);
                }
            }
        });

        videoLabel.addMouseWheelListener(e -> {
            if (connected && mouseSyncEnabled && !selectionMode) {
                handleVNCMouseWheel(e);
            }
        });
    }

    private static void updateMousePositionLabel() {
        SwingUtilities.invokeLater(() -> {
            String syncStatus = mouseSyncEnabled ? "ВКЛ" : "ВЫКЛ";
            String selectionModeStatus = selectionMode ? " (Выделение)" : "";
            mousePositionLabel.setText("Мышь: [0,0] | Синхр: " + syncStatus + selectionModeStatus);
        });
    }

    private static JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Информация о VNC"));
        leftPanel.setMinimumSize(new Dimension(150, 200));
        leftPanel.setPreferredSize(new Dimension(200, 600));

        // Панель с информацией о подключении
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("Подключение VNC"));
        return leftPanel;
    }

    private static JPanel createControlPanel() {
        // Создаем главную панель управления
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setBorder(BorderFactory.createTitledBorder("Управление передачей через COM-порт"));
        controlPanel.setMinimumSize(new Dimension(400, 150));

        // Панель со всеми контролами в одной линии
        JPanel controlsLinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        controlsLinePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Создаем компоненты управления COM-портом
        JLabel portLabel = new JLabel("Порт:");

        SerialPort[] ports = SerialPort.getCommPorts();
        String[] portNames = new String[ports.length + 1];
        portNames[0] = "Выберите порт";
        for (int i = 0; i < ports.length; i++) {
            portNames[i + 1] = ports[i].getSystemPortName() + " - " + ports[i].getDescriptivePortName();
        }
        portComboBox = new JComboBox<>(portNames);
        portComboBox.setPreferredSize(new Dimension(200, 25));

        JLabel baudLabel = new JLabel("Скорость:");
        String[] baudRates = {"9600", "19200", "38400", "57600", "115200", "230400"};
        baudRateComboBox = new JComboBox<>(baudRates);
        baudRateComboBox.setSelectedItem("115200");
        baudRateComboBox.setPreferredSize(new Dimension(100, 25));

        connectButton = new JButton("Подключить COM");
        connectionStatusLabel = new JLabel("COM: Не подключено");
        connectionStatusLabel.setForeground(Color.RED);

        ideCheckBox = new JCheckBox("IDE", true);

        startButton = new JButton("Старт");
        pauseButton = new JButton("Пауза");
        stopButton = new JButton("Стоп");
        toggleLayoutButton = new JButton("Rus/Lat");

        lineNumberLabel = new JLabel("Строка: 0");
        timeRemainingLabel = new JLabel("Осталось: --:--");
        timeRemainingLabel.setForeground(new Color(0, 100, 200));

        layoutStatusLabel = new JLabel("Lat");
        layoutStatusLabel.setForeground(new Color(0, 100, 200));

        // Кнопки управления VNC (перенесены сюда)
        vncConnectBtn = new JButton("Подключить VNC");
        vncDisconnectBtn = new JButton("Отключить VNC");

        // Добавляем слушатели
        connectButton.addActionListener(e -> toggleSerialConnection());
        vncConnectBtn.addActionListener(e -> connectVNC());
        vncDisconnectBtn.addActionListener(e -> disconnectVNC());
        startButton.addActionListener(e -> startTransmission());
        pauseButton.addActionListener(e -> togglePause());
        stopButton.addActionListener(e -> stopTransmission());
        toggleLayoutButton.addActionListener(e -> toggleKeyboardLayout());

        vncDisconnectBtn.setEnabled(false);

        // Добавляем все компоненты в одну линию
        controlsLinePanel.add(portLabel);
        controlsLinePanel.add(portComboBox);
        controlsLinePanel.add(baudLabel);
        controlsLinePanel.add(baudRateComboBox);
        controlsLinePanel.add(connectButton);
        controlsLinePanel.add(connectionStatusLabel);
        controlsLinePanel.add(ideCheckBox);

        // Добавляем кнопки VNC
        controlsLinePanel.add(vncConnectBtn);
        controlsLinePanel.add(vncDisconnectBtn);

        controlsLinePanel.add(startButton);
        controlsLinePanel.add(pauseButton);
        controlsLinePanel.add(stopButton);
        controlsLinePanel.add(toggleLayoutButton);
        controlsLinePanel.add(layoutStatusLabel);
        controlsLinePanel.add(lineNumberLabel);
        controlsLinePanel.add(timeRemainingLabel);

        // Панель для текстового поля (ниже контролов)
        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        messageTextPane = new JTextPane();
        messageTextPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        messageTextPane.setEditable(false);

        textPanel.add(new JScrollPane(messageTextPane), BorderLayout.CENTER);

        // Объединяем все в главную панель
        controlPanel.add(controlsLinePanel, BorderLayout.NORTH);
        controlPanel.add(textPanel, BorderLayout.CENTER);

        updateTransmissionButtons(false, false);

        return controlPanel;
    }

    private static void setupGlobalKeyBindings(JFrame frame) {
        InputMap inputMap = frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = frame.getRootPane().getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "showHelp");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelSelection");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copySelection");

        actionMap.put("showHelp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showHelpDialog();
            }
        });

        actionMap.put("cancelSelection", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectionMode) {
                    selectionMode = false;
                    clearSelection();
                    updateStatus("Режим выделения отменен");
                    updateMousePositionLabel();
                }
            }
        });

        actionMap.put("copySelection", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectionRect != null) {
                    copySelectionToClipboard();
                }
            }
        });
    }

    private static void showHelpDialog() {
        JDialog helpDialog = new JDialog(mainFrame, "Справка по управлению", true);
        helpDialog.setLayout(new BorderLayout());

        JTextArea instructions = new JTextArea();
        instructions.setText("Управление VNC трансляцией и COM-портом:\n\n" +
                "Подключение VNC:\n" +
                "  • Нажмите 'Подключить VNC' в верхней панели\n" +
                "  • Конфигурация: " + DEFAULT_HOST + ":" + DEFAULT_PORT + "\n\n" +
                "Управление мышью/выделением:\n" +
                "  • F9: переключение между режимами\n" +
                "  • Режим мыши: управление удаленным ПК\n" +
                "  • Режим выделения: выделение области ЛКМ\n" +
                "  • Автокопирование в буфер при отпускании ЛКМ\n" +
                "  • Esc: отмена режима выделения\n\n" +
                "Клавиатурный ввод в VNC:\n" +
                "  • Кликните на видео для активации\n" +
                "  • Нажимайте клавиши - они будут отправляться в VNC\n" +
                "  • Поддерживаются все стандартные клавиши\n\n" +
                "Передача текста через COM-порт:\n" +
                "  • Подключитесь к COM-порту\n" +
                "  • Введите текст в поле\n" +
                "  • Нажмите 'Старт' для передачи\n" +
                "  • Используйте 'Rus/Lat' для переключения раскладки\n" +
                "  • Включите галочку 'IDE' для специальной обработки символов\n\n" +
                "Горячие клавиши:\n" +
                "  • F1: эта справка\n" +
                "  • F9: переключение мышь/выделение\n" +
                "  • Esc: отмена выделения\n" +
                "  • Ctrl+C: копирование выделенной области");
        instructions.setEditable(false);
        instructions.setFont(new Font("Monospaced", Font.PLAIN, 12));
        instructions.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(instructions);
        scrollPane.setPreferredSize(new Dimension(500, 400));

        JButton closeButton = new JButton("Закрыть");
        closeButton.addActionListener(e -> helpDialog.dispose());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(closeButton);

        helpDialog.add(scrollPane, BorderLayout.CENTER);
        helpDialog.add(buttonPanel, BorderLayout.SOUTH);

        helpDialog.pack();
        helpDialog.setLocationRelativeTo(mainFrame);
        helpDialog.setVisible(true);
    }

    // ================= VNC МЕТОДЫ =================

    private static void connectVNC() {
        if (vncRunning) {
            return;
        }

        new Thread(() -> {
            try {
                updateStatus("Подключение к VNC серверу...");

                socket = new Socket();
                socket.setSoTimeout(5000);
                socket.connect(new InetSocketAddress(DEFAULT_HOST, DEFAULT_PORT), 5000);

                in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 65536));
                out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 65536));
                socket.setTcpNoDelay(true);

                // Handshake
                byte[] serverVersion = new byte[12];
                in.readFully(serverVersion);
                String serverVerStr = new String(serverVersion).trim();

                out.write("RFB 003.008\n".getBytes());
                out.flush();

                int numAuthTypes = in.readByte() & 0xFF;
                byte[] authTypes = new byte[numAuthTypes];
                in.readFully(authTypes);

                boolean authSelected = false;
                for (byte authType : authTypes) {
                    if (authType == 1) {
                        out.writeByte(1);
                        out.flush();
                        authSelected = true;
                        break;
                    } else if (authType == 2) {
                        out.writeByte(2);
                        out.flush();

                        byte[] challenge = new byte[16];
                        in.readFully(challenge);

                        byte[] encrypted = vncAuthEncrypt(DEFAULT_PASSWORD, challenge);
                        out.write(encrypted);
                        out.flush();

                        authSelected = true;
                        break;
                    }
                }

                if (!authSelected) {
                    throw new IOException("Нет поддерживаемого типа аутентификации");
                }

                int authResult = in.readInt();
                if (authResult != 0) {
                    throw new IOException("Аутентификация не удалась");
                }

                out.writeByte(1);
                out.flush();

                screenWidth = in.readUnsignedShort();
                screenHeight = in.readUnsignedShort();

                byte[] pixelFormat = new byte[16];
                in.readFully(pixelFormat);

                int nameLength = in.readInt();
                byte[] nameBytes = new byte[nameLength];
                in.readFully(nameBytes);
                String serverName = new String(nameBytes);

                screenImage = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_RGB);
                connected = true;
                vncRunning = true;

                SwingUtilities.invokeLater(() -> {
                    vncConnectBtn.setEnabled(false);
                    vncDisconnectBtn.setEnabled(true);
                    resolutionLabel.setText(String.format("Разрешение: %dx%d", screenWidth, screenHeight));
                    updateStatus("Подключено к VNC: " + serverName);
                });

                startVNCUpdate();

            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    updateStatus("Ошибка подключения к VNC: " + e.getMessage());
                    showErrorImage(e.getMessage());
                });
            }
        }).start();
    }

    private static byte[] vncAuthEncrypt(String password, byte[] challenge) {
        try {
            byte[] key = new byte[8];
            byte[] passwordBytes = password.getBytes("ASCII");
            System.arraycopy(passwordBytes, 0, key, 0, Math.min(passwordBytes.length, 8));

            key = reverseBits(key);

            SecretKeySpec desKey = new SecretKeySpec(key, "DES");
            Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, desKey);

            byte[] encrypted = new byte[16];
            byte[] block1 = cipher.doFinal(Arrays.copyOfRange(challenge, 0, 8));
            byte[] block2 = cipher.doFinal(Arrays.copyOfRange(challenge, 8, 16));

            System.arraycopy(block1, 0, encrypted, 0, 8);
            System.arraycopy(block2, 0, encrypted, 8, 8);

            return encrypted;

        } catch (Exception e) {
            e.printStackTrace();
            return new byte[16];
        }
    }

    private static byte[] reverseBits(byte[] data) {
        byte[] reversed = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            reversed[i] = (byte) (
                    ((data[i] & 0x01) << 7) |
                            ((data[i] & 0x02) << 5) |
                            ((data[i] & 0x04) << 3) |
                            ((data[i] & 0x08) << 1) |
                            ((data[i] & 0x10) >> 1) |
                            ((data[i] & 0x20) >> 3) |
                            ((data[i] & 0x40) >> 5) |
                            ((data[i] & 0x80) >> 7)
            );
        }
        return reversed;
    }

    private static void startVNCUpdate() {
        executor = Executors.newScheduledThreadPool(2);

        executor.scheduleAtFixedRate(() -> {
            if (connected && out != null) {
                try {
                    sendFrameBufferUpdateRequest();
                } catch (Exception e) {
                    if (connected) {
                        e.printStackTrace();
                        disconnectVNC();
                    }
                }
            }
        }, 0, 33, TimeUnit.MILLISECONDS);

        executor.execute(() -> {
            try {
                while (connected && !Thread.currentThread().isInterrupted()) {
                    if (in.available() > 0) {
                        readServerResponse();
                        frameCount.incrementAndGet();
                        updateFPS();
                    } else {
                        Thread.sleep(1);
                    }
                }
            } catch (Exception e) {
                if (connected) {
                    e.printStackTrace();
                    disconnectVNC();
                }
            }
        });
    }

    private static void sendFrameBufferUpdateRequest() throws IOException {
        out.writeByte(3);
        out.writeByte(1);
        out.writeShort(0);
        out.writeShort(0);
        out.writeShort(screenWidth);
        out.writeShort(screenHeight);
        out.flush();
    }

    private static void readServerResponse() throws IOException {
        int messageType = in.readByte() & 0xFF;

        switch (messageType) {
            case 0:
                readFramebufferUpdate();
                SwingUtilities.invokeLater(() -> {
                    updateVideoDisplay();
                    vncFrame.repaint();
                });
                break;
            case 1:
                skipColorMapEntries();
                break;
            case 2:
                break;
            case 3:
                skipServerCutText();
                break;
        }
    }

    private static void readFramebufferUpdate() throws IOException {
        in.readByte();
        int numberOfRectangles = in.readUnsignedShort();

        for (int i = 0; i < numberOfRectangles; i++) {
            int x = in.readUnsignedShort();
            int y = in.readUnsignedShort();
            int width = in.readUnsignedShort();
            int height = in.readUnsignedShort();
            int encodingType = in.readInt();

            if (encodingType == 0) {
                int bytesRead = readRawRectangle(x, y, width, height);
                totalBytesReceived += bytesRead;
            } else {
                skipUnknownEncoding(encodingType, width, height);
            }
        }
    }

    private static int readRawRectangle(int x, int y, int width, int height) throws IOException {
        int bytesPerPixel = 4;
        int dataSize = width * height * bytesPerPixel;
        byte[] pixelData = new byte[dataSize];
        in.readFully(pixelData);

        int[] pixels = new int[width * height];
        int idx = 0;

        for (int i = 0; i < pixels.length; i++) {
            int b = pixelData[idx++] & 0xFF;
            int g = pixelData[idx++] & 0xFF;
            int r = pixelData[idx++] & 0xFF;
            idx++;

            pixels[i] = (r << 16) | (g << 8) | b;
        }

        if (screenImage != null) {
            screenImage.setRGB(x, y, width, height, pixels, 0, width);
        }

        return dataSize;
    }

    private static void skipColorMapEntries() throws IOException {
        in.readByte();
        int firstColor = in.readUnsignedShort();
        int numberOfColors = in.readUnsignedShort();
        in.skipBytes(numberOfColors * 6);
    }

    private static void skipServerCutText() throws IOException {
        in.skipBytes(3);
        int length = in.readInt();
        if (length > 0) {
            in.skipBytes(length);
        }
    }

    private static void skipUnknownEncoding(int encodingType, int width, int height) throws IOException {
        switch (encodingType) {
            case 1:
                in.skipBytes(4);
                break;
            case 5:
                skipHextileData(width, height);
                break;
            default:
                in.skipBytes(width * height * 3);
        }
    }

    private static void skipHextileData(int width, int height) throws IOException {
        int tilesX = (width + 15) / 16;
        int tilesY = (height + 15) / 16;

        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                int subencoding = in.readByte() & 0xFF;

                if ((subencoding & 1) != 0) {
                    in.skipBytes(16 * 16 * 4);
                } else {
                    if ((subencoding & 2) != 0) {
                        in.skipBytes(4);
                    }
                    if ((subencoding & 4) != 0) {
                        in.skipBytes(4);
                    }
                    if ((subencoding & 8) != 0) {
                        int nSubrects = in.readByte() & 0xFF;
                        in.skipBytes(nSubrects * 2);
                    }
                }
            }
        }
    }

    private static void updateFPS() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFpsUpdate >= 1000) {
            int currentFps = frameCount.get();
            fps.set(currentFps);
            frameCount.set(0);
            lastFpsUpdate = currentTime;

            SwingUtilities.invokeLater(() -> {
                fpsLabel.setText("FPS: " + currentFps);
            });
        }
    }

    private static void updateVideoDisplay() {
        if (screenImage != null && videoLabel != null) {
            int labelWidth = videoLabel.getWidth();
            int labelHeight = videoLabel.getHeight();

            double scaleX = (double) labelWidth / screenImage.getWidth();
            double scaleY = (double) labelHeight / screenImage.getHeight();
            scale = Math.min(scaleX, scaleY);

            int scaledWidth = (int) (screenImage.getWidth() * scale);
            int scaledHeight = (int) (screenImage.getHeight() * scale);

            offsetX = (labelWidth - scaledWidth) / 2;
            offsetY = (labelHeight - scaledHeight) / 2;

            Image scaledImage = screenImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
            videoLabel.setIcon(new ImageIcon(scaledImage));

            // Сохраняем изображение для отрисовки красного квадрата
            currentDisplayImage = screenImage;
        }
    }

    private static void disconnectVNC() {
        connected = false;
        vncRunning = false;

        if (executor != null) {
            executor.shutdownNow();
        }

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            vncConnectBtn.setEnabled(true);
            vncDisconnectBtn.setEnabled(false);
            updateStatus("VNC отключен");
            showErrorImage("VNC отключен");
        });
    }

    private static void showErrorImage(String message) {
        screenImage = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = screenImage.createGraphics();

        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRect(0, 0, 800, 600);

        g2d.setColor(Color.RED);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("VNC НЕ АКТИВЕН", 300, 150);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.PLAIN, 16));
        g2d.drawString(message, 100, 200);
        g2d.drawString("Подключитесь к VNC серверу", 100, 230);
        g2d.drawString("в левой панели настроек", 100, 260);

        g2d.dispose();
        updateVideoDisplay();
    }

    // ================= ОБРАБОТКА СОБЫТИЙ VNC =================

    private static void handleVNCMousePress(MouseEvent e) {
        int buttonMask = 0;

        switch (e.getButton()) {
            case MouseEvent.BUTTON1:
                buttonMask = 1;
                break;
            case MouseEvent.BUTTON2:
                buttonMask = 2;
                break;
            case MouseEvent.BUTTON3:
                buttonMask = 4;
                break;
        }

        if (buttonMask != 0) {
            mouseButtons |= buttonMask;
            sendVNCMouseEvent();
        }
    }

    private static void handleVNCMouseRelease(MouseEvent e) {
        int buttonMask = 0;

        switch (e.getButton()) {
            case MouseEvent.BUTTON1:
                buttonMask = 1;
                break;
            case MouseEvent.BUTTON2:
                buttonMask = 2;
                break;
            case MouseEvent.BUTTON3:
                buttonMask = 4;
                break;
        }

        if (buttonMask != 0) {
            mouseButtons &= ~buttonMask;
            sendVNCMouseEvent();
        }
    }

    private static void handleVNCMouseMove(MouseEvent e) {
        lastMousePosition = e.getPoint();
        sendVNCMouseEvent();
    }

    private static void handleVNCMouseWheel(MouseWheelEvent e) {
        int scrollAmount = e.getWheelRotation();
        if (scrollAmount > 0) {
            mouseButtons |= 8;
            sendVNCMouseEvent();
            mouseButtons &= ~8;
            sendVNCMouseEvent();
        } else {
            mouseButtons |= 16;
            sendVNCMouseEvent();
            mouseButtons &= ~16;
            sendVNCMouseEvent();
        }
    }

    private static void sendVNCMouseEvent() {
        if (!connected || out == null || !mouseSyncEnabled || selectionMode) {
            return;
        }

        int serverX = (int) ((lastMousePosition.x - offsetX) / scale);
        int serverY = (int) ((lastMousePosition.y - offsetY) / scale);

        serverX = Math.max(0, Math.min(serverX, screenWidth - 1));
        serverY = Math.max(0, Math.min(serverY, screenHeight - 1));

        try {
            out.writeByte(5);
            out.writeByte(mouseButtons);
            out.writeShort(serverX);
            out.writeShort(serverY);
            out.flush();

        } catch (IOException e) {
            System.err.println("Ошибка отправки события мыши: " + e.getMessage());
        }
    }

    private static void sendVNCKeyEvent(int keyCode, boolean pressed) {
        if (!connected || out == null) {
            return;
        }

        try {
            out.writeByte(4);
            out.writeByte(pressed ? 1 : 0);
            out.writeShort(0);
            out.writeInt(convertVNCKeyCode(keyCode));
            out.flush();

        } catch (IOException e) {
            System.err.println("Ошибка отправки события клавиатуры: " + e.getMessage());
        }
    }



    // ================= МЕТОДЫ ВЫДЕЛЕНИЯ ОБЛАСТИ =================

    private static void updateSelectionRect() {
        if (selectionStart != null && selectionEnd != null) {
            int x1 = Math.min(selectionStart.x, selectionEnd.x);
            int y1 = Math.min(selectionStart.y, selectionEnd.y);
            int x2 = Math.max(selectionStart.x, selectionEnd.x);
            int y2 = Math.max(selectionStart.y, selectionEnd.y);
            selectionRect = new Rectangle(x1, y1, x2 - x1, y2 - y1);
        }
    }

    private static void copySelectionToClipboard() {
        if (selectionRect != null && screenImage != null) {
            try {
                int imgX = (int) ((selectionRect.x - offsetX) / scale);
                int imgY = (int) ((selectionRect.y - offsetY) / scale);
                int imgWidth = (int) (selectionRect.width / scale);
                int imgHeight = (int) (selectionRect.height / scale);

                imgX = Math.max(0, Math.min(imgX, screenWidth - 1));
                imgY = Math.max(0, Math.min(imgY, screenHeight - 1));
                imgWidth = Math.min(imgWidth, screenWidth - imgX);
                imgHeight = Math.min(imgHeight, screenHeight - imgY);

                if (imgWidth > 0 && imgHeight > 0) {
                    BufferedImage subImage = screenImage.getSubimage(imgX, imgY, imgWidth, imgHeight);

                    Transferable transferable = new Transferable() {
                        @Override
                        public DataFlavor[] getTransferDataFlavors() {
                            return new DataFlavor[]{DataFlavor.imageFlavor};
                        }

                        @Override
                        public boolean isDataFlavorSupported(DataFlavor flavor) {
                            return DataFlavor.imageFlavor.equals(flavor);
                        }

                        @Override
                        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                            if (isDataFlavorSupported(flavor)) {
                                return subImage;
                            }
                            throw new UnsupportedFlavorException(flavor);
                        }
                    };

                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);

                    updateStatus(String.format("Область %dx%d скопирована в буфер", imgWidth, imgHeight));
                }
            } catch (Exception e) {
                updateStatus("Ошибка при копировании: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void clearSelection() {
        selectionStart = null;
        selectionEnd = null;
        isSelecting = false;
        selectionRect = null;
        if (videoLabel != null) {
            videoLabel.repaint();
        }
    }

    private static void updateMousePosition(MouseEvent e) {
        if (connected && screenImage != null) {
            int serverX = (int) ((e.getX() - offsetX) / scale);
            int serverY = (int) ((e.getY() - offsetY) / scale);
            serverX = Math.max(0, Math.min(serverX, screenWidth - 1));
            serverY = Math.max(0, Math.min(serverY, screenHeight - 1));

            int finalServerX = serverX;
            int finalServerY = serverY;
            SwingUtilities.invokeLater(() -> {
                String syncStatus = mouseSyncEnabled ? "ВКЛ" : "ВЫКЛ";
                String selectionModeStatus = selectionMode ? " (Выделение)" : "";
                mousePositionLabel.setText(String.format("Мышь: [%d,%d] | Синхр: %s%s",
                        finalServerX, finalServerY, syncStatus, selectionModeStatus));
            });
        }
    }

    // ================= МЕТОДЫ ДЛЯ РАБОТЫ С COM-ПОРТОМ =================

    private static void toggleSerialConnection() {
        if (isSerialConnected) {
            disconnectSerial();
        } else {
            connectSerial();
        }
    }

    private static void connectSerial() {
        int selectedIndex = portComboBox.getSelectedIndex();
        if (selectedIndex <= 0) {
            JOptionPane.showMessageDialog(vncFrame, "Выберите COM-порт!");
            return;
        }

        String selectedPort = (String) portComboBox.getSelectedItem();
        String portName = selectedPort.split(" - ")[0];

        String baudRateStr = (String) baudRateComboBox.getSelectedItem();
        int baudRate = Integer.parseInt(baudRateStr);

        try {
            serialPort = SerialPort.getCommPort(portName);
            serialPort.setBaudRate(baudRate);
            serialPort.setNumDataBits(8);
            serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
            serialPort.setParity(SerialPort.NO_PARITY);

            if (serialPort.openPort()) {
                serialOutputStream = serialPort.getOutputStream();
                isSerialConnected = true;

                connectionStatusLabel.setText("COM: Подключено");
                connectionStatusLabel.setForeground(new Color(0, 150, 0));
                connectButton.setText("Отключить COM");
                messageTextPane.setEditable(true);
                updateTransmissionButtons(true, false);

                updateStatus("Подключено к " + portName + " @ " + baudRate + " бод");
            } else {
                JOptionPane.showMessageDialog(vncFrame, "Не удалось открыть порт!");
                connectionStatusLabel.setText("COM: Ошибка");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(vncFrame, "Ошибка подключения: " + e.getMessage());
            connectionStatusLabel.setText("COM: Ошибка");
        }
    }

    private static void disconnectSerial() {
        isSerialConnected = false;
        isStopped = true;

        if (transmissionThread != null && transmissionThread.isAlive()) {
            transmissionThread.interrupt();
        }

        try {
            if (serialOutputStream != null) {
                serialOutputStream.close();
            }
            if (serialPort != null) {
                serialPort.closePort();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        connectionStatusLabel.setText("COM: Не подключено");
        connectionStatusLabel.setForeground(Color.RED);
        connectButton.setText("Подключить COM");
        messageTextPane.setEditable(false);
        updateTransmissionButtons(false, false);
        lineNumberLabel.setText("Строка: 0");
        timeRemainingLabel.setText("Осталось: --:--");
        resetAllTextColor();

        serialPort = null;
        serialOutputStream = null;
    }

    // ================= МЕТОДЫ ПЕРЕДАЧИ ТЕКСТА =================

    private static void startTransmission() {
        if (!isSerialConnected) {
            JOptionPane.showMessageDialog(vncFrame, "Сначала подключитесь к COM-порту!");
            return;
        }

        String message = messageTextPane.getText();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(vncFrame, "Введите текст для отправки!");
            return;
        }

        isStopped = false;
        isPaused = false;
        isEnglish = true;
        updateLayoutStatus();

        currentTransmissionPosition = 0;
        totalCharactersToSend = message.replaceAll("[\\p{C}&&[^\n]]", "").length();
        transmissionStartTime = System.currentTimeMillis();
        lastUpdateTime = 0;

        resetAllTextColor();
        updateTransmissionButtons(true, true);

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
                    JOptionPane.showMessageDialog(vncFrame, "Ошибка передачи: " + e.getMessage());
                    disconnectSerial();
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    updateTransmissionButtons(true, false);
                    lineNumberLabel.setText("Строка: 0");
                    currentTransmissionPosition = 0;
                });
            }
        });

        transmissionThread.start();
    }

    private static void transmitMessage(String message) throws InterruptedException, IOException {
        message = message.replaceAll("[\\p{C}&&[^\n]]", "");
        message = message.replaceAll("(?m)^[ \\t]+$", "");
        String[] lines = message.split("\n");
        int finalLineNum = 1;
        int charsSent = 0;

        SwingUtilities.invokeLater(() -> lineNumberLabel.setText("Строка: 1 из " + lines.length));

        for (char c : message.toCharArray()) {
            if (isStopped) {
                break;
            }

            while (isPaused && !isStopped) {
                Thread.sleep(100);
            }

            if (isStopped) {
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

            highlightSentCharacter(currentTransmissionPosition);
            currentTransmissionPosition++;
            charsSent++;

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

        SwingUtilities.invokeLater(() -> {
            timeRemainingLabel.setText("Осталось: 00:00");
        });
    }

    private static void handleYoChar(char yoChar) throws IOException, InterruptedException {
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

    private static void sendRussianChar(char russianChar) throws IOException {
        String charStr = String.valueOf(russianChar);
        if (KEY_CODES_RUS.containsKey(charStr)) {
            int code = KEY_CODES_RUS.get(charStr);
            sendByte(code);
            System.out.println("Отправлен русский символ '" + russianChar + "' как код: " + code);
        } else {
            System.out.println("Русский символ '" + russianChar + "' не найден в KEY_CODES_RUS, отправляем как ASCII");
            sendByte(russianChar);
        }
    }

    private static void togglePause() {
        if (!isSerialConnected || transmissionThread == null || !transmissionThread.isAlive()) {
            return;
        }

        isPaused = !isPaused;
        pauseButton.setText(isPaused ? "Продолжить" : "Пауза");
    }

    private static void stopTransmission() {
        isStopped = true;
        updateTransmissionButtons(true, false);
    }

    private static void toggleKeyboardLayout() {
        try {
            if (!isSerialConnected) {
                return;
            }

            if (isEnglish) {
                sendByte(96);
            } else {
                sendByte(96);
            }
            isEnglish = !isEnglish;
            updateLayoutStatus();
            Thread.sleep(SWITCH_LAYOUT_DELAY);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(vncFrame,
                    "Ошибка переключения раскладки: " + ex.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void switchLayout() throws IOException, InterruptedException {
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

    private static void updateLayoutStatus() {
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

    private static void highlightSentCharacter(int position) {
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

    private static void resetAllTextColor() {
        SwingUtilities.invokeLater(() -> {
            try {
                StyledDocument doc = messageTextPane.getStyledDocument();
                Style style = messageTextPane.addStyle("DefaultStyle", null);
                StyleConstants.setForeground(style, Color.BLACK);
                StyleConstants.setBold(style, false);
                doc.setCharacterAttributes(0, doc.getLength(), style, false);
                currentTransmissionPosition = 0;
                messageTextPane.setCaretPosition(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void updateRemainingTime(int charsSent, int totalChars) {
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

        long minutes = remainingTime / 60000;
        long seconds = (remainingTime % 60000) / 1000;

        String timeStr = String.format("%02d:%02d", minutes, seconds);

        SwingUtilities.invokeLater(() -> {
            timeRemainingLabel.setText("Осталось: " + timeStr);
        });

        lastUpdateTime = currentTime;
    }

    private static void updateTransmissionButtons(boolean connected, boolean transmissionRunning) {
        connectButton.setEnabled(true);
        connectButton.setText(connected ? "Отключить COM" : "Подключить COM");

        startButton.setEnabled(connected && !transmissionRunning);
        pauseButton.setEnabled(connected && transmissionRunning);
        stopButton.setEnabled(connected && transmissionRunning);
        toggleLayoutButton.setEnabled(connected);

        ideCheckBox.setEnabled(connected && !transmissionRunning);

        if (!transmissionRunning) {
            pauseButton.setText("Пауза");
        }

        portComboBox.setEnabled(!connected);
        baudRateComboBox.setEnabled(!connected);
        messageTextPane.setEditable(connected && !transmissionRunning);
    }

    private static boolean isLayoutSwitchChar(char c) {
        return c == '~' || c == 'Ё' || c == 'ё' || c == '`';
    }

    private static boolean isSpecialCharacterForIDE(char c) {
        return c == '{' ||
                c == '\"' ||
                c == '\'' ||
                c == '[' ||
                c == '(';
    }

    // Вспомогательные методы
    private static boolean isCyrillic(char c) {
        return (c >= 'А' && c <= 'Я') || (c >= 'а' && c <= 'я') || c == 'Ё' || c == 'ё';
    }

    private static void sendByte(int value) throws IOException {
        if (value < 0 || value > 255) {
            System.err.println("Value must be in range 0-255: " + value);
            return;
        }
        byte b = (byte) (value & 0xFF);
        if (serialOutputStream != null) {
            serialOutputStream.write(new byte[]{b});
            serialOutputStream.flush();
            System.out.println("Отправлен байт: " + value);
        }

        try {
            Thread.sleep(BASE_DELAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Transmission interrupted", e);
        }
    }

    private static void updateStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
        System.out.println("VNC: " + message);
    }
}