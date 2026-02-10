package ru.miacomsoft;

import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_imgproc.*;
import org.bytedeco.opencv.global.opencv_imgproc;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

public class CameraMonitor extends JFrame {
    private JLabel imageLabel;
    private JLabel statusLabel;
    private OpenCVFrameGrabber grabber;
    private Timer timer;
    private double alpha = 1.0; // Контрастность по умолчанию = 1.0 (без изменений)
    private double beta = 0;    // Яркость по умолчанию = 0 (без изменений)
    private boolean filtersEnabled = false; // Фильтры отключены по умолчанию

    private Point startPoint = null;
    private Point currentPoint = null;
    private boolean selecting = false;

    private BufferedImage currentImage;
    private Dimension originalImageSize;
    private int cameraIndex = 1;
    private List<Integer> availableCameras;

    // Для обработки ошибок
    private int errorCount = 0;
    private static final int MAX_ERRORS = 10;

    public CameraMonitor() {
        setTitle("Camera Monitor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        // Найти доступные камеры
        findAvailableCameras();

        // Если камер нет - выйти
        if (availableCameras.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Не найдено доступных камер!\nПроверьте подключение камеры и перезапустите программу.",
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // Выбрать камеру
        selectCamera();

        initComponents();
        initCamera();
        initKeyBindings();

        setVisible(true);
        updateStatus();
    }

    private void findAvailableCameras() {
        availableCameras = new ArrayList<>();
        int maxCamerasToCheck = 10;

        for (int i = 0; i < maxCamerasToCheck; i++) {
            OpenCVFrameGrabber testGrabber = null;
            try {
                testGrabber = new OpenCVFrameGrabber(i);
                testGrabber.start();

                // Пробуем захватить несколько кадров
                for (int attempt = 0; attempt < 3; attempt++) {
                    Frame testFrame = testGrabber.grab();
                    if (testFrame != null && testFrame.imageWidth > 0) {
                        availableCameras.add(i);
                        break;
                    }
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                }
                testGrabber.stop();
            } catch (Exception e) {
                // Камера не доступна
            } finally {
                if (testGrabber != null) {
                    try {
                        testGrabber.release();
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private void selectCamera() {
        if (availableCameras.size() == 1) {
            cameraIndex = availableCameras.get(0);
            return;
        }

        // Пользователь выбирает камеру
        String[] options = new String[availableCameras.size()];
        for (int i = 0; i < availableCameras.size(); i++) {
            options[i] = "Камера " + availableCameras.get(i);
        }

        String selected = (String) JOptionPane.showInputDialog(
                this,
                "Выберите камеру:",
                "Выбор камеры",
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (selected != null) {
            int selectedIndex = Integer.parseInt(selected.replace("Камера ", ""));
            cameraIndex = selectedIndex;
        } else {
            System.exit(0);
        }
    }

    private void initComponents() {
        imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setBackground(Color.BLACK);
        imageLabel.setOpaque(true);

        JScrollPane scrollPane = new JScrollPane(imageLabel);
        add(scrollPane, BorderLayout.CENTER);

        // Панель с информацией
        JPanel infoPanel = new JPanel(new BorderLayout());

        // Панель статуса
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        infoPanel.add(statusLabel, BorderLayout.CENTER);

        // Панель управления
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(new JLabel("Управление:"));
        controlPanel.add(createButton("+ Контраст", e -> {
            alpha = Math.min(alpha + 0.1, 3.0);
            updateStatus();
        }));
        controlPanel.add(createButton("- Контраст", e -> {
            alpha = Math.max(alpha - 0.1, 0.1);
            updateStatus();
        }));
        controlPanel.add(createButton("+ Яркость", e -> {
            beta = Math.min(beta + 10, 100);
            updateStatus();
        }));
        controlPanel.add(createButton("- Яркость", e -> {
            beta = Math.max(beta - 10, -100);
            updateStatus();
        }));
        controlPanel.add(createButton("Фильтры", e -> {
            filtersEnabled = !filtersEnabled;
            updateStatus();
        }));
        controlPanel.add(createButton("Переключить камеру", e -> switchCamera()));
        controlPanel.add(createButton("Выход", e -> System.exit(0)));

        infoPanel.add(controlPanel, BorderLayout.SOUTH);
        add(infoPanel, BorderLayout.SOUTH);

        // Обработчик мыши для выделения области
        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                startPoint = e.getPoint();
                selecting = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (selecting && startPoint != null && currentPoint != null) {
                    captureSelectedArea();
                }
                selecting = false;
                startPoint = null;
                currentPoint = null;
                repaint();
            }
        });

        imageLabel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (selecting) {
                    currentPoint = e.getPoint();
                    repaint();
                }
            }
        });

        // Обработчик изменения размера окна
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateImageDisplay();
            }
        });
    }

    private JButton createButton(String text, ActionListener listener) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        button.setMargin(new Insets(2, 5, 2, 5));
        return button;
    }

    private void initCamera() {
        try {
            if (grabber != null) {
                grabber.stop();
                grabber.release();
            }

            grabber = new OpenCVFrameGrabber(cameraIndex);
            // Устанавливаем оптимальные размеры
            grabber.setImageWidth(1280);
            grabber.setImageHeight(720);
            grabber.setFrameRate(30);
            grabber.start();

            // Таймер для обновления кадров (30 FPS)
            if (timer != null) {
                timer.stop();
            }
            timer = new Timer(33, e -> updateFrame());
            timer.start();

            errorCount = 0; // Сбросить счетчик ошибок

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Не удалось открыть камеру " + cameraIndex + ": " + e.getMessage(),
                    "Ошибка камеры", JOptionPane.ERROR_MESSAGE);
            // Пробуем следующую камеру
            switchCamera();
        }
    }

    private void initKeyBindings() {
        InputMap inputMap = imageLabel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = imageLabel.getActionMap();

        // Базовые горячие клавиши
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0), "exit");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exit");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), "increaseContrast");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.SHIFT_DOWN_MASK), "increaseContrast");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "decreaseContrast");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "increaseBrightness");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0), "decreaseBrightness");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "toggleFilters");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK), "switchCamera");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "refreshCamera");

        actionMap.put("exit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        actionMap.put("increaseContrast", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                alpha = Math.min(alpha + 0.1, 3.0);
                updateStatus();
            }
        });

        actionMap.put("decreaseContrast", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                alpha = Math.max(alpha - 0.1, 0.1);
                updateStatus();
            }
        });

        actionMap.put("increaseBrightness", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                beta = Math.min(beta + 10, 100);
                updateStatus();
            }
        });

        actionMap.put("decreaseBrightness", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                beta = Math.max(beta - 10, -100);
                updateStatus();
            }
        });

        actionMap.put("toggleFilters", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                filtersEnabled = !filtersEnabled;
                updateStatus();
            }
        });

        actionMap.put("switchCamera", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                switchCamera();
            }
        });

        actionMap.put("refreshCamera", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                initCamera();
            }
        });
    }

    private void switchCamera() {
        if (availableCameras.isEmpty()) return;

        // Найти следующий доступный индекс камеры
        int currentIdx = availableCameras.indexOf(cameraIndex);
        if (currentIdx == -1 || currentIdx >= availableCameras.size() - 1) {
            cameraIndex = availableCameras.get(0);
        } else {
            cameraIndex = availableCameras.get(currentIdx + 1);
        }

        initCamera();
    }

    private void updateFrame() {
        try {
            Frame frame = grabber.grab();
            if (frame != null && frame.imageWidth > 0 && frame.imageHeight > 0) {
                Java2DFrameConverter converter = new Java2DFrameConverter();
                BufferedImage originalImage = converter.getBufferedImage(frame);

                BufferedImage processedImage;
                if (filtersEnabled) {
                    processedImage = applyFilters(originalImage);
                } else {
                    processedImage = originalImage;
                }

                if (originalImageSize == null) {
                    originalImageSize = new Dimension(processedImage.getWidth(), processedImage.getHeight());
                }

                currentImage = processedImage;
                updateImageDisplay();
                errorCount = 0; // Сбросить счетчик ошибок при успешном захвате
            }
        } catch (Exception e) {
            errorCount++;
            if (errorCount > MAX_ERRORS) {
                timer.stop();
                JOptionPane.showMessageDialog(this,
                        "Потеряно соединение с камерой " + cameraIndex +
                                "\nПопробуйте переключить камеру (Ctrl+C) или перезапустить программу.",
                        "Ошибка камеры", JOptionPane.WARNING_MESSAGE);
                errorCount = 0;
            }
        }
    }

    private void updateImageDisplay() {
        if (currentImage != null) {
            BufferedImage displayImage = currentImage;

            // Если есть выделенная область, рисуем её
            if (selecting && startPoint != null && currentPoint != null) {
                displayImage = new BufferedImage(
                        currentImage.getWidth(),
                        currentImage.getHeight(),
                        currentImage.getType()
                );
                Graphics2D g2d = displayImage.createGraphics();
                g2d.drawImage(currentImage, 0, 0, null);
                g2d.setColor(Color.RED);
                g2d.setStroke(new BasicStroke(2));
                int x = Math.min(startPoint.x, currentPoint.x);
                int y = Math.min(startPoint.y, currentPoint.y);
                int width = Math.abs(currentPoint.x - startPoint.x);
                int height = Math.abs(currentPoint.y - startPoint.y);
                g2d.drawRect(x, y, width, height);
                g2d.dispose();
            }

            // Масштабируем изображение под размер компонента
            Dimension labelSize = imageLabel.getSize();
            if (labelSize.width > 0 && labelSize.height > 0) {
                Image scaledImage = displayImage.getScaledInstance(
                        labelSize.width,
                        labelSize.height,
                        Image.SCALE_SMOOTH
                );
                imageLabel.setIcon(new ImageIcon(scaledImage));
            } else {
                imageLabel.setIcon(new ImageIcon(displayImage));
            }
        }
    }

    private BufferedImage applyFilters(BufferedImage image) {
        if (alpha == 1.0 && beta == 0) {
            return image; // Нет изменений
        }

        OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
        Frame frame = new Java2DFrameConverter().convert(image);
        Mat mat = converter.convert(frame);

        Mat result = new Mat();

        if (filtersEnabled) {
            // Применяем черно-белый фильтр
            Mat gray = new Mat();
            opencv_imgproc.cvtColor(mat, gray, opencv_imgproc.COLOR_BGR2GRAY);
            // Применяем контрастность и яркость
            gray.convertTo(result, -1, alpha, beta);
        } else {
            // Только контрастность и яркость к цветному изображению
            mat.convertTo(result, -1, alpha, beta);
        }

        Frame processedFrame = converter.convert(result);
        return new Java2DFrameConverter().getBufferedImage(processedFrame);
    }

    private void captureSelectedArea() {
        if (startPoint == null || currentPoint == null || currentImage == null) return;

        double scaleX = (double) originalImageSize.width / imageLabel.getWidth();
        double scaleY = (double) originalImageSize.height / imageLabel.getHeight();

        int x = (int) (Math.min(startPoint.x, currentPoint.x) * scaleX);
        int y = (int) (Math.min(startPoint.y, currentPoint.y) * scaleY);
        int width = (int) (Math.abs(currentPoint.x - startPoint.x) * scaleX);
        int height = (int) (Math.abs(currentPoint.y - startPoint.y) * scaleY);

        if (width > 10 && height > 10) {
            try {
                BufferedImage selectedArea = currentImage.getSubimage(x, y, width, height);
                setClipboard(selectedArea);

                // Показать сообщение о успешном копировании
                statusLabel.setText("Область скопирована в буфер обмена: " + width + "x" + height);

                Timer clearTimer = new Timer(3000, e -> statusLabel.setText(" "));
                clearTimer.setRepeats(false);
                clearTimer.start();

            } catch (Exception e) {
                statusLabel.setText("Ошибка: " + e.getMessage());
            }
        }
    }

    private void setClipboard(BufferedImage image) {
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

    private void updateStatus() {
        setTitle(String.format("Camera Monitor - Камера %d | Контраст: %.1f | Яркость: %.0f | Фильтры: %s",
                cameraIndex, alpha, beta, filtersEnabled ? "ВКЛ" : "ВЫКЛ"));
    }

    @Override
    public void dispose() {
        if (timer != null) {
            timer.stop();
        }
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        super.dispose();
    }

    // Точка входа для тестирования
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new CameraMonitor();
        });
    }
}