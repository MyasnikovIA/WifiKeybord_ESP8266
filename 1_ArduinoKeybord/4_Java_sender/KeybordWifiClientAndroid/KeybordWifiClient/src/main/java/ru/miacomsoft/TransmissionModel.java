package ru.miacomsoft;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static ru.miacomsoft.KeyCodes.KEY_CODES_RUS;
import static ru.miacomsoft.KeyCodes.isCyrillic;

public class TransmissionModel {
    // Константы задержек
    public static final int BASE_DELAY = 100;
    public static final int SWITCH_LAYOUT_DELAY = 400;
    public static final int SPECIAL_CHAR_DELAY = 300;
    public static final int VIRTUAL_KEY_DELAY = 50;
    public static final int MIN_DELAY = 50;
    public static final int MAX_DELAY = 500;
    public static final int RANDOM_DELAY_RANGE = 300;

    // Транспортные объекты
    private Socket wifiSocket;
    private OutputStream wifiOutputStream;
    private SerialTransmitter serialTransmitter;

    // Состояния
    private boolean isWifiMode = true;
    private boolean isConnected = false;
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private AtomicBoolean isStopped = new AtomicBoolean(false);
    private AtomicBoolean isVirtualKeyboardActive = new AtomicBoolean(false);
    private boolean isEnglish = true;
    private boolean wasTransmissionPausedByVirtualKeyboard = false;
    private AtomicBoolean isDirectKeyboardActive = new AtomicBoolean(false);
    private boolean ideMode = false; // Флаг для IDE режима

    // Поток передачи
    private Thread transmissionThread;

    // Данные для передачи
    private String currentMessage;
    private int currentTransmissionPosition = 0;
    private int totalCharactersToSend = 0;
    private long transmissionStartTime = 0;
    private long lastUpdateTime = 0;

    // GUI Callbacks
    private Runnable onStatusUpdate;
    private Runnable onConnectionStateChange;
    private Runnable onTransmissionProgress;
    private Runnable onLayoutChanged;
    private Runnable onVirtualKeyboardStateChange;
    private java.util.function.Consumer<String> onError;
    private java.util.function.Consumer<Integer> onLineNumberUpdate;
    private java.util.function.BiConsumer<Integer, Integer> onCharacterHighlight;
    private java.util.function.Consumer<String> onTimeRemainingUpdate;
    private Runnable onAllTextReset;
    private java.util.function.Consumer<Boolean> onButtonStatesUpdate;
    private Runnable onLanguageSwitch;

    public TransmissionModel() {
    }

    // Установка callback'ов для обновления GUI
    public void setOnStatusUpdate(Runnable callback) { this.onStatusUpdate = callback; }
    public void setOnConnectionStateChange(Runnable callback) { this.onConnectionStateChange = callback; }
    public void setOnTransmissionProgress(Runnable callback) { this.onTransmissionProgress = callback; }
    public void setOnLayoutChanged(Runnable callback) { this.onLayoutChanged = callback; }
    public void setOnVirtualKeyboardStateChange(Runnable callback) { this.onVirtualKeyboardStateChange = callback; }
    public void setOnError(java.util.function.Consumer<String> callback) { this.onError = callback; }
    public void setOnLineNumberUpdate(java.util.function.Consumer<Integer> callback) { this.onLineNumberUpdate = callback; }
    public void setOnCharacterHighlight(java.util.function.BiConsumer<Integer, Integer> callback) { this.onCharacterHighlight = callback; }
    public void setOnTimeRemainingUpdate(java.util.function.Consumer<String> callback) { this.onTimeRemainingUpdate = callback; }
    public void setOnAllTextReset(Runnable callback) { this.onAllTextReset = callback; }
    public void setOnButtonStatesUpdate(java.util.function.Consumer<Boolean> callback) { this.onButtonStatesUpdate = callback; }
    public void setOnLanguageSwitch(Runnable callback) { this.onLanguageSwitch = callback; }

