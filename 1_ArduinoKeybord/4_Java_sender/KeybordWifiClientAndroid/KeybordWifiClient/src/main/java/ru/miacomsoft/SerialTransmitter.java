package ru.miacomsoft;

import com.fazecast.jSerialComm.SerialPort;
import java.io.IOException;
import java.io.OutputStream;

public class SerialTransmitter {
    private SerialPort serialPort;
    private OutputStream outputStream;
    private boolean connected = false;
    private String portName;
    private int baudRate;

    public SerialTransmitter(String portName, int baudRate) {
        this.portName = portName;
        this.baudRate = baudRate;
    }

    public void connect() throws IOException {
        if (connected) {
            disconnect();
        }

        serialPort = SerialPort.getCommPort(portName);
        serialPort.setBaudRate(baudRate);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(1);
        serialPort.setParity(SerialPort.NO_PARITY);
        serialPort.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);

        if (!serialPort.openPort()) {
            throw new IOException("Не удалось открыть порт " + portName);
        }

        // Даем время на инициализацию
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        outputStream = serialPort.getOutputStream();
        connected = true;
        System.out.println("Serial порт " + portName + " открыт на скорости " + baudRate);
    }

    public void disconnect() {
        connected = false;
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (serialPort != null) {
            serialPort.closePort();
        }
        outputStream = null;
        serialPort = null;
        System.out.println("Serial порт закрыт");
    }

    public void sendByte(int value) throws IOException {
        if (!connected || outputStream == null) {
            throw new IOException("Не подключено к последовательному порту");
        }
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Значение должно быть в диапазоне 0-255: " + value);
        }
        byte b = (byte) (value & 0xFF);
        outputStream.write(new byte[]{b});
        outputStream.flush();
        System.out.println("✓ Отправлен байт через Serial: " + value + " (0x" + Integer.toHexString(value) + ")");
    }

    public boolean isConnected() {
        return connected;
    }

    public String getConnectionInfo() {
        return portName + " @ " + baudRate + " бод";
    }

    // Статический метод для получения списка доступных COM-портов
    public static String[] getAvailablePorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        String[] portNames = new String[ports.length];
        for (int i = 0; i < ports.length; i++) {
            portNames[i] = ports[i].getSystemPortName();
        }
        return portNames;
    }
}