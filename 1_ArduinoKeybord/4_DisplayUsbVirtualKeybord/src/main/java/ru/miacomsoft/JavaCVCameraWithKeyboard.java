package ru.miacomsoft;

import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.opencv_core.Mat;
import com.fazecast.jSerialComm.*;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import static org.bytedeco.opencv.global.opencv_core.CV_8U;
import static ru.miacomsoft.KeyCodes.KEY_CODES_RUS;
import static ru.miacomsoft.KeyCodes.isCyrillic;

public class JavaCVCameraWithKeyboard {
    private static FrameGrabber grabber;
    private static JFrame cameraFrame;
    private static boolean isRunning = false;
    private static float contrast = 1.0f;
    private static float brightness = 0.0f;
    private static final float CONTRAST_STEP = 0.1f;
    private static final float BRIGHTNESS_STEP = 5.0f;

    // Для выделения области
    private static Point selectionStart = null;
    private static Point selectionEnd = null;
    private static boolean isSelecting = false;
    private static BufferedImage currentFrame = null;
    private static Dimension originalImageSize = null;

    // Панель управления камерой
    private static JLabel contrastLabel;
    private static JLabel brightnessLabel;
    private static JLabel statusLabel;

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

    // Галочка "IDE" (добавлено)
    private static JCheckBox ideCheckBox;

    // Split pane для разделения видео и текста
    private static JSplitPane splitPane;
    private static CanvasFrame canvas;

    // Для передачи данных
    private static Thread transmissionThread;
    private static boolean isPaused = false;
    private static boolean isStopped = false;
    private static boolean isConnected = false;
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

    // Коды модификаторов для клавиатуры
    private static final int KEY_LEFT_CTRL = 0x80;   // 128
    private static final int KEY_LEFT_SHIFT = 0x81;  // 129
    private static final int KEY_LEFT_ALT = 0x82;    // 130
    private static final int KEY_LEFT_GUI = 0x83;    // 131
    private static final int KEY_RIGHT_CTRL = 0x84;  // 132
    private static final int KEY_RIGHT_SHIFT = 0x85; // 133
    private static final int KEY_RIGHT_ALT = 0x86;   // 134
    private static final int KEY_RIGHT_GUI = 0x87;   // 135
    private static final int KEY_RELEASE_ALL = 0x00; // 0

    // Компонент для отображения видео
    private static JLabel videoLabel;

    // Флаг для отслеживания, находится ли видео в фокусе
    private static boolean videoHasFocus = false;

    // Компоненты для управления камерой (вынесенные в панель управления передачей)
    private static JComboBox<String> resCombo;
    private static JButton cameraStartBtn;
    private static JButton cameraStopBtn;
    private static JButton screenshotBtn;

