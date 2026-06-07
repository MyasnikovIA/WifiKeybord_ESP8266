package ru.miacomsoft;

import ru.miacomsoft.TextPunctuator;
import ru.miacomsoft.audio.AudioCapture;
import ru.miacomsoft.audio.AudioCaptureCallback;
import ru.miacomsoft.audio.AudioDeviceInfo;
import ru.miacomsoft.audio.SystemAudioCapture;
import ru.miacomsoft.audio.WindowsSystemAudioCapture;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConsoleRecognitionWorker {
    private static final int SAMPLE_RATE = 16000;
    private static final int CHUNK_SIZE = 4000;

    private String modelPath;
    private String serverUrl;
    private boolean isSystemAudio;
    private AudioDeviceInfo device;
    private String clientName;

    private Model voskModel;
    private Recognizer recognizer;
    private BlockingQueue<byte[]> audioQueue;
    private AtomicBoolean isRunning;
    private Thread recognitionThread;
    private Thread captureThread;
    private AudioCapture audioCapture;
    private SystemAudioCapture systemAudioCapture;
    private WindowsSystemAudioCapture windowsSystemAudioCapture;

    private int totalFinalResults = 0;

    public ConsoleRecognitionWorker(String modelPath, int sampleRate, String serverUrl,
                                    boolean isSystemAudio, AudioDeviceInfo device, String clientName) {
        this.modelPath = modelPath;
        this.serverUrl = serverUrl;
        this.isSystemAudio = isSystemAudio;
        this.device = device;
        this.clientName = clientName;
        this.audioQueue = new LinkedBlockingQueue<>(2000);
        this.isRunning = new AtomicBoolean(false);
    }

    public void start() {
        System.out.println("[INFO] Загрузка модели Vosk...");

        if (!loadModel()) {
            System.err.println("[ERROR] Не удалось загрузить модель");
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
            voskModel = new Model(modelPath);
            recognizer = new Recognizer(voskModel, SAMPLE_RATE);
            recognizer.setWords(true);
            System.out.println("[SUCCESS] Модель загружена");
            return true;
        } catch (Exception e) {
            System.err.println("[ERROR] Ошибка загрузки модели: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void startRecognitionThread() {
        recognitionThread = new Thread(() -> {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bufferPos = 0;
            String lastPartial = "";

            while (isRunning.get()) {
                try {
                    byte[] data = audioQueue.poll(50, TimeUnit.MILLISECONDS);
                    if (data != null && data.length > 0) {
                        for (byte b : data) {
                            buffer[bufferPos++] = b;
                            if (bufferPos >= CHUNK_SIZE) {
                                if (recognizer.acceptWaveForm(buffer, bufferPos)) {
                                    String result = recognizer.getResult();
                                    if (result != null && !result.isEmpty()) {
                                        String text = extractTextFromJson(result);
                                        if (!text.isEmpty()) {
                                            totalFinalResults++;
                                            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                                            text = TextPunctuator.addPunctuation(text);
                                            text = TextPunctuator.capitalizeSentences(text);
                                            text = "[RECOGNIZED]" + "[" + clientName + "]" + text;
                                            // Вывод в консоль
                                            System.out.println("\n[" + timestamp + "]" + text);
                                            // Отправка на сервер
                                            sendToServer(text);
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
                                            System.out.print("\r[PARTIAL]" + "[" + clientName + "]" + partialText);
                                            sendToServer("[PARTIAL]"+ "[" + clientName + "]" +partialText);
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
            System.out.println("\n[INFO] Распознавание остановлено. Всего результатов: " + totalFinalResults);
        });
        recognitionThread.setDaemon(true);
        recognitionThread.start();
    }

    private void sendToServer(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        new Thread(() -> {
            try {
                URL url = new URL(serverUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                String encodedText = java.net.URLEncoder.encode(text, "UTF-8");
                String body = encodedText;

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    System.out.println("[SERVER] Отправлено: " + text);
                } else {
                    System.err.println("[SERVER] Ошибка отправки (код " + responseCode + "): " + text);
                }

                connection.disconnect();
            } catch (Exception e) {
                System.err.println("[SERVER] Ошибка соединения с сервером: " + e.getMessage());
            }
        }).start();
    }

    private void startMicrophoneCapture() {
        audioCapture = new AudioCapture(SAMPLE_RATE);

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

        captureThread = new Thread(() -> {
            System.out.println("[INFO] Запуск захвата с микрофона: " + device.getName());
            audioCapture.startCapture(device.getId(), callback);
        });
        captureThread.setDaemon(true);
        captureThread.start();
    }

    private void startSystemAudioCapture() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            windowsSystemAudioCapture = new WindowsSystemAudioCapture(SAMPLE_RATE);
            List<AudioDeviceInfo> systemDevices = windowsSystemAudioCapture.listSystemAudioDevices();

            if (systemDevices.isEmpty()) {
                System.err.println("[ERROR] Stereo Mix не найден!");
                System.err.println("Включите Stereo Mix в настройках звука Windows");
                return;
            }

            AudioDeviceInfo selectedDevice = systemDevices.get(0);
            System.out.println("[INFO] Захват системного звука с: " + selectedDevice.getName());

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

            captureThread = new Thread(() -> {
                windowsSystemAudioCapture.startCapture(selectedDevice, callback);
            });
            captureThread.setDaemon(true);
            captureThread.start();
        } else {
            // Linux
            systemAudioCapture = new SystemAudioCapture();
            String monitorName = SystemAudioCapture.getDefaultMonitor();

            if (monitorName == null) {
                System.err.println("[ERROR] Системный монитор не найден!");
                return;
            }

            System.out.println("[INFO] Захват системного звука с: " + monitorName);

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

            captureThread = new Thread(() -> {
                systemAudioCapture.startCapture(monitorName, callback);
            });
            captureThread.setDaemon(true);
            captureThread.start();
        }
    }

    private String extractTextFromJson(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String text = obj.optString("text", "");
            if (text != null && !text.isEmpty()) {
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

    private String fixRussianEncoding(String text) {
        if (text == null || text.isEmpty()) return text;

        try {
            byte[] cp1251Bytes = text.getBytes(java.nio.charset.Charset.forName("Windows-1251"));
            String utf8Text = new String(cp1251Bytes, StandardCharsets.UTF_8);
            if (utf8Text.matches(".*[а-яА-ЯёЁ].*")) {
                return utf8Text;
            }

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

    public void stop() {
        System.out.println("[INFO] Остановка...");
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
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}