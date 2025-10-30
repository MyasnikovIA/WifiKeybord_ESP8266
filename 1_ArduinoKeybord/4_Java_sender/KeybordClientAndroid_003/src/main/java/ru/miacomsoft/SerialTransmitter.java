package ru.miacomsoft;

import com.fazecast.jSerialComm.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static ru.miacomsoft.KeyCodes.KEY_CODES_RUS;
import static ru.miacomsoft.KeyCodes.isCyrillic;

// public static void main(String[] args) {
//     SwingUtilities.invokeLater(() -> {
//         try {
//             UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//         } catch (Exception e) {
//             e.printStackTrace();
//         }
//         new SerialTransmitter();
//     });
// }

public class SerialTransmitter extends JFrame {
    private JComboBox<String> portComboBox;
    private JTextArea messageTextArea;
    private JButton pauseButton;
    private JButton stopButton;
    private JButton startButton;
    private JLabel lineNumberLabel;

    private SerialPort port;
    private Thread transmissionThread;
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private AtomicBoolean isStopped = new AtomicBoolean(false);
    private boolean isEnglish = true;

    public SerialTransmitter() {
        setTitle("Serial Transmitter");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        initComponents();
        layoutComponents();
        refreshPorts();

        setVisible(true);
    }

    private void initComponents() {
        // ComboBox для портов
        portComboBox = new JComboBox<>();
        JButton refreshButton = new JButton("Обновить");
        refreshButton.addActionListener(e -> refreshPorts());

        // Текстовое поле для сообщения
        messageTextArea = new JTextArea();
        messageTextArea.setLineWrap(true);
        messageTextArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(messageTextArea);

        // Кнопки управления
        pauseButton = new JButton("Пауза");
        stopButton = new JButton("Стоп");
        startButton = new JButton("Старт");

        lineNumberLabel = new JLabel("Строка: 0");

        // Обработчики кнопок
        pauseButton.addActionListener(e -> togglePause());
        stopButton.addActionListener(e -> stopTransmission());
        startButton.addActionListener(e -> startTransmission());

        // Начальное состояние кнопок
        updateButtonStates(false);
    }

    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));

        // Панель с выбором порта
        JPanel portPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        portPanel.add(new JLabel("COM порт:"));
        portPanel.add(portComboBox);
        portPanel.add(new JButton("Обновить") {{
            addActionListener(e -> refreshPorts());
        }});

        // Панель с кнопками
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.add(startButton);
        buttonPanel.add(pauseButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(lineNumberLabel);

        // Основная компоновка
        add(portPanel, BorderLayout.NORTH);
        add(new JScrollPane(messageTextArea), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Отступы
        ((JComponent)getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private void refreshPorts() {
        portComboBox.removeAllItems();
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            portComboBox.addItem(port.getSystemPortName());
        }
    }

    private void startTransmission() {
        if (portComboBox.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(this, "Выберите COM порт!");
            return;
        }

        String message = messageTextArea.getText();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Введите текст для отправки!");
            return;
        }

        String portName = (String) portComboBox.getSelectedItem();
        port = SerialPort.getCommPort(portName);

        if (!port.openPort()) {
            JOptionPane.showMessageDialog(this, "Не удалось открыть порт: " + portName);
            return;
        }

        port.setComPortParameters(9600, 8, 1, 0);

        isStopped.set(false);
        isPaused.set(false);
        isEnglish = true;

        updateButtonStates(true);

        // Запуск потока передачи
        transmissionThread = new Thread(() -> {
            try {
                transmitMessage(message);
                messageTextArea.setText("");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                SwingUtilities.invokeLater(() -> {
                    updateButtonStates(false);
                    lineNumberLabel.setText("Строка: 0");
                    if (port != null && port.isOpen()) {
                        port.closePort();
                    }
                });
            }
        });

        transmissionThread.start();
    }

    private void transmitMessage(String message) throws InterruptedException {
        message = message.replaceAll("[\\p{C}&&[^\n]]", "");

        String[] lines= message.split("\n");
        int finalLineNum = 1;
        SwingUtilities.invokeLater(() -> lineNumberLabel.setText("Строка: 1 из  "+lines.length));
        for (char c : message.toCharArray()) {
            if (isStopped.get()) {
                break;
            }

            // Ожидание если пауза
            while (isPaused.get() && !isStopped.get()) {
                Thread.sleep(100);
            }

            if (isStopped.get()) {
                break;
            }

            System.out.println(c);

            if (c == 10) { // Home
                Thread.sleep(100);
                port.writeBytes(new byte[]{(byte) 32}, 1);
                Thread.sleep(100);
                finalLineNum+=1;
                int finalLineNum1 = finalLineNum;
                SwingUtilities.invokeLater(() -> lineNumberLabel.setText("Строка: " + finalLineNum1 +" из  "+lines.length));
            }

            boolean isRussian = isCyrillic(c);
            if (isRussian) {
                if (isEnglish) {
                    isEnglish = false;
                    Thread.sleep(200);
                    port.writeBytes(new byte[]{(byte) 134}, 1);
                    Thread.sleep(200);
                }
                if (KEY_CODES_RUS.containsKey(String.valueOf(c))) {
                    int symb = KEY_CODES_RUS.get(String.valueOf(c));
                    sendByte(port, symb);
                }
            } else {
                if (!isEnglish) {
                    isEnglish = true;
                    Thread.sleep(200);
                    port.writeBytes(new byte[]{(byte) 134}, 1);
                    Thread.sleep(200);
                }
                sendByte(port, c);
            }

            if (c == 10) { // Home
                Thread.sleep(300);
                port.writeBytes(new byte[]{(byte) 231}, 1);
                Thread.sleep(300);
            }
        }

        if (!isEnglish) {
            Thread.sleep(200);
            port.writeBytes(new byte[]{(byte) 134}, 1);
            Thread.sleep(200);
        }
    }

    private void togglePause() {
        isPaused.set(!isPaused.get());
        pauseButton.setText(isPaused.get() ? "Продолжить" : "Пауза");
    }

    private void stopTransmission() {
        isStopped.set(true);
        updateButtonStates(false);
    }

    private void updateButtonStates(boolean transmissionRunning) {
        startButton.setEnabled(!transmissionRunning);
        pauseButton.setEnabled(transmissionRunning);
        stopButton.setEnabled(transmissionRunning);

        if (!transmissionRunning) {
            pauseButton.setText("Пауза");
        }

        portComboBox.setEnabled(!transmissionRunning);
        messageTextArea.setEnabled(!transmissionRunning);
    }

    public static void sendByte(SerialPort port, int value) throws InterruptedException {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Value must be in range 0-255--" + value + "  ");
        }
        byte b = (byte) (value & 0xFF);
        port.writeBytes(new byte[]{b}, 1);
        Thread.sleep(100);
    }

}