    // Главное окно управления
    private static JFrame controlFrame;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            createAndShowGUI();
            startCameraOnProgramStart(); // Запускаем камеру сразу при старте программы
        });
    }

    private static void createAndShowGUI() {
        // Создаем главное окно управления (упрощенное)
        controlFrame = new JFrame("Управление камерой");
        controlFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        controlFrame.setLayout(new BorderLayout());

        // Панель информации и состояния (упрощенная)
        JPanel infoPanel = new JPanel(new GridLayout(1, 3, 5, 5));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        contrastLabel = new JLabel("Контраст: 1.0");
        brightnessLabel = new JLabel("Яркость: 0.0");
        statusLabel = new JLabel("Готов к работе");

        infoPanel.add(contrastLabel);
        infoPanel.add(brightnessLabel);
        infoPanel.add(statusLabel);

        // Панель управления камерой (упрощенная)
        JPanel cameraPanel = new JPanel(new FlowLayout());

        JButton helpBtn = new JButton("Справка (F1)");
        helpBtn.addActionListener(e -> showHelpDialog());

        cameraPanel.add(helpBtn);

        // Собираем главное окно
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(infoPanel, BorderLayout.NORTH);
        mainPanel.add(cameraPanel, BorderLayout.CENTER);

        controlFrame.add(mainPanel, BorderLayout.CENTER);
        controlFrame.setSize(400, 150);
        controlFrame.setLocationRelativeTo(null);
        controlFrame.setVisible(true);

        // Добавляем горячую клавишу F1 для вызова справки
        setupGlobalKeyBindings(controlFrame);
    }

    private static void startCameraOnProgramStart() {
        // Автоматически запускаем камеру с разрешением 1280x720
        SwingUtilities.invokeLater(() -> {
            startCamera(0, 1280, 720);
        });
    }

    private static void setupGlobalKeyBindings(JFrame frame) {
        InputMap inputMap = frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = frame.getRootPane().getActionMap();

        // Горячие клавиши для управления камерой
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), "increaseContrast");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.SHIFT_DOWN_MASK), "increaseContrast");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "increaseContrast");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "decreaseContrast");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), "decreaseContrast");

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, KeyEvent.CTRL_DOWN_MASK), "increaseBrightness");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "increaseBrightness");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, KeyEvent.CTRL_DOWN_MASK), "increaseBrightness");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.CTRL_DOWN_MASK), "decreaseBrightness");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, KeyEvent.CTRL_DOWN_MASK), "decreaseBrightness");

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0), "resetContrast");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, 0), "resetBrightness");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "resetAll");

        // Горячая клавиша F1 для вызова справки
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "showHelp");

        actionMap.put("increaseContrast", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                contrast = Math.min(3.0f, contrast + CONTRAST_STEP);
                contrastLabel.setText("Контраст: " + String.format("%.1f", contrast));
                updateStatus("Контрастность увеличена до " + String.format("%.1f", contrast));
            }
        });

        actionMap.put("decreaseContrast", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                contrast = Math.max(0.1f, contrast - CONTRAST_STEP);
                contrastLabel.setText("Контраст: " + String.format("%.1f", contrast));
                updateStatus("Контрастность уменьшена до " + String.format("%.1f", contrast));
            }
        });

        actionMap.put("increaseBrightness", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                brightness = Math.min(100.0f, brightness + BRIGHTNESS_STEP);
                brightnessLabel.setText("Яркость: " + String.format("%.1f", brightness));
                updateStatus("Яркость увеличена до " + String.format("%.1f", brightness));
            }
        });

        actionMap.put("decreaseBrightness", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                brightness = Math.max(-100.0f, brightness - BRIGHTNESS_STEP);
                brightnessLabel.setText("Яркость: " + String.format("%.1f", brightness));
                updateStatus("Яркость уменьшена до " + String.format("%.1f", brightness));
            }
        });

        actionMap.put("resetContrast", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                contrast = 1.0f;
                contrastLabel.setText("Контраст: 1.0");
                updateStatus("Контрастность сброшена");
            }
        });

        actionMap.put("resetBrightness", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                brightness = 0.0f;
                brightnessLabel.setText("Яркость: 0.0");
                updateStatus("Яркость сброшена");
            }
        });

        actionMap.put("resetAll", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                contrast = 1.0f;
                brightness = 0.0f;
                contrastLabel.setText("Контраст: 1.0");
                brightnessLabel.setText("Яркость: 0.0");
                updateStatus("Все настройки сброшены");
            }
        });

        actionMap.put("showHelp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showHelpDialog();
            }
        });
    }

    private static void showHelpDialog() {
        JDialog helpDialog = new JDialog(controlFrame, "Справка по управлению", true);
        helpDialog.setLayout(new BorderLayout());

        JTextArea instructions = new JTextArea();
        instructions.setText("Управление камерой:\n" +
                "  + / - : Изменить контрастность\n" +
                "  Ctrl + +/- : Изменить яркость\n" +
                "  C / B : Сброс контрастности/яркости\n" +
                "  R : Сброс всех настроек\n\n" +
                "Выделение области:\n" +
                "  Зажмите ЛКМ и выделите область\n" +
                "  Отпустите ЛКМ - область скопируется в буфер\n" +
                "  ESC : Отмена выделения\n\n" +
                "Прямая передача клавиш:\n" +
                "  Кликните на видео для активации режима\n" +
                "  Нажимайте клавиши - они будут отправляться\n" +
                "  ESC : Выход из режима прямой передачи");
        instructions.setEditable(false);
        instructions.setFont(new Font("Monospaced", Font.PLAIN, 12));
        instructions.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(instructions);
        scrollPane.setPreferredSize(new Dimension(500, 300));

        JButton closeButton = new JButton("Закрыть");
        closeButton.addActionListener(e -> helpDialog.dispose());

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(closeButton);

        helpDialog.add(scrollPane, BorderLayout.CENTER);
        helpDialog.add(buttonPanel, BorderLayout.SOUTH);

        helpDialog.pack();
        helpDialog.setLocationRelativeTo(controlFrame);
        helpDialog.setVisible(true);
    }

    private static void startCamera(int index, int width, int height) {
        if (isRunning) {
            stopCamera();
        }

        new Thread(() -> {
            try {
                // Создаем одно окно камеры
                cameraFrame = new JFrame("Камера с виртуальной клавиатурой " + width + "x" + height);
                cameraFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                cameraFrame.setLayout(new BorderLayout());

                // Создаем панель управления передачей (верхняя панель)
                JPanel controlPanel = createControlPanel(width, height);

                // СОЗДАЕМ ПУСТУЮ ЛЕВУЮ ПАНЕЛЬ
                JPanel leftPanel = createLeftPanel();

                // Создаем панель для видео камеры (правая панель)
                JPanel cameraVideoPanel = new JPanel(new BorderLayout());
                cameraVideoPanel.setBorder(BorderFactory.createTitledBorder("Трансляция камеры"));
                cameraVideoPanel.setMinimumSize(new Dimension(400, 200));

                // Вместо CanvasFrame создаем простой компонент для отображения видео
                videoLabel = new JLabel();
                videoLabel.setHorizontalAlignment(SwingConstants.CENTER);
                videoLabel.setPreferredSize(new Dimension(width, height));

                // Добавляем слушатели мыши для выделения области
                addMouseListenersToVideoLabel(videoLabel);

                // Добавляем обработчик фокуса для видео
                videoLabel.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        videoHasFocus = true;
                        videoLabel.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                        updateStatus("Видео в фокусе - режим прямой передачи клавиш");
                    }

                    @Override
                    public void focusLost(FocusEvent e) {
                        videoHasFocus = false;
                        videoLabel.setBorder(null);
                        updateStatus("Режим прямой передачи клавиш отключен");
                    }
                });

                // Добавляем слушатель кликов для активации фокуса
                videoLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        videoLabel.requestFocusInWindow();
                    }
                });

                // Добавляем обработчик клавиш для отмены выделения и прямой передачи
                videoLabel.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        // =========== МЕСТО ОТПРАВКИ КЛАВИШ ===========
                        // Отправка нажатой клавиши, если активно подключение и видео в фокусе
                        if (videoHasFocus && isConnected && serialOutputStream != null) {
                            handleDirectKeyPress(e);
                        }
                        // ==============================================
                    }

                    @Override
                    public void keyTyped(KeyEvent e) {
                        // Игнорируем keyTyped, используем keyPressed для получения кодов клавиш
                    }
                });

                // Устанавливаем фокус на videoLabel, чтобы работали горячие клавиши
                videoLabel.setFocusable(true);

                cameraVideoPanel.add(videoLabel, BorderLayout.CENTER);

                // СОЗДАЕМ ГОРИЗОНТАЛЬНЫЙ РАЗДЕЛИТЕЛЬ МЕЖДУ ЛЕВОЙ И ПРАВОЙ ПАНЕЛЯМИ
                JSplitPane horizontalSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
                horizontalSplitPane.setLeftComponent(leftPanel);
                horizontalSplitPane.setRightComponent(cameraVideoPanel);
                horizontalSplitPane.setDividerLocation(200); // Начальная ширина левой панели
                horizontalSplitPane.setResizeWeight(0.2); // 20% для левой, 80% для правой
                horizontalSplitPane.setOneTouchExpandable(true); // Добавляем кнопки для быстрого скрытия/показа

                // Создаем вертикальный разделитель между панелью управления и видео
                splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

                // Добавляем панель управления в верхнюю часть вертикального разделителя
                splitPane.setTopComponent(controlPanel);

                // Добавляем горизонтальный разделитель (левая+правая панели) в нижнюю часть вертикального разделителя
                splitPane.setBottomComponent(horizontalSplitPane);

                // Настраиваем вертикальный разделитель
                splitPane.setDividerLocation(200); // Начальная высота панели управления
                splitPane.setResizeWeight(0.3); // Обе панели могут изменять размер (30% для верхней, 70% для нижней)
                splitPane.setOneTouchExpandable(true);

                // Добавляем разделитель в окно
                cameraFrame.add(splitPane, BorderLayout.CENTER);

                // Настраиваем окно - увеличиваем ширину для левой панели
                cameraFrame.pack();
                cameraFrame.setSize(width + 220, height + 300); // +220 пикселей для левой панели
                cameraFrame.setLocationRelativeTo(null);
                cameraFrame.setVisible(true);

                // Создаем grabber
                grabber = FrameGrabber.createDefault(index);
                grabber.setImageWidth(width);
                grabber.setImageHeight(height);
                grabber.setFrameRate(30);

                grabber.start();
                isRunning = true;

                updateStatus("Камера запущена " + width + "x" + height);

                OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
                Java2DFrameConverter converterToBufferedImage = new Java2DFrameConverter();

                long frameCount = 0;
                long startTime = System.currentTimeMillis();

                // Основной цикл захвата
                while (isRunning && cameraFrame.isVisible()) {
                    try {
                        Frame grabbedFrame = grabber.grab();

                        if (grabbedFrame != null) {
                            frameCount++;

                            // Обновляем FPS в заголовке
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - startTime >= 1000) {
                                double fps = frameCount * 1000.0 / (currentTime - startTime);
                                cameraFrame.setTitle(String.format("Камера %dx%d @ %.1f FPS", width, height, fps));
                                frameCount = 0;
                                startTime = currentTime;
                            }

                            // Преобразуем кадр в Mat для обработки
                            Mat mat = converterToMat.convert(grabbedFrame);

                            // Применяем коррекцию контрастности и яркости
                            Mat processedMat = adjustContrastAndBrightness(mat, contrast, brightness);

                            // Преобразуем обратно в Frame
                            Frame processedFrame = converterToMat.convert(processedMat);

                            // Сохраняем текущий кадр для выделения области
                            currentFrame = converterToBufferedImage.getBufferedImage(processedFrame);
                            if (originalImageSize == null) {
                                originalImageSize = new Dimension(currentFrame.getWidth(), currentFrame.getHeight());
                            }

                            // Преобразуем Frame в ImageIcon для отображения в JLabel
                            BufferedImage imageToShow;
                            // Если идет выделение области, рисуем прямоугольник
                            if (isSelecting && selectionStart != null && selectionEnd != null) {
                                imageToShow = drawSelectionRectangleOnImage(currentFrame, videoLabel);
                            } else {
                                imageToShow = currentFrame;
                            }

                            // Масштабируем изображение под размер компонента
                            if (imageToShow != null) {
                                Image scaledImage = imageToShow.getScaledInstance(
                                        videoLabel.getWidth(),
                                        videoLabel.getHeight(),
                                        Image.SCALE_SMOOTH
                                );
                                videoLabel.setIcon(new ImageIcon(scaledImage));
                            }

                            // Освобождаем ресурсы
                            processedMat.release();
                        }

                        Thread.sleep(1);

                    } catch (Exception e) {
                        if (isRunning) {
                            System.err.println("Ошибка обработки кадра: " + e.getMessage());
                        }
                        break;
                    }
                }

                stopCamera();

            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "Ошибка запуска камеры: " + e.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                stopCamera();
            }
        }).start();
    }



    private static void handleDirectKeyPress(KeyEvent e) {
        try {
            int keyCode = e.getKeyCode();
            int modifiers = e.getModifiersEx();

            // Обработка сочетаний клавиш с Ctrl, Shift, Alt
            boolean ctrlPressed = (modifiers & KeyEvent.CTRL_DOWN_MASK) != 0;
            boolean shiftPressed = (modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0;
            boolean altPressed = (modifiers & KeyEvent.ALT_DOWN_MASK) != 0;

            // Сначала отправляем модификаторы
            if (ctrlPressed) {
                sendSpecialKey(KEY_LEFT_CTRL);
            }
            if (shiftPressed) {
                sendSpecialKey(KEY_LEFT_SHIFT);
            }
            if (altPressed) {
                sendSpecialKey(KEY_LEFT_ALT);
            }

            int keyCodeDst = -1;
            char keyChar = e.getKeyChar();

            // ============ ИСПРАВЛЕННАЯ ЛОГИКА: ТОЛЬКО ИНВЕРСИЯ БУКВ ============
            if (keyChar != KeyEvent.CHAR_UNDEFINED) {
                char invertedChar = keyChar;

                // Инверсия ТОЛЬКО для английских букв A-Z, a-z
                if ((keyChar >= 'A' && keyChar <= 'Z') || (keyChar >= 'a' && keyChar <= 'z')) {
                    if (keyChar >= 'A' && keyChar <= 'Z') {
                        // Заглавную → строчную
                        invertedChar = (char)(keyChar + 32);
                    } else if (keyChar >= 'a' && keyChar <= 'z') {
                        // Строчную → заглавную
                        invertedChar = (char)(keyChar - 32);
                    }
                    keyCodeDst = invertedChar;
                } else {
                    // Для всех остальных символов - отправляем как есть
                    keyCodeDst = keyChar;
                }
            } else {
                // Для клавиш без символов (стрелки, F-клавиши и т.д.)
                switch (keyCode) {
                    case 38: // "стрелка верх"
                        keyCodeDst = 218;
                        break;
                    case 40: // "стрелка низ"
                        keyCodeDst = 217;
                        break;
                    case 37: // "стрелка лево"
                        keyCodeDst = 216;
                        break;
                    case 39: // "стрелка право"
                        keyCodeDst = 215;
                        break;
                    case 127: // "del"
                        keyCodeDst = 212;
                        break;
                    case 36: // "hom"
                        keyCodeDst = 210;
                        break;
                    case 35: // "end"
                        keyCodeDst = 213;
                        break;
                    case 33: // "PageUp"
                        keyCodeDst = 211;
                        break;
                    case 34: // "PageDown"
                        keyCodeDst = 214;
                        break;
                    case 155: // "inser"
                        keyCodeDst = 234;
                        break;
                    case 112: // "F1"
                        keyCodeDst = 194;
                        break;
                    case 113: // "F2"
                        keyCodeDst = 195;
                        break;
                    case 114: // "F3"
                        keyCodeDst = 196;
                        break;
                    case 115: // "F4"
                        keyCodeDst = 197;
                        break;
                    case 116: // "F5"
                        keyCodeDst = 198;
                        break;
                    case 117: // "F6"
                        keyCodeDst = 199;
                        break;
                    case 118: // "F7"
                        keyCodeDst = 200;
                        break;
                    case 119: // "F8"
                        keyCodeDst = 201;
                        break;
                    case 120: // "F9"
                        keyCodeDst = 202;
                        break;
                    case 121: // "F10"
                        keyCodeDst = 203;
                        break;
                    case 122: // "F11"
                        keyCodeDst = 204;
                        break;
                    case 123: // "F12"
                        keyCodeDst = 205;
                        break;
                    case 27: // "ESC"
                        keyCodeDst = 177;
                        break;
                    // Игнорируем клавиши-модификаторы
                    case KeyEvent.VK_CONTROL:
                    case KeyEvent.VK_ALT:
                    case KeyEvent.VK_ALT_GRAPH:
                    case KeyEvent.VK_META:
                    case KeyEvent.VK_SHIFT:
                        keyCodeDst = -1;
                        break;
                    default:
                        // Для остальных клавиш используем convertKeyCode
                        keyCodeDst = convertKeyCode(keyCode);
                        break;
                }
            }

            if (keyCodeDst != -1) {
                sendByte(keyCodeDst);
                updateStatus("Отправлен код: " + keyCodeDst + " (" +
                        (keyChar != KeyEvent.CHAR_UNDEFINED ?
                                "символ '" + (char)keyCodeDst + "'" :
                                "клавиша " + KeyEvent.getKeyText(keyCode)) + ")");
            }

            // ============ ВАЖНО: Отпустить модификаторы в обратном порядке ============
            // Короткая задержка для имитации удержания клавиши
            Thread.sleep(50);

            // Отпустить только те модификаторы, которые были нажаты
            if (altPressed) {
                sendSpecialKey(KEY_LEFT_ALT);
                Thread.sleep(10);
            }
            if (shiftPressed) {
                sendSpecialKey(KEY_LEFT_SHIFT);
                Thread.sleep(10);
            }
            if (ctrlPressed) {
                sendSpecialKey(KEY_LEFT_CTRL);
                Thread.sleep(10);
            }

            // Гарантированное полное отпускание
            sendSpecialKey(KEY_RELEASE_ALL);

        } catch (Exception ex) {
            ex.printStackTrace();
            updateStatus("Ошибка отправки клавиши: " + ex.getMessage());
        }
    }

    /**
     * Конвертация Java KeyCode в код для отправки через Serial
     * Этот метод используется как fallback, когда не сработал основной switch
     */
    private static int convertKeyCode(int javaKeyCode) {
        switch (javaKeyCode) {
            case 38: // "стрелка верх"
                return 218;
            case 40: // "стрелка низ"
                return 217;
            case 37: // "стрелка лево"
                return 216;
            case 39: //"стрелка право"
                return 215;
            case 127: //"del"
                return 212;
            case 36: //"hom"
                return 210;
            case 35: //"end"
                return 213;
            case 33: //"PageUp"
                return 211;
            case 34: //"PageDown"
                return 214;
            case 155: //"inser"
                return 234;

            // F-клавиши
            case 112: //"F1"
                return 194;
            case 113: //"F2"
                return 195;
            case 114: //"F3"
                return 196;
            case 115: //"F4"
                return 197;
            case 116: //"F5"
                return 198;
            case 117: //"F6"
                return 199;
            case 118: //"F7"
                return 200;
            case 119: //"F8"
                return 201;
            case 120: //"F9"
                return 202;
            case 121: //"F10"
                return 203;
            case 122: //"F11"
                return 204;
            case 123: //"F12"
                return 205;

            // Игнорируем клавиши-модификаторы
            case KeyEvent.VK_CONTROL:
            case KeyEvent.VK_ALT:
            case KeyEvent.VK_ALT_GRAPH:
            case KeyEvent.VK_META:
            case KeyEvent.VK_SHIFT:
                return -1; // Не отправляем отдельно

            // ============ ИЗМЕНЕНИЕ: Возвращаем базовые коды без учета Shift ============
            // Теперь основная логика обработки Shift в handleDirectKeyPress
            case KeyEvent.VK_A: return 'a';
            case KeyEvent.VK_B: return 'b';
            case KeyEvent.VK_C: return 'c';
            case KeyEvent.VK_D: return 'd';
            case KeyEvent.VK_E: return 'e';
            case KeyEvent.VK_F: return 'f';
            case KeyEvent.VK_G: return 'g';
            case KeyEvent.VK_H: return 'h';
            case KeyEvent.VK_I: return 'i';
            case KeyEvent.VK_J: return 'j';
            case KeyEvent.VK_K: return 'k';
            case KeyEvent.VK_L: return 'l';
            case KeyEvent.VK_M: return 'm';
            case KeyEvent.VK_N: return 'n';
            case KeyEvent.VK_O: return 'o';
            case KeyEvent.VK_P: return 'p';
            case KeyEvent.VK_Q: return 'q';
            case KeyEvent.VK_R: return 'r';
            case KeyEvent.VK_S: return 's';
            case KeyEvent.VK_T: return 't';
            case KeyEvent.VK_U: return 'u';
            case KeyEvent.VK_V: return 'v';
            case KeyEvent.VK_W: return 'w';
            case KeyEvent.VK_X: return 'x';
            case KeyEvent.VK_Y: return 'y';
            case KeyEvent.VK_Z: return 'z';

            case KeyEvent.VK_0: return '0';
            case KeyEvent.VK_1: return '1';
            case KeyEvent.VK_2: return '2';
            case KeyEvent.VK_3: return '3';
            case KeyEvent.VK_4: return '4';
            case KeyEvent.VK_5: return '5';
            case KeyEvent.VK_6: return '6';
            case KeyEvent.VK_7: return '7';
            case KeyEvent.VK_8: return '8';
            case KeyEvent.VK_9: return '9';

            // Основные символы (без учета Shift)
            case KeyEvent.VK_SPACE: return ' ';
            case KeyEvent.VK_ENTER: return '\n';
            case KeyEvent.VK_BACK_SPACE: return '\b';
            case KeyEvent.VK_TAB: return '\t';
            case KeyEvent.VK_COMMA: return ',';
            case KeyEvent.VK_PERIOD: return '.';
            case KeyEvent.VK_SLASH: return '/';
            case KeyEvent.VK_SEMICOLON: return ';';
            case KeyEvent.VK_EQUALS: return '=';
            case KeyEvent.VK_MINUS: return '-';
            case KeyEvent.VK_BACK_QUOTE: return '`';
            case KeyEvent.VK_OPEN_BRACKET: return '[';
            case KeyEvent.VK_CLOSE_BRACKET: return ']';
            case KeyEvent.VK_BACK_SLASH: return '\\';
            case KeyEvent.VK_QUOTE: return '\'';
            // Основные символы (без учета Shift)
            case KeyEvent.VK_LEFT_PARENTHESIS: return '(';  // Добавлено
            case KeyEvent.VK_RIGHT_PARENTHESIS: return ')'; // Добавлено
            case KeyEvent.VK_BRACELEFT: return '{';        // Добавлено
            case KeyEvent.VK_BRACERIGHT: return '}';       // Добавлено

            default:
                return (javaKeyCode >= 1 && javaKeyCode <= 127) ? javaKeyCode : -1;
        }
    }

    /**
     * Метод для отправки специальных кодов (модификаторов)
     */
    private static void sendSpecialKey(int keyCode) throws IOException {
        if (serialOutputStream != null) {
            serialOutputStream.write(keyCode);
            serialOutputStream.flush();
            System.out.println("Отправлен специальный код: " + keyCode);

            // Небольшая задержка для стабильности
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Метод для отправки байта через Serial
     */
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

    // =========================================================

    // Новый метод для добавления слушателей мыши к JLabel
    private static void addMouseListenersToVideoLabel(JLabel videoLabel) {
        videoLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && currentFrame != null) {
                    // Получаем координаты на оригинальном изображении
                    Point originalPoint = convertToOriginalCoordinates(e.getPoint(), videoLabel);
                    selectionStart = originalPoint;
                    isSelecting = true;
                    updateStatus("Начало выделения области");
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && isSelecting && currentFrame != null) {
                    // Получаем координаты на оригинальном изображении
                    Point originalPoint = convertToOriginalCoordinates(e.getPoint(), videoLabel);
                    selectionEnd = originalPoint;
                    isSelecting = false;

                    // Копируем выделенную область в буфер обмена
                    copySelectionToClipboard(videoLabel);

                    // Очищаем выделение через 500 мс
                    Timer clearTimer = new Timer(500, evt -> clearSelection());
                    clearTimer.setRepeats(false);
                    clearTimer.start();
                }
            }
        });

        videoLabel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isSelecting && currentFrame != null) {
                    // Получаем координаты на оригинальном изображении
                    Point originalPoint = convertToOriginalCoordinates(e.getPoint(), videoLabel);
                    selectionEnd = originalPoint;
                }
            }
        });
    }

    // Метод для преобразования координат с масштабированного изображения на оригинальное
    private static Point convertToOriginalCoordinates(Point scaledPoint, JLabel videoLabel) {
        if (currentFrame == null || originalImageSize == null) {
            return scaledPoint;
        }

        // Получаем размеры компонента и иконки
        ImageIcon icon = (ImageIcon) videoLabel.getIcon();
        if (icon == null) {
            return scaledPoint;
        }

        int componentWidth = videoLabel.getWidth();
        int componentHeight = videoLabel.getHeight();
        int imageWidth = icon.getIconWidth();
        int imageHeight = icon.getIconHeight();

        // Вычисляем смещение изображения внутри компонента (центрирование)
        int offsetX = (componentWidth - imageWidth) / 2;
        int offsetY = (componentHeight - imageHeight) / 2;

        // Корректируем координаты мыши с учетом смещения
        int adjustedX = scaledPoint.x - offsetX;
        int adjustedY = scaledPoint.y - offsetY;

        // Ограничиваем координаты границами изображения
        adjustedX = Math.max(0, Math.min(adjustedX, imageWidth - 1));
        adjustedY = Math.max(0, Math.min(adjustedY, imageHeight - 1));

        // Вычисляем масштаб
        double scaleX = (double) originalImageSize.width / imageWidth;
        double scaleY = (double) originalImageSize.height / imageHeight;

        // Преобразуем координаты на оригинальное изображение
        int originalX = (int) (adjustedX * scaleX);
        int originalY = (int) (adjustedY * scaleY);

        return new Point(originalX, originalY);
    }

    // ДОБАВЛЯЕМ ПРОСТОЙ МЕТОД ДЛЯ СОЗДАНИЯ ПУСТОЙ ЛЕВОЙ ПАНЕЛИ
    private static JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Левая панель"));
        leftPanel.setMinimumSize(new Dimension(150, 200));
        leftPanel.setPreferredSize(new Dimension(200, 600));

        // Добавляем простую метку
        JLabel infoLabel = new JLabel("Панель можно изменить");
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        leftPanel.add(infoLabel, BorderLayout.CENTER);

        return leftPanel;
    }

    private static JPanel createControlPanel(int currentWidth, int currentHeight) {
        // Создаем главную панель управления
        JPanel controlPanel = new JPanel(new BorderLayout());
        controlPanel.setBorder(BorderFactory.createTitledBorder("Управление передачей"));
        controlPanel.setMinimumSize(new Dimension(400, 150)); // Минимальная высота панели управления

        // Панель со всеми контролами в одной линии
        JPanel controlsLinePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        controlsLinePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Создаем компоненты управления COM-портом
        JLabel portLabel = new JLabel("Порт:");

        // Получаем список доступных COM-портов
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

        connectButton = new JButton("Подключить");
        connectionStatusLabel = new JLabel("Не подключено");
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

        // Добавляем компоненты управления камерой (вынесенные)
        JLabel resLabel = new JLabel("Разрешение:");
        String[] resolutions = {"640x480", "800x600", "1024x768", "1280x720", "1600x900", "1920x1080"};
        resCombo = new JComboBox<>(resolutions);
        // Устанавливаем текущее разрешение
        String currentRes = currentWidth + "x" + currentHeight;
        boolean found = false;
        for (int i = 0; i < resolutions.length; i++) {
            if (resolutions[i].equals(currentRes)) {
                resCombo.setSelectedIndex(i);
                found = true;
                break;
            }
        }
        if (!found) {
            resCombo.setSelectedIndex(3); // 1280x720 по умолчанию
        }

        cameraStartBtn = new JButton("Старт");
        cameraStopBtn = new JButton("Стоп");
        screenshotBtn = new JButton("Снимок");

        // Добавляем слушатели для управления камерой
        cameraStartBtn.addActionListener(e -> {
            String selectedRes = (String) resCombo.getSelectedItem();
            String[] dims = selectedRes.split("x");
            int width = Integer.parseInt(dims[0]);
            int height = Integer.parseInt(dims[1]);
            startCamera(0, width, height);
        });

        cameraStopBtn.addActionListener(e -> stopCamera());
        screenshotBtn.addActionListener(e -> takeScreenshot());

        // Добавляем слушатели для управления передачей
        connectButton.addActionListener(e -> toggleConnection());
        startButton.addActionListener(e -> startTransmission());
        pauseButton.addActionListener(e -> togglePause());
        stopButton.addActionListener(e -> stopTransmission());
        toggleLayoutButton.addActionListener(e -> toggleKeyboardLayout());

        // Добавляем все компоненты в одну линию
        controlsLinePanel.add(portLabel);
        controlsLinePanel.add(portComboBox);
        controlsLinePanel.add(baudLabel);
        controlsLinePanel.add(baudRateComboBox);
        controlsLinePanel.add(connectButton);
        controlsLinePanel.add(connectionStatusLabel);
        controlsLinePanel.add(ideCheckBox);
        controlsLinePanel.add(startButton);
        controlsLinePanel.add(pauseButton);
        controlsLinePanel.add(stopButton);
        controlsLinePanel.add(toggleLayoutButton);
        controlsLinePanel.add(lineNumberLabel);
        controlsLinePanel.add(timeRemainingLabel);
        controlsLinePanel.add(layoutStatusLabel);

        // Добавляем разделитель и компоненты управления камерой
        controlsLinePanel.add(Box.createHorizontalStrut(20)); // Разделитель
        controlsLinePanel.add(resLabel);
        controlsLinePanel.add(resCombo);
        controlsLinePanel.add(cameraStartBtn);
        controlsLinePanel.add(cameraStopBtn);
        controlsLinePanel.add(screenshotBtn);

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

    private static void addMouseListenersToCanvas(CanvasFrame canvas) {
        if (canvas == null) return;

        Component canvasComponent = canvas.getCanvas();

        canvasComponent.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    selectionStart = e.getPoint();
                    isSelecting = true;
                    updateStatus("Начало выделения области");
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && isSelecting) {
                    selectionEnd = e.getPoint();
                    isSelecting = false;

                    // Очищаем выделение через 500 мс
                    Timer clearTimer = new Timer(500, evt -> clearSelection());
                    clearTimer.setRepeats(false);
                    clearTimer.start();
                }
            }
        });

        canvasComponent.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isSelecting) {
                    selectionEnd = e.getPoint();
                }
            }
        });
    }

    private static BufferedImage drawSelectionRectangleOnImage(BufferedImage originalImage, JLabel videoLabel) {
        if (selectionStart == null || selectionEnd == null || videoLabel == null) {
            return originalImage;
        }

        try {
            BufferedImage image = new BufferedImage(
                    originalImage.getWidth(),
                    originalImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g2d = image.createGraphics();
            g2d.drawImage(originalImage, 0, 0, null);

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
            return image;

        } catch (Exception e) {
            e.printStackTrace();
            return originalImage;
        }
    }

    // Обновленный метод для копирования выделения
    private static void copySelectionToClipboard(JLabel videoLabel) {
        if (selectionStart == null || selectionEnd == null || currentFrame == null) {
            return;
        }

        try {
            // Вычисляем границы прямоугольника на оригинальном изображении
            int x = Math.min(selectionStart.x, selectionEnd.x);
            int y = Math.min(selectionStart.y, selectionEnd.y);
            int width = Math.abs(selectionEnd.x - selectionStart.x);
            int height = Math.abs(selectionEnd.y - selectionStart.y);

            // Проверяем минимальный размер
            if (width < 10 || height < 10) {
                updateStatus("Область слишком мала (мин. 10x10)");
                return;
            }

            // Обрезаем область, чтобы она не выходила за границы
            x = Math.max(0, Math.min(x, currentFrame.getWidth() - 1));
            y = Math.max(0, Math.min(y, currentFrame.getHeight() - 1));
            width = Math.min(width, currentFrame.getWidth() - x);
            height = Math.min(height, currentFrame.getHeight() - y);

            // Вырезаем область из изображения
            BufferedImage selectedArea = currentFrame.getSubimage(x, y, width, height);

            // Копируем в буфер обмена
            copyImageToClipboard(selectedArea);

            updateStatus("Область " + width + "x" + height + " скопирована в буфер");

        } catch (Exception e) {
            updateStatus("Ошибка при копировании: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void copyImageToClipboard(BufferedImage image) {
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
                    return image;
                }
                throw new UnsupportedFlavorException(flavor);
            }
        };

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
    }

    private static void clearSelection() {
        selectionStart = null;
        selectionEnd = null;
        isSelecting = false;
    }

    private static Mat adjustContrastAndBrightness(Mat src, float alpha, float beta) {
        Mat dst = new Mat();
        // Используем встроенный метод для коррекции контрастности и яркости
        src.convertTo(dst, -1, alpha, beta);
        return dst;
    }

    private static void takeScreenshot() {
        if (currentFrame != null) {
            try {
                String filename = String.format("screenshot_%d.png", System.currentTimeMillis());
                // Здесь можно добавить сохранение в файл
                updateStatus("Снимок сохранен как " + filename);
            } catch (Exception e) {
                updateStatus("Ошибка сохранения снимка: " + e.getMessage());
            }
        } else {
            updateStatus("Нет активного изображения для сохранения");
        }
    }

    private static void updateStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
        System.out.println("Камера: " + message);
    }

    private static void stopCamera() {
        isRunning = false;
        clearSelection();
        videoHasFocus = false;

        try {
            if (grabber != null) {
                grabber.stop();
                grabber.release();
                grabber = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (cameraFrame != null) {
            cameraFrame.dispose();
            cameraFrame = null;
        }

        currentFrame = null;
        originalImageSize = null;

        updateStatus("Камера остановлена");
    }

    // Методы для работы с COM-портом
    private static void toggleConnection() {
        if (isConnected) {
            disconnect();
        } else {
            connect();
        }
    }

    private static void connect() {
        int selectedIndex = portComboBox.getSelectedIndex();
        if (selectedIndex <= 0) {
            JOptionPane.showMessageDialog(cameraFrame, "Выберите COM-порт!");
            return;
        }

        String selectedPort = (String) portComboBox.getSelectedItem();
        String portName = selectedPort.split(" - ")[0]; // Извлекаем имя порта

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
                isConnected = true;

                connectionStatusLabel.setText("Подключено");
                connectionStatusLabel.setForeground(new Color(0, 150, 0));
                connectButton.setText("Отключить");
                messageTextPane.setEditable(true);
                updateTransmissionButtons(true, false);

                updateStatus("Подключено к " + portName + " @ " + baudRate + " бод");
            } else {
                JOptionPane.showMessageDialog(cameraFrame, "Не удалось открыть порт!");
                connectionStatusLabel.setText("Ошибка подключения");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(cameraFrame, "Ошибка подключения: " + e.getMessage());
            connectionStatusLabel.setText("Ошибка подключения");
        }
    }

    private static void disconnect() {
        isConnected = false;
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

        connectionStatusLabel.setText("Не подключено");
        connectionStatusLabel.setForeground(Color.RED);
        connectButton.setText("Подключить");
        messageTextPane.setEditable(false);
        updateTransmissionButtons(false, false);
        lineNumberLabel.setText("Строка: 0");
        timeRemainingLabel.setText("Осталось: --:--");
        resetAllTextColor();

        serialPort = null;
        serialOutputStream = null;
    }

    // ============ НОВЫЙ МЕХАНИЗМ ПЕРЕДАЧИ ТЕКСТА ИЗ SocketTransmitter ============

    private static void startTransmission() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(cameraFrame, "Сначала подключитесь к COM-порту!");
            return;
        }

        String message = messageTextPane.getText();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(cameraFrame, "Введите текст для отправки!");
            return;
        }

        isStopped = false;
        isPaused = false;
        isEnglish = true;
        updateLayoutStatus();

        // Сбрасываем позицию отправки и подсветку
        currentTransmissionPosition = 0;
        totalCharactersToSend = message.replaceAll("[\\p{C}&&[^\n]]", "").length();
        transmissionStartTime = System.currentTimeMillis();
        lastUpdateTime = 0;

        // Сбрасываем цвет всего текста в черный перед началом передачи
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
                    JOptionPane.showMessageDialog(cameraFrame, "Ошибка передачи: " + e.getMessage());
                    disconnect();
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

            // ОСНОВНОЙ МЕХАНИЗМ ПАУЗЫ: если isPaused = true, то входим в цикл ожидания
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
        // Ищем русский символ напрямую в KEY_CODES_RUS
        if (KEY_CODES_RUS.containsKey(charStr)) {
            int code = KEY_CODES_RUS.get(charStr);
            sendByte(code);
            System.out.println("Отправлен русский символ '" + russianChar + "' как код: " + code);
        } else {
            // Если символ не найден в карте, пробуем отправить как есть
            System.out.println("Русский символ '" + russianChar + "' не найден в KEY_CODES_RUS, отправляем как ASCII");
            sendByte(russianChar);
        }
    }

    private static void togglePause() {
        if (!isConnected || transmissionThread == null || !transmissionThread.isAlive()) {
            return;
        }

        // Переключаем состояние паузы
        isPaused = !isPaused;
        pauseButton.setText(isPaused ? "Продолжить" : "Пауза");
    }

    private static void stopTransmission() {
        isStopped = true;
        updateTransmissionButtons(true, false);
    }

    private static void toggleKeyboardLayout() {
        try {
            if (!isConnected) {
                return;
            }

            if (isEnglish) {
                sendByte(96); // Переключение на русскую
            } else {
                sendByte(96); // Переключение на английскую
            }
            isEnglish = !isEnglish;
            updateLayoutStatus();
            Thread.sleep(SWITCH_LAYOUT_DELAY);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(cameraFrame,
                    "Ошибка переключения раскладки: " + ex.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void switchLayout() throws IOException, InterruptedException {
        System.out.println("Переключение раскладки с " + (isEnglish ? "английской" : "русской") + " на " + (!isEnglish ? "английскую" : "русскую"));

        if (isEnglish) {
            sendByte(96); // Переключение на русскую
        } else {
            sendByte(96); // Переключение на английскую
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
        connectButton.setText(connected ? "Отключить" : "Подключить");

        startButton.setEnabled(connected && !transmissionRunning);
        pauseButton.setEnabled(connected && transmissionRunning);
        stopButton.setEnabled(connected && transmissionRunning);
        toggleLayoutButton.setEnabled(connected);

        // Добавляем управление состоянием галочки IDE
        ideCheckBox.setEnabled(connected && !transmissionRunning);

        if (!transmissionRunning) {
            pauseButton.setText("Пауза");
        }

        portComboBox.setEnabled(!connected);
        baudRateComboBox.setEnabled(!connected);
        messageTextPane.setEditable(connected && !transmissionRunning);
    }

    // Вспомогательные методы для работы с раскладкой
    private static boolean isLayoutSwitchChar(char c) {
        return c == '~' || c == 'Ё' || c == 'ё' || c == '`';
    }

    // Метод проверки специальных символов для IDE (взят из SocketTransmitter)
    private static boolean isSpecialCharacterForIDE(char c) {
        return c == '{' ||
                c == '\"' ||
                c == '\'' ||
                c == '[' ||
                c == '(';
    }
}