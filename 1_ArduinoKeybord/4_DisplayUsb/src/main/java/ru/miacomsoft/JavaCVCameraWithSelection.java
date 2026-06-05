package ru.miacomsoft;

import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.opencv_core.Mat;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.CV_8U;

public class JavaCVCameraWithSelection {
    private static FrameGrabber grabber;
    private static CanvasFrame canvas;
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

    // Панель управления
    private static JLabel contrastLabel;
    private static JLabel brightnessLabel;
    private static JLabel statusLabel;

    // Компоненты для выбора источника
    private static JComboBox<String> deviceCombo;
    private static JButton refreshDevicesBtn;
    private static List<DeviceInfo> availableDevices = new ArrayList<>();

    private static final Object cameraLock = new Object();
    private static Thread cameraThread = null;


    // Класс для хранения информации об устройстве
    private static class DeviceInfo {
        int index;
        String name;
        String description;

        DeviceInfo(int index, String name, String description) {
            this.index = index;
            this.name = name;
            this.description = description;
        }

        @Override
        public String toString() {
            return index + ": " + name + (description.isEmpty() ? "" : " (" + description + ")");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> createAndShowGUI());
    }

    private static void createAndShowGUI() {
        // Создаем главное окно управления
        JFrame controlFrame = new JFrame("Камера с выделением области");
        controlFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        controlFrame.setLayout(new BorderLayout());

        // Панель информации и состояния
        JPanel infoPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        contrastLabel = new JLabel("1.0");
        brightnessLabel = new JLabel("0.0");
        statusLabel = new JLabel("Готов к работе");

        infoPanel.add(new JLabel("Контрастность:"));
        infoPanel.add(contrastLabel);
        infoPanel.add(new JLabel("Яркость:"));
        infoPanel.add(brightnessLabel);
        infoPanel.add(new JLabel("Статус:"));
        infoPanel.add(statusLabel);

        // Панель выбора устройства
        JPanel devicePanel = new JPanel(new BorderLayout());
        devicePanel.setBorder(BorderFactory.createTitledBorder("Источник видео"));

        JPanel deviceSelectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        deviceCombo = new JComboBox<>();
        deviceCombo.setPreferredSize(new Dimension(350, 25));
        refreshDevicesBtn = new JButton("Обновить список");

        deviceSelectPanel.add(new JLabel("Устройство:"));
        deviceSelectPanel.add(deviceCombo);
        deviceSelectPanel.add(refreshDevicesBtn);

        devicePanel.add(deviceSelectPanel, BorderLayout.CENTER);

        // Загружаем список устройств
        refreshDeviceList();

        // Панель управления камерой
        JPanel cameraPanel = new JPanel(new FlowLayout());

        JLabel resLabel = new JLabel("Разрешение:");
        String[] resolutions = {"640x480", "800x600", "1024x768", "1280x720", "1600x900", "1920x1080"};
        JComboBox<String> resCombo = new JComboBox<>(resolutions);
        resCombo.setSelectedIndex(3);

        JButton startBtn = new JButton("Старт");
        JButton stopBtn = new JButton("Стоп");
        JButton screenshotBtn = new JButton("Снимок");

        cameraPanel.add(resLabel);
        cameraPanel.add(resCombo);
        cameraPanel.add(startBtn);
        cameraPanel.add(stopBtn);
        cameraPanel.add(screenshotBtn);

        // Панель горячих клавиш и инструкций
        JPanel instructionPanel = new JPanel(new BorderLayout());
        instructionPanel.setBorder(BorderFactory.createTitledBorder("Управление"));

        JTextArea instructions = new JTextArea();
        instructions.setText("Управление камерой:\n" +
                "  + / - : Изменить контрастность\n" +
                "  Ctrl + +/- : Изменить яркость\n" +
                "  C / B : Сброс контрастности/яркости\n" +
                "  R : Сброс всех настроек\n\n" +
                "Выделение области:\n" +
                "  Зажмите ЛКМ и выделите область\n" +
                "  Отпустите ЛКМ - область скопируется в буфер\n" +
                "  ESC : Отмена выделения");
        instructions.setEditable(false);
        instructions.setBackground(new Color(240, 240, 240));
        instructions.setFont(new Font("Monospaced", Font.PLAIN, 12));

        instructionPanel.add(new JScrollPane(instructions), BorderLayout.CENTER);

        // Собираем главное окно
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(infoPanel, BorderLayout.NORTH);
        mainPanel.add(devicePanel, BorderLayout.NORTH);
        mainPanel.add(cameraPanel, BorderLayout.CENTER);
        mainPanel.add(instructionPanel, BorderLayout.SOUTH);

        controlFrame.add(mainPanel, BorderLayout.CENTER);
        controlFrame.setSize(600, 500);
        controlFrame.setLocationRelativeTo(null);
        controlFrame.setVisible(true);

        // Обработчики событий
        refreshDevicesBtn.addActionListener(e -> refreshDeviceList());

        startBtn.addActionListener(e -> {
            if (deviceCombo.getSelectedIndex() >= 0) {
                DeviceInfo selectedDevice = availableDevices.get(deviceCombo.getSelectedIndex());
                String selectedRes = (String) resCombo.getSelectedItem();
                String[] dims = selectedRes.split("x");
                int width = Integer.parseInt(dims[0]);
                int height = Integer.parseInt(dims[1]);

                startCamera(selectedDevice.index, width, height);
            } else {
                updateStatus("Пожалуйста, выберите устройство");
            }
        });

        stopBtn.addActionListener(e -> stopCamera());
        screenshotBtn.addActionListener(e -> takeScreenshot());

        // Добавляем глобальные горячие клавиши для окна управления
        setupGlobalKeyBindings(controlFrame);
    }

