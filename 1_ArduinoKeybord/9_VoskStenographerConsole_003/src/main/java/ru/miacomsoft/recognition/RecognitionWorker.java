package ru.miacomsoft.recognition;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import ru.miacomsoft.TextPunctuator;
import ru.miacomsoft.audio.*;
import ru.miacomsoft.ui.ConsolePanel;
import ru.miacomsoft.ui.TranscriptPanel;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecognitionWorker {
    private static final int SAMPLE_RATE = 16000;
    private static final int CHUNK_SIZE = 4000;

    private String modelPath;
    private TranscriptPanel panel;
    private ConsolePanel consolePanel;
    private boolean isSystemAudio;
    private AudioDeviceInfo device;

    private Model voskModel;
    private Recognizer recognizer;
    private BlockingQueue<byte[]> audioQueue;
    private AtomicBoolean isRunning;
    private Thread recognitionThread;
    private Thread captureThread;
    private AudioCapture audioCapture;
    private SystemAudioCapture systemAudioCapture;
    private WindowsSystemAudioCapture windowsSystemAudioCapture;
    private int totalChunksProcessed = 0;
    private int totalFinalResults = 0;

    public RecognitionWorker(String modelPath, int sampleRate, TranscriptPanel panel,
                             boolean isSystemAudio, AudioDeviceInfo device, ConsolePanel consolePanel) {
        this.modelPath = modelPath;
        this.panel = panel;
        this.consolePanel = consolePanel;
        this.isSystemAudio = isSystemAudio;
        this.device = device;
        this.audioQueue = new LinkedBlockingQueue<>(2000);
        this.isRunning = new AtomicBoolean(false);
    }

    public void start() {
        if (consolePanel != null) {
            consolePanel.printInfo("Запуск RecognitionWorker для " +
                    (isSystemAudio ? "системного звука" : "устройства: " + device.getName()));
        }

        if (!loadModel()) {
            if (consolePanel != null) {
                consolePanel.printError("Не удалось загрузить модель");
            }
            return;
        }

        isRunning.set(true);
        startRecognitionThread();

        if (isSystemAudio) {
            startSystemAudioCapture();
        } else {
            startMicrophoneCapture();
        }
    }

    private boolean loadModel() {
        try {
            if (consolePanel != null) {
                consolePanel.printInfo("Загрузка модели Vosk из: " + modelPath);
            }
            voskModel = new Model(modelPath);
            recognizer = new Recognizer(voskModel, SAMPLE_RATE);
            recognizer.setWords(true);
            if (consolePanel != null) {
                consolePanel.printSuccess("Модель Vosk успешно загружена");
            }
            return true;
        } catch (Exception e) {
            if (consolePanel != null) {
                consolePanel.printError("Ошибка загрузки модели: " + e.getMessage());
            }
            e.printStackTrace();
            return false;
        }
    }

    private void startRecognitionThread() {
        recognitionThread = new Thread(() -> {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bufferPos = 0;
            String lastPartial = "";
            int chunkCount = 0;

            while (isRunning.get()) {
                try {
                    byte[] data = audioQueue.poll(50, TimeUnit.MILLISECONDS);
                    if (data != null && data.length > 0) {
                        // Проверка первых байт для отладки
                        if (consolePanel != null && chunkCount == 1) {
                            StringBuilder hex = new StringBuilder();
                            for (int i = 0; i < Math.min(16, data.length); i++) {
                                hex.append(String.format("%02X ", data[i]));
                            }
                            consolePanel.printDebug("Первые байты аудио: " + hex.toString());
                        }
                        chunkCount++;
                        if (chunkCount % 100 == 0 && consolePanel != null) {
                            consolePanel.printDebug("Получено " + chunkCount + " аудио-чанков, размер очереди: " + audioQueue.size());
                        }

                        for (byte b : data) {
                            buffer[bufferPos++] = b;
                            if (bufferPos >= CHUNK_SIZE) {
                                totalChunksProcessed++;

                                if (consolePanel != null && totalChunksProcessed % 50 == 0) {
                                    consolePanel.printDebug("Обработано " + totalChunksProcessed + " чанков по " + CHUNK_SIZE + " байт");
                                }

                                if (recognizer.acceptWaveForm(buffer, bufferPos)) {
                                    String result = recognizer.getResult();
                                    if (consolePanel != null) {
                                        consolePanel.printDebug("acceptWaveForm=true, результат: " + result);
                                    }
                                    if (result != null && !result.isEmpty()) {
                                        String text = extractTextFromJson(result);
                                        if (!text.isEmpty()) {
                                            totalFinalResults++;
                                            String timestamp = LocalDateTime.now()
                                                    .format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                                            text = TextPunctuator.addPunctuation(text);
                                            text = TextPunctuator.capitalizeSentences(text);

                                            if (consolePanel != null) {
                                                consolePanel.printRecognition("Распознано: " + text);
                                            }
                                            panel.addFinalText(text, timestamp);
                                            lastPartial = "";
                                        }
                                    }
                                } else {
                                    String partial = recognizer.getPartialResult();
                                    if (partial != null && !partial.isEmpty()) {
                                        String partialText = extractPartialFromJson(partial);
                                        if (!partialText.isEmpty() && !partialText.equals(lastPartial)) {
                                            lastPartial = partialText;
                                            partialText = TextPunctuator.addPunctuation(partialText);

                                            if (consolePanel != null && partialText.length() > 0) {
                                                consolePanel.printDebug("Частичный результат: " + partialText);
                                            }
                                            panel.addPartialText(partialText);
                                        }
                                    }
                                }
                                bufferPos = 0;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        recognitionThread.setDaemon(true);
        recognitionThread.start();
    }

    private void startMicrophoneCapture() {
        audioCapture = new AudioCapture(SAMPLE_RATE);
        audioCapture.setConsolePanel(consolePanel);

        // Используем AudioCaptureCallback вместо внутреннего интерфейса
        AudioCaptureCallback callback = data -> {
            if (isRunning.get() && data != null && data.length > 0) {
                try {
                    if (!audioQueue.offer(data, 10, TimeUnit.MILLISECONDS)) {
                        if (consolePanel != null) {
                            consolePanel.printWarning("Очередь аудио переполнена, данные потеряны");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        captureThread = new Thread(() -> {
            if (consolePanel != null) {
                consolePanel.printInfo("Запуск захвата с микрофона, устройство: " + device.getId());
            }
            audioCapture.startCapture(device.getId(), callback);
        });
        captureThread.setDaemon(true);
        captureThread.start();
    }

    private void startSystemAudioCapture() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            windowsSystemAudioCapture = new WindowsSystemAudioCapture(SAMPLE_RATE);
            windowsSystemAudioCapture.setConsolePanel(consolePanel);

            List<AudioDeviceInfo> systemDevices = windowsSystemAudioCapture.listSystemAudioDevices();

            if (systemDevices.isEmpty()) {
                if (consolePanel != null) {
                    consolePanel.printError("═══════════════════════════════════════════════════════════");
                    consolePanel.printError("  Stereo Mix не найден!");
                    consolePanel.printError("═══════════════════════════════════════════════════════════");
                    consolePanel.printError("  Решения:");
                    consolePanel.printError("  1. Включите Stereo Mix в настройках звука Windows:");
                    consolePanel.printError("     - ПКМ на динамик в трее → Звуки → Запись");
                    consolePanel.printError("     - ПКМ на пустом месте → Показать отключенные устройства");
                    consolePanel.printError("     - Включите Stereo Mix или 'Что слышно'");
                    consolePanel.printError("  2. Установите Virtual Audio Cable");
                    consolePanel.printError("  3. Используйте обычный микрофон (выберите в правой панели)");
                    consolePanel.printError("═══════════════════════════════════════════════════════════");

                    List<AudioDeviceInfo> allDevices = windowsSystemAudioCapture.listAllCaptureDevices();
                    if (!allDevices.isEmpty()) {
                        consolePanel.printInfo("Доступные устройства для захвата:");
                        for (AudioDeviceInfo dev : allDevices) {
                            consolePanel.printInfo("  • " + dev.getName());
                        }
                    }
                }
                return;
            }

            AudioDeviceInfo selectedDevice = systemDevices.get(0);
            if (consolePanel != null) {
                consolePanel.printSuccess("Найден Stereo Mix: " + selectedDevice.getName());
                consolePanel.printInfo("Захват системного звука с: " + selectedDevice.getName());
            }

            AudioCaptureCallback callback = data -> {
                if (isRunning.get() && data != null && data.length > 0) {
                    try {
                        if (!audioQueue.offer(data, 10, TimeUnit.MILLISECONDS)) {
                            if (consolePanel != null && Math.random() < 0.01) {
                                consolePanel.printWarning("Очередь аудио переполнена");
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            };

            captureThread = new Thread(() -> {
                windowsSystemAudioCapture.startCapture(selectedDevice, callback);
            });
            captureThread.setDaemon(true);
            captureThread.start();

        } else {
            startLinuxSystemAudioCapture();
        }
    }

    private void startLinuxSystemAudioCapture() {
        String monitorName = SystemAudioCapture.getDefaultMonitor();
        if (consolePanel != null) {
            consolePanel.printWarning("═══════════════════════════════════════════════════════════");
            consolePanel.printWarning("  ВНИМАНИЕ! Захват системного звука включает:");
            consolePanel.printWarning("  - Звук из видео/музыки/приложений");
            consolePanel.printWarning("  - Звук микрофона, если он слышен в динамиках!");
            consolePanel.printWarning("═══════════════════════════════════════════════════════════");
        }

        if (monitorName == null) {
            if (consolePanel != null) {
                consolePanel.printWarning("Монитор по умолчанию не найден, создаем loopback...");
            }
            systemAudioCapture = new SystemAudioCapture();
            systemAudioCapture.setConsolePanel(consolePanel);
            systemAudioCapture.createLoopback();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            monitorName = SystemAudioCapture.getDefaultMonitor();
            if (consolePanel != null && monitorName != null) {
                consolePanel.printSuccess("Loopback создан, монитор: " + monitorName);
            }
        }

        if (monitorName == null) {
            if (consolePanel != null) {
                consolePanel.printError("Не найден системный монитор");
            }
            return;
        }

        if (systemAudioCapture == null) {
            systemAudioCapture = new SystemAudioCapture();
            systemAudioCapture.setConsolePanel(consolePanel);
        }

        AudioCaptureCallback callback = data -> {
            if (isRunning.get() && data != null && data.length > 0) {
                try {
                    if (!audioQueue.offer(data, 10, TimeUnit.MILLISECONDS)) {
                        // Очередь переполнена
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        final String finalMonitorName = monitorName;

        captureThread = new Thread(() -> {
            if (consolePanel != null) {
                consolePanel.printSuccess("Начинаем захват системного звука с: " + finalMonitorName);
            }
            systemAudioCapture.startCapture(finalMonitorName, callback);
        });
        captureThread.setDaemon(true);
        captureThread.start();
    }

    public void stop() {
        if (consolePanel != null) {
            consolePanel.printInfo("Остановка RecognitionWorker...");
        }
        isRunning.set(false);

        if (audioCapture != null) {
            audioCapture.stopCapture();
        }
        if (systemAudioCapture != null) {
            systemAudioCapture.stopCapture();
        }
        if (windowsSystemAudioCapture != null) {
            windowsSystemAudioCapture.stopCapture();
        }
        if (recognizer != null) {
            recognizer.close();
        }
        if (voskModel != null) {
            voskModel.close();
        }

        if (recognitionThread != null) {
            recognitionThread.interrupt();
        }
        if (captureThread != null) {
            captureThread.interrupt();
        }

        if (consolePanel != null) {
            consolePanel.printSuccess("RecognitionWorker остановлен");
        }
    }

    private String extractTextFromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String text = obj.optString("text", "");

            if (text != null && !text.isEmpty()) {
                // Принудительное исправление кодировки
                text = fixRussianEncoding(text);
            }

            return text != null ? text.trim() : "";
        } catch (Exception e) {
            try {
                int textIndex = json.indexOf("\"text\"");
                if (textIndex == -1) return "";
                int colonIndex = json.indexOf(":", textIndex);
                if (colonIndex == -1) return "";
                int startQuote = json.indexOf("\"", colonIndex + 1);
                if (startQuote == -1) return "";
                int endQuote = json.indexOf("\"", startQuote + 1);
                if (endQuote == -1) return "";
                String text = json.substring(startQuote + 1, endQuote);
                text = fixRussianEncoding(text);
                return text.trim();
            } catch (Exception ex) {
                return "";
            }
        }
    }

    private String extractPartialFromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String partial = obj.optString("partial", "");

            if (partial != null && !partial.isEmpty()) {
                partial = fixRussianEncoding(partial);
            }

            return partial != null ? partial.trim() : "";
        } catch (Exception e) {
            try {
                int partialIndex = json.indexOf("\"partial\"");
                if (partialIndex == -1) return "";
                int colonIndex = json.indexOf(":", partialIndex);
                if (colonIndex == -1) return "";
                int startQuote = json.indexOf("\"", colonIndex + 1);
                if (startQuote == -1) return "";
                int endQuote = json.indexOf("\"", startQuote + 1);
                if (endQuote == -1) return "";
                String partial = json.substring(startQuote + 1, endQuote);
                partial = fixRussianEncoding(partial);
                return partial.trim();
            } catch (Exception ex) {
                return "";
            }
        }
    }

    // Добавьте новый метод для исправления русской кодировки
    private String fixRussianEncoding(String text) {
        if (text == null || text.isEmpty()) return text;

        try {
            // Пробуем преобразовать из CP1251 в UTF-8
            byte[] cp1251Bytes = text.getBytes(Charset.forName("Windows-1251"));
            String utf8Text = new String(cp1251Bytes, StandardCharsets.UTF_8);

            // Проверяем результат
            if (utf8Text.matches(".*[а-яА-ЯёЁ].*")) {
                return utf8Text;
            }

            // Пробуем другой вариант
            byte[] isoBytes = text.getBytes(StandardCharsets.ISO_8859_1);
            String utf8Text2 = new String(isoBytes, StandardCharsets.UTF_8);
            if (utf8Text2.matches(".*[а-яА-ЯёЁ].*")) {
                return utf8Text2;
            }

            return text;
        } catch (Exception e) {
            return text;
        }
    }


    private String fixEncoding(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        try {
            byte[] bytes = input.getBytes(StandardCharsets.ISO_8859_1);
            String utf8String = new String(bytes, StandardCharsets.UTF_8);

            if (containsRussianLetters(utf8String)) {
                return utf8String;
            }

            String direct = new String(input.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            if (containsRussianLetters(direct)) {
                return direct;
            }

            String cp1251 = new String(input.getBytes(StandardCharsets.UTF_8), Charset.forName("Windows-1251"));
            if (containsRussianLetters(cp1251)) {
                return cp1251;
            }

            return input;
        } catch (Exception e) {
            return input;
        }
    }

    private boolean containsRussianLetters(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.matches(".*[а-яА-ЯёЁ].*");
    }

    private String cleanRussianText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        if (result.startsWith("\uFEFF")) {
            result = result.substring(1);
        }

        result = result.replace("\\u0027", "'")
                .replace("\\u0022", "\"")
                .replace("\\u003C", "<")
                .replace("\\u003E", ">")
                .replace("\\\\", "\\");

        return result;
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}