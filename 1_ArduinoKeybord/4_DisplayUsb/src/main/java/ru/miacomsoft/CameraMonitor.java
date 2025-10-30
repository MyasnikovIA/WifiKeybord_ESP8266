package ru.miacomsoft;

import org.bytedeco.javacv.*;
import org.bytedeco.javacv.Frame;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_imgproc.*;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.global.opencv_core;

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

public class CameraMonitor extends JFrame {
    private JLabel imageLabel;
    private OpenCVFrameGrabber grabber;
    private Timer timer;
    private double alpha = 1.5;
    private double beta = 0;
    private boolean filtersEnabled = true;

    private Point startPoint = null;
    private Point currentPoint = null;
    private boolean selecting = false;

    private BufferedImage currentImage;
    private Dimension originalImageSize;

    public CameraMonitor() {
        setTitle("Camera Monitor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        initComponents();
        initCamera();
        initKeyBindings();

        setVisible(true);
    }

    private void initComponents() {
        imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JScrollPane scrollPane = new JScrollPane(imageLabel);
        add(scrollPane, BorderLayout.CENTER);

        // Панель с информацией
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        infoPanel.add(new JLabel("Управление: '+' - контрастность, '-' - контрастность, 'a' - яркость, 'z' - яркость, 'Ctrl+Z' - фильтры, 'q' - выход"));
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

    private void initCamera() {
        try {
            grabber = new OpenCVFrameGrabber(0);
            grabber.setImageWidth(1920);
            grabber.setImageHeight(820);
            grabber.start();

            // Таймер для обновления кадров
            timer = new Timer(33, e -> updateFrame());
            timer.start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Не удалось открыть камеру: " + e.getMessage());
            System.exit(1);
        }
    }

    private void initKeyBindings() {
        InputMap inputMap = imageLabel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = imageLabel.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0), "exit");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), "increaseContrast");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), "increaseContrast");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "decreaseContrast");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0), "increaseBrightness");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0), "decreaseBrightness");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK), "toggleFilters");

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
    }

    private void updateFrame() {
        try {
            Frame frame = grabber.grab();
            if (frame != null) {
                // Конвертируем Frame в BufferedImage
                Java2DFrameConverter converter = new Java2DFrameConverter();
                BufferedImage originalImage = converter.getBufferedImage(frame);

                BufferedImage processedImage;
                if (filtersEnabled) {
                    processedImage = applyFilters(originalImage);
                } else {
                    processedImage = originalImage;
                }

                // Сохраняем оригинальный размер изображения
                if (originalImageSize == null) {
                    originalImageSize = new Dimension(processedImage.getWidth(), processedImage.getHeight());
                }

                currentImage = processedImage;
                updateImageDisplay();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateImageDisplay() {
        if (currentImage != null) {
            BufferedImage displayImage = currentImage;

            // Если есть выделенная область, рисуем её
            if (selecting && startPoint != null && currentPoint != null) {
                displayImage = new BufferedImage(currentImage.getWidth(), currentImage.getHeight(), currentImage.getType());
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
        // Конвертируем BufferedImage в Mat
        OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
        Frame frame = new Java2DFrameConverter().convert(image);
        Mat mat = converter.convert(frame);

        // Конвертируем в grayscale
        Mat gray = new Mat();
        opencv_imgproc.cvtColor(mat, gray, opencv_imgproc.COLOR_BGR2GRAY);

        // Применяем контрастность и яркость
        Mat contrasted = new Mat();
        gray.convertTo(contrasted, -1, alpha, beta);

        // Конвертируем обратно в BufferedImage
        Frame processedFrame = converter.convert(contrasted);
        return new Java2DFrameConverter().getBufferedImage(processedFrame);
    }

    private void captureSelectedArea() {
        if (startPoint == null || currentPoint == null || currentImage == null) return;

        // Масштабируем координаты обратно к оригинальному размеру изображения
        double scaleX = (double) originalImageSize.width / imageLabel.getWidth();
        double scaleY = (double) originalImageSize.height / imageLabel.getHeight();

        int x = (int) (Math.min(startPoint.x, currentPoint.x) * scaleX);
        int y = (int) (Math.min(startPoint.y, currentPoint.y) * scaleY);
        int width = (int) (Math.abs(currentPoint.x - startPoint.x) * scaleX);
        int height = (int) (Math.abs(currentPoint.y - startPoint.y) * scaleY);

        if (width > 10 && height > 10) { // Минимальный размер области
            try {
                // Вырезаем выделенную область из оригинального изображения
                BufferedImage selectedArea = currentImage.getSubimage(x, y, width, height);

                // Помещаем в буфер обмена
                setClipboard(selectedArea);

                //
                // JOptionPane.showMessageDialog(this,"Область скопирована в буфер обмена!\nРазмер: " + width + "x" + height);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Ошибка при захвате области: " + e.getMessage());
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
        setTitle(String.format("Camera Monitor - Контраст: %.1f, Яркость: %.0f, Фильтры: %s",
                alpha, beta, filtersEnabled ? "ВКЛ" : "ВЫКЛ"));
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
}