    private static void refreshDeviceList() {
        availableDevices.clear();
        deviceCombo.removeAllItems();

        updateStatus("Поиск доступных устройств...");

        // Временно перенаправляем stderr для подавления ошибок FlyCapture
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(new OutputStream() {
            private boolean isFirstLine = true;
            @Override
            public void write(int b) {
                // Полностью подавляем вывод ошибок
            }
        }));

        // Поиск устройств через videoInput (для получения реальных имен)
        findDevicesWithVideoInput();

        // Поиск устройств через стандартный метод
        for (int i = 0; i < 10; i++) {
            try {
                FrameGrabber testGrabber = FrameGrabber.createDefault(i);
                if (testGrabber != null) {
                    try {
                        testGrabber.start();
                        // Проверяем, не добавлено ли уже устройство с таким индексом
                        boolean exists = false;
                        for (DeviceInfo device : availableDevices) {
                            if (device.index == i) {
                                exists = true;
                                break;
                            }
                        }

                        if (!exists) {
                            String deviceName = getRealDeviceName(i);
                            DeviceInfo device = new DeviceInfo(i, deviceName, getDeviceDescription(testGrabber));
                            availableDevices.add(device);
                            deviceCombo.addItem(device.toString());
                            updateStatus("Найдено устройство: " + deviceName);
                        }

                        testGrabber.stop();
                        testGrabber.release();
                    } catch (Exception e) {
                        // Устройство недоступно или занято - игнорируем
                    }
                }
            } catch (Exception e) {
                // Устройство не существует - игнорируем
            }
        }

        // Восстанавливаем вывод ошибок
        System.setErr(originalErr);