    // Геттеры/сеттеры для состояний
    public boolean isWifiMode() { return isWifiMode; }
    public boolean isConnected() {
        if (isWifiMode) {
            return isConnected && wifiSocket != null && !wifiSocket.isClosed();
        } else {
            return isConnected && serialTransmitter != null && serialTransmitter.isConnected();
        }
    }
    public boolean isPaused() { return isPaused.get(); }
    public boolean isStopped() { return isStopped.get(); }
    public boolean isVirtualKeyboardActive() { return isVirtualKeyboardActive.get(); }
    public boolean isDirectKeyboardActive() { return isDirectKeyboardActive.get(); }
    public boolean isEnglish() { return isEnglish; }
    public boolean isIdeMode() { return ideMode; }
    public boolean wasTransmissionPausedByVirtualKeyboard() { return wasTransmissionPausedByVirtualKeyboard; }
    public Thread getTransmissionThread() { return transmissionThread; }
    public String getCurrentMessage() { return currentMessage; }
    public int getCurrentTransmissionPosition() { return currentTransmissionPosition; }

    public void setEnglish(boolean english) {
        this.isEnglish = english;
    }

    public void setIdeMode(boolean enabled) {
        this.ideMode = enabled;
        System.out.println("IDE режим " + (enabled ? "включен" : "выключен"));
    }

    // Методы для управления режимами
    public void switchToWifiMode() {
        if (isWifiMode) return;
        if (isConnected()) disconnect();
        isWifiMode = true;
        if (onStatusUpdate != null) onStatusUpdate.run();
        if (onConnectionStateChange != null) onConnectionStateChange.run();
    }

    public void switchToSerialMode() {
        if (!isWifiMode) return;
        if (isConnected()) disconnect();
        isWifiMode = false;
        if (onStatusUpdate != null) onStatusUpdate.run();
        if (onConnectionStateChange != null) onConnectionStateChange.run();
    }

    // Методы подключения
    public void connectWifi(String host, int port) throws IOException {
        if (isConnected()) disconnect();
        wifiSocket = new Socket(host, port);
        wifiOutputStream = wifiSocket.getOutputStream();
        isConnected = true;
        if (onConnectionStateChange != null) onConnectionStateChange.run();
    }

    public void connectSerial(String portName, int baudRate) throws IOException {
        if (isConnected()) disconnect();
        serialTransmitter = new SerialTransmitter(portName, baudRate);
        serialTransmitter.connect();
        isConnected = true;
        if (onConnectionStateChange != null) onConnectionStateChange.run();
    }

    public void disconnect() {
        if (isWifiMode) {
            disconnectWifi();
        } else {
            disconnectSerial();
        }

        isConnected = false;
        isStopped.set(true);
        isVirtualKeyboardActive.set(false);
        wasTransmissionPausedByVirtualKeyboard = false;

        if (transmissionThread != null && transmissionThread.isAlive()) {
            transmissionThread.interrupt();
        }

        if (onConnectionStateChange != null) onConnectionStateChange.run();
        if (onVirtualKeyboardStateChange != null) onVirtualKeyboardStateChange.run();
        if (onAllTextReset != null) onAllTextReset.run();
    }

    private void disconnectWifi() {
        try {
            if (wifiOutputStream != null) wifiOutputStream.close();
            if (wifiSocket != null) wifiSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        wifiOutputStream = null;
        wifiSocket = null;
    }

    private void disconnectSerial() {
        if (serialTransmitter != null) {
            serialTransmitter.disconnect();
            serialTransmitter = null;
        }
    }

    // Отправка байта
    public void sendByte(int value) throws IOException {
        if (isWifiMode) {
            sendByteWifi(value);
        } else {
            sendByteSerial(value);
        }

        int delay = getRandomDelay(BASE_DELAY);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Transmission interrupted", e);
        }
    }

