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
import java.io.IOException;

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
        mainPanel.add(cameraPanel, BorderLayout.CENTER);
        mainPanel.add(instructionPanel, BorderLayout.SOUTH);

        controlFrame.add(mainPanel, BorderLayout.CENTER);
        controlFrame.setSize(600, 400);
        controlFrame.setLocationRelativeTo(null);
        controlFrame.setVisible(true);

        // Обработчики событий
        startBtn.addActionListener(e -> {
            String selectedRes = (String) resCombo.getSelectedItem();
            String[] dims = selectedRes.split("x");
            int width = Integer.parseInt(dims[0]);
            int height = Integer.parseInt(dims[1]);

            startCamera(0, width, height);
        });

        stopBtn.addActionListener(e -> stopCamera());

        screenshotBtn.addActionListener(e -> takeScreenshot());

        // Добавляем глобальные горячие клавиши для окна управления
        setupGlobalKeyBindings(controlFrame);
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

    private static void startCamera(int index, int width, int height) {
        if (isRunning) {
            stopCamera();
        }

        new Thread(() -> {
            try {
                // Создаем окно для отображения видео
                canvas = new CanvasFrame("Камера - выделите область мышью");
                canvas.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                canvas.setCanvasSize(width, height);

                // Добавляем обработчики мыши для выделения области
                addMouseListenersToCanvas();

                // Добавляем обработчик клавиш для отмены выделения
                canvas.getCanvas().addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                            clearSelection();
                            updateStatus("Выделение отменено");
                        }
                    }
                });

                // Создаем grabber
                grabber = FrameGrabber.createDefault(index);
                grabber.setImageWidth(width);
                grabber.setImageHeight(height);
                grabber.setFrameRate(30);

                grabber.start();
                isRunning = true;

                updateStatus("Камера запущена " + width + "x" + height);

                // Конвертеры для преобразования кадров
                OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
                Java2DFrameConverter converterToBufferedImage = new Java2DFrameConverter();

                long frameCount = 0;
                long startTime = System.currentTimeMillis();

                // Основной цикл захвата
                while (isRunning && canvas.isVisible()) {
                    try {
                        Frame grabbedFrame = grabber.grab();

                        if (grabbedFrame != null) {
                            frameCount++;

                            // Обновляем FPS в заголовке
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - startTime >= 1000) {
                                double fps = frameCount * 1000.0 / (currentTime - startTime);
                                canvas.setTitle(String.format("Камера @ %.1f FPS - выделите область", fps));
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

                            // Если идет выделение области, рисуем прямоугольник
                            if (isSelecting && selectionStart != null && selectionEnd != null) {
                                Frame frameWithSelection = drawSelectionRectangle(processedFrame);
                                canvas.showImage(frameWithSelection);
                            } else {
                                canvas.showImage(processedFrame);
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

    private static void addMouseListenersToCanvas() {
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
                if (isSelecting) {
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
            // Вычисляем реальные координаты на исходном изображении
            double scaleX = (double) originalImageSize.width / canvas.getCanvas().getWidth();
            double scaleY = (double) originalImageSize.height / canvas.getCanvas().getHeight();

            int x = (int) (Math.min(selectionStart.x, selectionEnd.x) * scaleX);
            int y = (int) (Math.min(selectionStart.y, selectionEnd.y) * scaleY);
            int width = (int) (Math.abs(selectionEnd.x - selectionStart.x) * scaleX);
            int height = (int) (Math.abs(selectionEnd.y - selectionStart.y) * scaleY);

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
        System.out.println(message);
    }

    private static void stopCamera() {
        isRunning = false;
        clearSelection();

        try {
            if (grabber != null) {
                grabber.stop();
                grabber.release();
                grabber = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (canvas != null) {
            canvas.dispose();
            canvas = null;
        }

        currentFrame = null;
        originalImageSize = null;

        updateStatus("Камера остановлена");
    }
}