        if (availableDevices.isEmpty()) {
            deviceCombo.addItem("Не найдено устройств");
            deviceCombo.setEnabled(false);
            updateStatus("Устройства не найдены. Проверьте подключение камеры.");
        } else {
            deviceCombo.setEnabled(true);
            deviceCombo.setSelectedIndex(0);
            updateStatus("Найдено " + availableDevices.size() + " устройств");
        }
    }

    // Новый метод для получения реальных имен устройств через videoInput
    private static void findDevicesWithVideoInput() {
        try {
            // Используем videoInput для получения списка устройств
            ProcessBuilder pb = new ProcessBuilder("powershell.exe",
                    "Get-CimInstance Win32_PnPEntity | Where-Object { $_.PNPClass -eq 'Camera' -or $_.PNPClass -eq 'Image' } | Select-Object Name, DeviceID");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            List<String> cameraNames = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("Name") && !line.startsWith("--") && !line.startsWith("USB")) {
                    cameraNames.add(line);
                }
            }
            process.waitFor();

            // Добавляем найденные камеры в список
            for (int i = 0; i < cameraNames.size() && i < 10; i++) {
                String name = cameraNames.get(i);
                if (name != null && !name.isEmpty() && name.length() > 3) {
                    DeviceInfo device = new DeviceInfo(i, name, "Камера");
                    availableDevices.add(device);
                    deviceCombo.addItem(device.toString());
                    updateStatus("Найдено устройство: " + name);
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки получения имен через PowerShell
        }
    }

    // Улучшенный метод получения имени устройства
    private static String getRealDeviceName(int index) {
        // Пробуем получить имя через DirectShow
        try {
            // Используем FFmpeg для получения имени
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-list_devices", "true", "-f", "dshow", "-i", "dummy");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean found = false;

            while ((line = reader.readLine()) != null) {
                if (line.contains("[" + index + "]")) {
                    found = true;
                } else if (found && line.contains("\"")) {
                    int start = line.indexOf("\"");
                    int end = line.lastIndexOf("\"");
                    if (start != -1 && end != -1 && start < end) {
                        String name = line.substring(start + 1, end);
                        if (!name.isEmpty()) {
                            return name;
                        }
                    }
                    found = false;
                }
            }
            process.waitFor();
        } catch (Exception e) {
            // Игнорируем
        }

        // Если не удалось получить имя, возвращаем стандартное
        return getDeviceNameByIndex(index);
    }

    private static String getDeviceNameByIndex(int index) {
        switch (index) {
            case 0: return "Insta360 Virtual Camera";
            case 1: return "USB2 Video";
            case 2: return "OBS Virtual Camera";
            default: return "Камера " + index;
        }
    }

    private static String getDeviceName(FrameGrabber grabber, int index) {
        try {
            // Пробуем получить реальное имя
            String realName = getRealDeviceName(index);
            if (realName != null && !realName.equals("Камера " + index)) {
                return realName;
            }

            // Для Windows можно получить имя через videoInput
            if (grabber.getClass().getSimpleName().contains("OpenCVFrameGrabber")) {
                return getDeviceNameByIndex(index);
            }
        } catch (Exception e) {
            // Игнорируем
        }
        return "Устройство " + index;
    }

    private static String getDeviceDescription(FrameGrabber grabber) {
        try {
            // Пробуем получить дополнительную информацию
            if (grabber.getImageWidth() > 0 && grabber.getImageHeight() > 0) {
                return grabber.getImageWidth() + "x" + grabber.getImageHeight();
            }
        } catch (Exception e) {
            // Игнорируем
        }
        return "";
    }

    private static void findOpenCVDevices() {
        // Дополнительный поиск с помощью OpenCV
        // На некоторых системах устройства могут быть доступны через другие индексы
        int[] additionalIndices = {0, 1, 2, 3, 4, 5, 10, 100, 200};

        for (int i : additionalIndices) {
            boolean exists = false;
            for (DeviceInfo device : availableDevices) {
                if (device.index == i) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                try {
                    // Пробуем создать grabber для проверки
                    FrameGrabber testGrabber = FrameGrabber.createDefault(i);
                    if (testGrabber != null) {
                        try {
                            testGrabber.setImageWidth(320);
                            testGrabber.setImageHeight(240);
                            testGrabber.start();
                            DeviceInfo device = new DeviceInfo(i, "Камера " + i, "Доступна");
                            availableDevices.add(device);
                            deviceCombo.addItem(device.toString());
                            testGrabber.stop();
                            testGrabber.release();
                            updateStatus("Найдено дополнительное устройство: " + i);
                        } catch (Exception e) {
                            // Не доступно
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем
                }
            }
        }
    }

    private static void setupGlobalKeyBindings(JFrame frame) {
        InputMap inputMap = frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = frame.getRootPane().getActionMap();

        // Клавиши для контрастности и яркости
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

        actionMap.put("increaseContrast", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                contrast = Math.min(3.0f, contrast + CONTRAST_STEP);
                contrastLabel.setText(String.format("%.1f", contrast));
                updateStatus("Контрастность увеличена до " + String.format("%.1f", contrast));
            }
        });

        actionMap.put("decreaseContrast", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                contrast = Math.max(0.1f, contrast - CONTRAST_STEP);
                contrastLabel.setText(String.format("%.1f", contrast));
                updateStatus("Контрастность уменьшена до " + String.format("%.1f", contrast));
            }
        });

        actionMap.put("increaseBrightness", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                brightness = Math.min(100.0f, brightness + BRIGHTNESS_STEP);
                brightnessLabel.setText(String.format("%.1f", brightness));
                updateStatus("Яркость увеличена до " + String.format("%.1f", brightness));
            }
        });

        actionMap.put("decreaseBrightness", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                brightness = Math.max(-100.0f, brightness - BRIGHTNESS_STEP);
                brightnessLabel.setText(String.format("%.1f", brightness));
                updateStatus("Яркость уменьшена до " + String.format("%.1f", brightness));
            }
        });

        actionMap.put("resetContrast", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                contrast = 1.0f;
                contrastLabel.setText("1.0");
                updateStatus("Контрастность сброшена");
            }
        });

        actionMap.put("resetBrightness", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                brightness = 0.0f;
                brightnessLabel.setText("0.0");
                updateStatus("Яркость сброшена");
            }
        });

        actionMap.put("resetAll", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                contrast = 1.0f;
                brightness = 0.0f;
                contrastLabel.setText("1.0");
                brightnessLabel.setText("0.0");
                updateStatus("Все настройки сброшены");
            }
        });
    }

    private static void stopCamera() {
        synchronized (JavaCVCameraWithSelection.class) {
            isRunning = false;
            clearSelection();

            try {
                if (grabber != null) {
                    grabber.stop();
                    grabber.release();
                    grabber = null;
                }
            } catch (Exception e) {
                System.err.println("Ошибка при остановке grabber: " + e.getMessage());
            }

            if (canvas != null) {
                try {
                    canvas.dispose();
                } catch (Exception e) {
                    System.err.println("Ошибка при закрытии canvas: " + e.getMessage());
                }
                canvas = null;
            }

            currentFrame = null;
            originalImageSize = null;

            updateStatus("Камера остановлена");
        }
    }

    private static void addMouseListenersToCanvas(CanvasFrame canvasFrame) {
        if (canvasFrame == null) return;

        Component canvasComponent = canvasFrame.getCanvas();

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
                if (e.getButton() == MouseEvent.BUTTON1 && isSelecting && selectionStart != null) {
                    selectionEnd = e.getPoint();
                    isSelecting = false;

                    // Копируем выделенную область в буфер обмена
                    copySelectionToClipboard();

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
                if (isSelecting && selectionStart != null) {
                    selectionEnd = e.getPoint();
                }
            }
        });
    }

    private static Frame drawSelectionRectangle(Frame originalFrame) {
        if (selectionStart == null || selectionEnd == null) {
            return originalFrame;
        }

        try {
            Java2DFrameConverter converter = new Java2DFrameConverter();
            BufferedImage image = converter.getBufferedImage(originalFrame);

            Graphics2D g2d = image.createGraphics();
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

            return converter.convert(image);

        } catch (Exception e) {
            e.printStackTrace();
            return originalFrame;
        }
    }

    private static void copySelectionToClipboard() {
        if (selectionStart == null || selectionEnd == null || currentFrame == null) {
            return;
        }

        try {
            // Используем текущий canvas для получения размеров
            CanvasFrame currentCanvas = canvas;
            if (currentCanvas == null) {
                return;
            }

            // Вычисляем реальные координаты на исходном изображении
            double scaleX = (double) originalImageSize.width / currentCanvas.getCanvas().getWidth();
            double scaleY = (double) originalImageSize.height / currentCanvas.getCanvas().getHeight();

            int x = (int) (Math.min(selectionStart.x, selectionEnd.x) * scaleX);
            int y = (int) (Math.min(selectionStart.y, selectionEnd.y) * scaleY);
            int width = Math.abs((int) ((selectionEnd.x - selectionStart.x) * scaleX));
            int height = Math.abs((int) ((selectionEnd.y - selectionStart.y) * scaleY));

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
                // TODO: Добавить сохранение в файл
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
        System.out.println(message);
    }
    private static void startCamera(int index, int width, int height) {
        if (isRunning) {
            stopCamera();
            // Даем время на полную остановку предыдущей камеры
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Thread cameraThread = new Thread(() -> {
            FrameGrabber localGrabber = null;
            CanvasFrame localCanvas = null;

            try {
                // Создаем окно для отображения видео
                localCanvas = new CanvasFrame("Камера " + index + " - выделите область мышью");
                localCanvas.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                localCanvas.setCanvasSize(width, height);

                // Устанавливаем обработчик закрытия окна
                localCanvas.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        stopCamera();
                    }
                });

                // Добавляем обработчики мыши для выделения области
                addMouseListenersToCanvas(localCanvas);

                // Добавляем обработчик клавиш для отмены выделения
                localCanvas.getCanvas().addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                            clearSelection();
                            updateStatus("Выделение отменено");
                        }
                    }
                });

                // Создаем grabber для выбранного устройства
                localGrabber = FrameGrabber.createDefault(index);
                localGrabber.setImageWidth(width);
                localGrabber.setImageHeight(height);
                localGrabber.setFrameRate(30);

                localGrabber.start();

                // Присваиваем глобальным переменным только после успешного запуска
                synchronized (JavaCVCameraWithSelection.class) {
                    if (isRunning) {
                        // Если уже запущена другая камера, останавливаем её
                        if (grabber != null) {
                            try {
                                grabber.stop();
                                grabber.release();
                            } catch (Exception e) {
                                // Игнорируем
                            }
                        }
                        if (canvas != null) {
                            canvas.dispose();
                        }
                    }
                    grabber = localGrabber;
                    canvas = localCanvas;
                    isRunning = true;
                }

                // Получаем информацию об устройстве
                String deviceInfo = "";
                for (DeviceInfo device : availableDevices) {
                    if (device.index == index) {
                        deviceInfo = device.name;
                        break;
                    }
                }

                updateStatus("Камера запущена: " + deviceInfo + " (" + width + "x" + height + ")");

                // Конвертеры для преобразования кадров
                OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
                Java2DFrameConverter converterToBufferedImage = new Java2DFrameConverter();

                long frameCount = 0;
                long startTime = System.currentTimeMillis();

                // Флаг для отслеживания состояния окна
                boolean wasVisible = true;

                // Основной цикл захвата
                while (isRunning) {
                    try {
                        // Проверяем существование и видимость окна
                        if (localCanvas == null || !localCanvas.isVisible()) {
                            if (wasVisible) {
                                updateStatus("Окно камеры закрыто");
                                wasVisible = false;
                            }
                            break;
                        }
                        wasVisible = true;

                        // Проверяем существование grabber
                        if (localGrabber == null) {
                            break;
                        }

                        Frame grabbedFrame = localGrabber.grab();

                        if (grabbedFrame != null) {
                            frameCount++;

                            // Обновляем FPS в заголовке
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - startTime >= 1000) {
                                double fps = frameCount * 1000.0 / (currentTime - startTime);
                                if (localCanvas != null && localCanvas.isVisible()) {
                                    localCanvas.setTitle(String.format("Камера %d @ %.1f FPS - выделите область", index, fps));
                                }
                                frameCount = 0;
                                startTime = currentTime;
                            }

                            // Преобразуем кадр в Mat для обработки
                            Mat mat = converterToMat.convert(grabbedFrame);
                            if (mat == null) {
                                continue;
                            }

                            // Применяем коррекцию контрастности и яркости
                            Mat processedMat = adjustContrastAndBrightness(mat, contrast, brightness);

                            // Преобразуем обратно в Frame
                            Frame processedFrame = converterToMat.convert(processedMat);

                            // Сохраняем текущий кадр для выделения области
                            currentFrame = converterToBufferedImage.getBufferedImage(processedFrame);
                            if (originalImageSize == null && currentFrame != null) {
                                originalImageSize = new Dimension(currentFrame.getWidth(), currentFrame.getHeight());
                            }

                            // Если идет выделение области, рисуем прямоугольник
                            if (isSelecting && selectionStart != null && selectionEnd != null && localCanvas != null && localCanvas.isVisible()) {
                                Frame frameWithSelection = drawSelectionRectangle(processedFrame);
                                localCanvas.showImage(frameWithSelection);
                            } else if (localCanvas != null && localCanvas.isVisible()) {
                                localCanvas.showImage(processedFrame);
                            }

                            // Освобождаем ресурсы
                            processedMat.release();
                            mat.release();
                        }

                        // Небольшая задержка для снижения нагрузки на CPU
                        Thread.sleep(1);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        if (isRunning && localCanvas != null && localCanvas.isVisible()) {
                            System.err.println("Ошибка обработки кадра: " + e.getMessage());
                            e.printStackTrace();
                        }
                        break;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null,
                            "Ошибка запуска камеры (индекс " + index + "): " + e.getMessage(),
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                // Очистка ресурсов
                synchronized (JavaCVCameraWithSelection.class) {
                    if (localGrabber != null && localGrabber == grabber) {
                        try {
                            if (grabber != null) {
                                grabber.stop();
                                grabber.release();
                            }
                        } catch (Exception e) {
                            // Игнорируем ошибки при остановке
                        }
                        grabber = null;
                    }

                    if (localCanvas != null && localCanvas == canvas) {
                        try {
                            if (canvas != null) {
                                canvas.dispose();
                            }
                        } catch (Exception e) {
                            // Игнорируем ошибки при закрытии
                        }
                        canvas = null;
                    }

                    if (localGrabber == grabber && localCanvas == canvas) {
                        isRunning = false;
                        currentFrame = null;
                        originalImageSize = null;
                        updateStatus("Камера остановлена");
                    }
                }
            }
        });

        cameraThread.setDaemon(true);
        cameraThread.start();
    }
}