    private void sendByteWifi(int value) throws IOException {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Value must be in range 0-255--" + value);
        }
        byte b = (byte) (value & 0xFF);
        if (wifiOutputStream != null) {
            wifiOutputStream.write(new byte[]{b});
            wifiOutputStream.flush();
            System.out.println("✓✓✓ ОТПРАВЛЕН БАЙТ (WiFi): " + value);
        } else {
            throw new IOException("WiFi outputStream is NULL!");
        }
    }

    private void sendByteSerial(int value) throws IOException {
        if (serialTransmitter == null || !serialTransmitter.isConnected()) {
            throw new IOException("Serial transmitter not connected!");
        }
        serialTransmitter.sendByte(value);
    }

    // Переключение языка через Scroll Lock
    public void switchLanguage() throws IOException, InterruptedException {
        if (!isConnected()) return;

        // Отправляем код переключения раскладки
        if (isEnglish) {
            sendByte(126);  // Переключение на русский (Ё)
        } else {
            sendByte(96);   // Переключение на английский (ё)
        }

        isEnglish = !isEnglish;

        // Уведомляем GUI об изменении
        SwingUtilities.invokeLater(() -> {
            if (onLayoutChanged != null) onLayoutChanged.run();
            if (onLanguageSwitch != null) onLanguageSwitch.run();
        });

        Thread.sleep(SWITCH_LAYOUT_DELAY);
    }

    public void toggleLanguageFromGUI() {
        if (!isConnected()) {
            if (onError != null) onError.accept("Нет подключения!");
            return;
        }

        new Thread(() -> {
            try {
                switchLanguage();
            } catch (Exception e) {
                if (onError != null) onError.accept("Ошибка переключения языка: " + e.getMessage());
            }
        }).start();
    }

    // Управление передачей
    public void startTransmission(String message) {
        if (!isConnected()) {
            if (onError != null) onError.accept("Сначала подключитесь!");
            return;
        }

        if (message.isEmpty()) {
            if (onError != null) onError.accept("Введите текст для отправки!");
            return;
        }

        if (isVirtualKeyboardActive.get()) {
            if (onError != null) onError.accept("Виртуальная клавиатура активна. Нажатия клавиш будут игнорироваться.");
            return;
        }

        isStopped.set(false);
        isPaused.set(false);
        isEnglish = true;
        currentMessage = message;
        currentTransmissionPosition = 0;
        totalCharactersToSend = message.replaceAll("[\\p{C}&&[^\n]]", "").length();
        transmissionStartTime = System.currentTimeMillis();
        lastUpdateTime = 0;

        if (onLayoutChanged != null) onLayoutChanged.run();
        if (onAllTextReset != null) onAllTextReset.run();
        if (onButtonStatesUpdate != null) onButtonStatesUpdate.accept(true);

        transmissionThread = new Thread(() -> {
            try {
                transmitMessage(message);
                SwingUtilities.invokeLater(() -> {
                    currentMessage = null;
                    currentTransmissionPosition = 0;
                    if (onTimeRemainingUpdate != null) onTimeRemainingUpdate.accept("Осталось: 00:00");
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                if (onError != null) onError.accept("Ошибка передачи: " + e.getMessage());
                disconnect();
            } finally {
                SwingUtilities.invokeLater(() -> {
                    if (onButtonStatesUpdate != null) onButtonStatesUpdate.accept(false);
                    if (onLineNumberUpdate != null) onLineNumberUpdate.accept(0);
                    currentTransmissionPosition = 0;
                });
            }
        });

        transmissionThread.start();
    }

    private void transmitMessage(String message) throws InterruptedException, IOException {
        message = message.replaceAll("[\\p{C}&&[^\n]]", "");
        message = message.replaceAll("(?m)^[ \\t]+$", "");
        String[] lines = message.split("\n");
        int finalLineNum = 1;
        int charsSent = 0;

        SwingUtilities.invokeLater(() -> {
            if (onLineNumberUpdate != null) onLineNumberUpdate.accept(1);
        });

        for (char c : message.toCharArray()) {
            if (isStopped.get()) break;

            while (isPaused.get() && !isStopped.get()) {
                Thread.sleep(100);
            }

            if (isStopped.get()) break;

            System.out.println("Отправка символа: " + c + " (IDE mode: " + ideMode + ")");

            if (c == 10) {
                Thread.sleep(BASE_DELAY);
                sendByte(32);
                Thread.sleep(BASE_DELAY);
                finalLineNum += 1;
                int finalLineNum1 = finalLineNum;
                SwingUtilities.invokeLater(() -> {
                    if (onLineNumberUpdate != null) onLineNumberUpdate.accept(finalLineNum1);
                });
            }

            if (isLayoutSwitchChar(c)) {
                if (c == 'ё' || c == 'Ё') {
                    handleYoChar(c);
                } else {
                    sendByte(c);
                    isEnglish = !isEnglish;
                    SwingUtilities.invokeLater(() -> {
                        if (onLayoutChanged != null) onLayoutChanged.run();
                    });
                    Thread.sleep(SWITCH_LAYOUT_DELAY);
                }
                continue;
            }

            // Обработка символов с учетом IDE режима
            if (ideMode && isSpecialCharacterForIDE(c)) {
                boolean isRussian = isCyrillic(c);
                if (isRussian) {
                    if (isEnglish) switchLayout();
                    sendRussianChar(c);
                } else {
                    if (!isEnglish) switchLayout();
                    sendByte(c);
                }

                Thread.sleep(SPECIAL_CHAR_DELAY);
                sendByte(212);
                Thread.sleep(SPECIAL_CHAR_DELAY);
            } else {
                boolean isRussian = isCyrillic(c);
                if (isRussian) {
                    if (isEnglish) switchLayout();
                    sendRussianChar(c);
                } else {
                    if (!isEnglish) switchLayout();
                    sendByte(c);
                }
            }

            final int position = currentTransmissionPosition;
            SwingUtilities.invokeLater(() -> {
                if (onCharacterHighlight != null) onCharacterHighlight.accept(position, 1);
            });
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
            if (onTimeRemainingUpdate != null) onTimeRemainingUpdate.accept("Осталось: 00:00");
        });
    }

    public void togglePause() {
        if (!isConnected() || transmissionThread == null || !transmissionThread.isAlive()) {
            return;
        }

        if (isPaused.get() && isVirtualKeyboardActive.get()) {
            setVirtualKeyboardActive(false);
        }

        isPaused.set(!isPaused.get());

        SwingUtilities.invokeLater(() -> {
            if (onStatusUpdate != null) onStatusUpdate.run();
        });
    }

    public void stopTransmission() {
        isStopped.set(true);
        if (onButtonStatesUpdate != null) onButtonStatesUpdate.accept(false);
    }

    // Виртуальная клавиатура
    public void setVirtualKeyboardActive(boolean active) {
        if (!isConnected()) {
            if (onError != null) onError.accept("Сначала подключитесь!");
            return;
        }

        isVirtualKeyboardActive.set(active);

        if (active) {
            if (transmissionThread != null && transmissionThread.isAlive() && !isPaused.get()) {
                wasTransmissionPausedByVirtualKeyboard = true;
                isPaused.set(true);
                SwingUtilities.invokeLater(() -> {
                    if (onStatusUpdate != null) onStatusUpdate.run();
                });
            }
        } else {
            if (transmissionThread != null && transmissionThread.isAlive() && isPaused.get() && wasTransmissionPausedByVirtualKeyboard) {
                wasTransmissionPausedByVirtualKeyboard = false;
            }
        }

        SwingUtilities.invokeLater(() -> {
            if (onVirtualKeyboardStateChange != null) onVirtualKeyboardStateChange.run();
            if (onButtonStatesUpdate != null) onButtonStatesUpdate.accept(transmissionThread != null && transmissionThread.isAlive());
        });
    }

    public void setDirectKeyboardActive(boolean active) {
        if (!isConnected()) {
            if (onError != null) onError.accept("Сначала подключитесь!");
            return;
        }

        if (active && !isVirtualKeyboardActive.get()) {
            if (onError != null) onError.accept("Для работы прямой клавиатуры необходимо включить виртуальную клавиатуру.");
            return;
        }

        isDirectKeyboardActive.set(active);
        if (onVirtualKeyboardStateChange != null) onVirtualKeyboardStateChange.run();
    }

    public void toggleKeyboardLayout() throws IOException, InterruptedException {
        if (!isConnected()) return;

        if (isEnglish) {
            sendByte(126);
        } else {
            sendByte(96);
        }

        isEnglish = !isEnglish;
        SwingUtilities.invokeLater(() -> {
            if (onLayoutChanged != null) onLayoutChanged.run();
        });

        Thread.sleep(SWITCH_LAYOUT_DELAY);
    }

    // Обработка виртуальных клавиш
    public void handleVirtualKeyEvent(int keyCode, char keyChar, boolean isShiftDown, boolean isPressed, boolean isActionKey) throws IOException, InterruptedException {
        if (isActionKey && isPressed) {
            handleActionKey(keyCode);
        } else if (!isActionKey && !isPressed && keyChar >= 32) {
            handleCharacter(keyChar, isShiftDown);
        }
    }

    private void handleActionKey(int keyCode) throws IOException, InterruptedException {
        switch (keyCode) {
            case KeyEvent.VK_ENTER: sendByte(176); break;
            case KeyEvent.VK_BACK_SPACE: sendByte(178); break;
            case KeyEvent.VK_TAB: sendByte(179); break;
            case KeyEvent.VK_SPACE: sendByte(180); break;
            case KeyEvent.VK_CAPS_LOCK:
                sendByte(134);
                isEnglish = !isEnglish;
                SwingUtilities.invokeLater(() -> {
                    if (onLayoutChanged != null) onLayoutChanged.run();
                    if (onLanguageSwitch != null) onLanguageSwitch.run();
                });
                break;
            case KeyEvent.VK_SCROLL_LOCK:
                switchLanguage();
                break;
            case KeyEvent.VK_HOME: sendByte(210); break;
            case KeyEvent.VK_END: sendByte(213); break;
            case KeyEvent.VK_PAGE_UP: sendByte(211); break;
            case KeyEvent.VK_PAGE_DOWN: sendByte(214); break;
            case KeyEvent.VK_UP: sendByte(218); break;
            case KeyEvent.VK_DOWN: sendByte(217); break;
            case KeyEvent.VK_LEFT: sendByte(216); break;
            case KeyEvent.VK_RIGHT: sendByte(215); break;
            case KeyEvent.VK_INSERT: sendByte(209); break;
            case KeyEvent.VK_DELETE: sendByte(212); break;
            case KeyEvent.VK_F1: sendByte(194); break;
            case KeyEvent.VK_F2: sendByte(195); break;
            case KeyEvent.VK_F3: sendByte(196); break;
            case KeyEvent.VK_F4: sendByte(197); break;
            case KeyEvent.VK_F5: sendByte(198); break;
            case KeyEvent.VK_F6: sendByte(199); break;
            case KeyEvent.VK_F7: sendByte(200); break;
            case KeyEvent.VK_F8: sendByte(201); break;
            case KeyEvent.VK_F9: sendByte(202); break;
            case KeyEvent.VK_F10: sendByte(203); break;
            case KeyEvent.VK_F11: sendByte(204); break;
            case KeyEvent.VK_F12: sendByte(205); break;
            case KeyEvent.VK_NUM_LOCK: sendByte(219); break;
        }
    }

    private void handleCharacter(char keyChar, boolean isShift) throws IOException, InterruptedException {
        if (keyChar < 32) {
            handleControlCharacter(keyChar);
            return;
        }

        if (isLayoutSwitchChar(keyChar)) {
            if (keyChar == 'ё' || keyChar == 'Ё') {
                handleYoChar(keyChar);
            } else {
                sendByte(keyChar);
                isEnglish = !isEnglish;
                SwingUtilities.invokeLater(() -> {
                    if (onLayoutChanged != null) onLayoutChanged.run();
                });
                Thread.sleep(SWITCH_LAYOUT_DELAY);
            }
            return;
        }

        if (ideMode && isSpecialCharacterForIDE(keyChar)) {
            boolean isRussian = isCyrillic(keyChar);
            if (isRussian) {
                if (isEnglish) switchLayout();
                sendRussianChar(keyChar);
            } else {
                if (!isEnglish) switchLayout();
                char charToSend = isShift && Character.isLetter(keyChar) ? Character.toUpperCase(keyChar) : keyChar;
                if (charToSend < 128) sendByte(charToSend);
            }

            Thread.sleep(SPECIAL_CHAR_DELAY);
            sendByte(212);
            Thread.sleep(SPECIAL_CHAR_DELAY);
        } else {
            boolean isRussian = isCyrillic(keyChar);
            if (isRussian) {
                if (isEnglish) switchLayout();
                sendRussianChar(keyChar);
            } else {
                if (!isEnglish) switchLayout();
                char charToSend = isShift && Character.isLetter(keyChar) ? Character.toUpperCase(keyChar) : keyChar;
                if (charToSend < 128) sendByte(charToSend);
            }
        }
    }

    private void handleControlCharacter(char controlChar) throws IOException {
        switch ((int)controlChar) {
            case 8: sendByte(178); break;
            case 9: sendByte(179); break;
            case 10: case 13: sendByte(176); break;
            case 127: sendByte(212); break;
        }
    }

    // Вспомогательные методы
    private void switchLayout() throws IOException, InterruptedException {
        sendByte(96);
        isEnglish = !isEnglish;
        SwingUtilities.invokeLater(() -> {
            if (onLayoutChanged != null) onLayoutChanged.run();
        });
        Thread.sleep(SWITCH_LAYOUT_DELAY);
    }

    private void handleYoChar(char yoChar) throws IOException, InterruptedException {
        if (yoChar == 'ё') {
            sendByte(96);
        } else if (yoChar == 'Ё') {
            sendByte(126);
        }

        if (isEnglish) {
            isEnglish = false;
            SwingUtilities.invokeLater(() -> {
                if (onLayoutChanged != null) onLayoutChanged.run();
            });
        }
        Thread.sleep(SWITCH_LAYOUT_DELAY);
    }

    private void sendRussianChar(char russianChar) throws IOException {
        String charStr = String.valueOf(russianChar);
        if (KEY_CODES_RUS.containsKey(charStr)) {
            int code = KEY_CODES_RUS.get(charStr);
            sendByte(code);
            System.out.println("Отправлен русский символ '" + russianChar + "' как код: " + code);
        }
    }

    private boolean isLayoutSwitchChar(char c) {
        return c == '~' || c == 'Ё' || c == 'ё' || c == '`';
    }

    private boolean isSpecialCharacterForIDE(char c) {
        return c == '{' || c == '}' || c == '"' || c == '\'' ||
                c == '[' || c == ']' || c == '(' || c == ')';
    }

    private void updateRemainingTime(int charsSent, int totalChars) {
        if (totalChars <= 0 || charsSent <= 0) {
            SwingUtilities.invokeLater(() -> {
                if (onTimeRemainingUpdate != null) onTimeRemainingUpdate.accept("Осталось: --:--");
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
            if (onTimeRemainingUpdate != null) onTimeRemainingUpdate.accept("Осталось: " + timeStr);
        });

        lastUpdateTime = currentTime;
    }

    private int getRandomDelay(int baseDelay) {
        int randomOffset = (int) (Math.random() * RANDOM_DELAY_RANGE) - (RANDOM_DELAY_RANGE / 2);
        int delay = baseDelay + randomOffset;
        if (delay < MIN_DELAY) delay = MIN_DELAY;
        if (delay > MAX_DELAY) delay = MAX_DELAY;
        return delay;
    }

    // Отправка кастомных данных
    public void sendCustomText(String text) throws IOException, InterruptedException {
        if (!isConnected()) throw new IllegalStateException("Не подключено");

        boolean wasEnglish = isEnglish;
        isEnglish = true;
        SwingUtilities.invokeLater(() -> { if (onLayoutChanged != null) onLayoutChanged.run(); });

        try {
            for (char c : text.toCharArray()) {
                if (isStopped.get()) break;

                if (isLayoutSwitchChar(c)) {
                    if (c == 'ё' || c == 'Ё') {
                        handleYoChar(c);
                    } else {
                        sendByte(c);
                        isEnglish = !isEnglish;
                        SwingUtilities.invokeLater(() -> { if (onLayoutChanged != null) onLayoutChanged.run(); });
                        Thread.sleep(SWITCH_LAYOUT_DELAY / 2);
                    }
                    continue;
                }

                if (ideMode && isSpecialCharacterForIDE(c)) {
                    boolean isRussian = isCyrillic(c);
                    if (isRussian) {
                        if (isEnglish) switchLayout();
                        sendRussianChar(c);
                    } else {
                        if (!isEnglish) switchLayout();
                        sendByte(c);
                    }

                    Thread.sleep(SPECIAL_CHAR_DELAY);
                    sendByte(212);
                    Thread.sleep(SPECIAL_CHAR_DELAY);
                } else {
                    boolean isRussian = isCyrillic(c);
                    if (isRussian) {
                        if (isEnglish) switchLayout();
                        sendRussianChar(c);
                    } else {
                        if (!isEnglish) switchLayout();
                        sendByte(c);
                    }
                }
                Thread.sleep(BASE_DELAY);
            }

            if (!isEnglish && wasEnglish) {
                Thread.sleep(SWITCH_LAYOUT_DELAY / 2);
                switchLayout();
            }

            isEnglish = wasEnglish;
            SwingUtilities.invokeLater(() -> { if (onLayoutChanged != null) onLayoutChanged.run(); });
        } catch (Exception e) {
            isEnglish = wasEnglish;
            SwingUtilities.invokeLater(() -> { if (onLayoutChanged != null) onLayoutChanged.run(); });
            throw e;
        }
    }

    public void sendCustomBytes(byte[] bytes) throws IOException, InterruptedException {
        if (!isConnected()) throw new IllegalStateException("Не подключено");

        for (byte b : bytes) {
            sendByte(b & 0xFF);
            Thread.sleep(BASE_DELAY);
        }
    }

    public void sendSingleByte(int value) throws IOException {
        if (!isConnected()) throw new IllegalStateException("Не подключено");
        sendByte(value);
    }
}