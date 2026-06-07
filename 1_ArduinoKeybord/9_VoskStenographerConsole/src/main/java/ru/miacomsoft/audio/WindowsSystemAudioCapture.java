package ru.miacomsoft.audio;

import ru.miacomsoft.ui.ConsolePanel;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

public class WindowsSystemAudioCapture {

    private TargetDataLine line;
    private AudioFormat format;
    private boolean isRunning = false;
    private ConsolePanel consolePanel;
    private Mixer selectedMixer;
    private AudioDeviceInfo currentDevice;

    public WindowsSystemAudioCapture(int sampleRate) {
        format = new AudioFormat(sampleRate, 16, 1, true, false);
    }

    public void setConsolePanel(ConsolePanel consolePanel) {
        this.consolePanel = consolePanel;
    }

    public List<AudioDeviceInfo> listSystemAudioDevices() {
        List<AudioDeviceInfo> devices = new ArrayList<>();
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();

        int index = 1;
        for (Mixer.Info mixerInfo : mixers) {
            String name = mixerInfo.getName().toLowerCase();

            if (name.contains("stereo mix") ||
                    name.contains("what u hear") ||
                    name.contains("loopback") ||
                    name.contains("wave out") ||
                    name.contains("mix") ||
                    name.contains("what you hear") ||
                    name.contains("цифровой звук") ||
                    name.contains("digital audio") ||
                    name.contains("hdmi")) {

                if (isCaptureDevice(mixerInfo)) {
                    devices.add(new AudioDeviceInfo(index, mixerInfo.getName(), mixerInfo, false));
                    index++;
                }
            }
        }

        return devices;
    }

    public List<AudioDeviceInfo> listAllCaptureDevices() {
        List<AudioDeviceInfo> devices = new ArrayList<>();
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();

        int index = 1;
        for (Mixer.Info mixerInfo : mixers) {
            if (isCaptureDevice(mixerInfo)) {
                devices.add(new AudioDeviceInfo(index, mixerInfo.getName(), mixerInfo, false));
                index++;
            }
        }

        return devices;
    }

    private boolean isCaptureDevice(Mixer.Info mixerInfo) {
        try {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            Line.Info[] targetLines = mixer.getTargetLineInfo();

            for (Line.Info lineInfo : targetLines) {
                if (lineInfo.getLineClass().equals(TargetDataLine.class)) {
                    DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
                    if (AudioSystem.isLineSupported(dataLineInfo)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Пропускаем
        }
        return false;
    }

    public boolean startCapture(AudioDeviceInfo device, AudioCaptureCallback callback) {
        if (device == null) {
            if (consolePanel != null) {
                consolePanel.printError("Устройство не выбрано");
            }
            return false;
        }

        try {
            currentDevice = device;
            Mixer mixer = AudioSystem.getMixer(device.getMixerInfo());

            if (consolePanel != null) {
                consolePanel.printInfo("Открытие аудиоустройства: " + device.getName());
            }

            int bufferSize = 8192;
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format, bufferSize);
            line = (TargetDataLine) mixer.getLine(info);
            line.open(format, bufferSize);
            line.start();

            if (consolePanel != null) {
                consolePanel.printSuccess("Аудиоустройство открыто, буфер: " + bufferSize + " байт");
                consolePanel.printInfo("Захват с: " + device.getName());
            }

            isRunning = true;

            Thread captureThread = new Thread(() -> {
                byte[] buffer = new byte[bufferSize];
                int totalBytes = 0;
                while (isRunning) {
                    int bytesRead = line.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        totalBytes += bytesRead;
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);

                        // Усиливаем звук в 5 раз (14 dB усиления)
                        data = amplifyAudio(data, 5.0);

                        // Проверка уровня звука после усиления
                        checkAudioLevel(data);

                        if (callback != null) {
                            callback.onAudioData(data);
                        }

                        if (totalBytes % 102400 < 4096 && consolePanel != null) {
                            consolePanel.printAudioData("Захвачено " + (totalBytes / 1024) + " KB с " + device.getName());
                        }
                    }
                }
                if (consolePanel != null) {
                    consolePanel.printInfo("Захват остановлен. Всего захвачено: " + (totalBytes / 1024) + " KB с " + device.getName());
                }
            });
            captureThread.setDaemon(true);
            captureThread.start();

            return true;
        } catch (Exception e) {
            if (consolePanel != null) {
                consolePanel.printError("Ошибка захвата: " + e.getMessage());
                consolePanel.printError("Убедитесь, что устройство доступно и не используется другим приложением");
            }
            return false;
        }
    }

    public boolean startCapture(AudioCaptureCallback callback) {
        List<AudioDeviceInfo> systemDevices = listSystemAudioDevices();

        if (!systemDevices.isEmpty()) {
            if (consolePanel != null) {
                consolePanel.printInfo("Найдено устройство системного звука: " + systemDevices.get(0).getName());
            }
            return startCapture(systemDevices.get(0), callback);
        }

        List<AudioDeviceInfo> allDevices = listAllCaptureDevices();
        if (!allDevices.isEmpty()) {
            if (consolePanel != null) {
                consolePanel.printWarning("Специальные устройства системного звука не найдены");
                consolePanel.printInfo("Доступные устройства захвата:");
                for (AudioDeviceInfo dev : allDevices) {
                    consolePanel.printInfo("  - " + dev.getName());
                }
                consolePanel.printWarning("Используйте микрофон или настройте Stereo Mix");
            }
            return false;
        }

        if (consolePanel != null) {
            consolePanel.printError("Не найдено устройств для захвата звука");
        }
        return false;
    }

    public void stopCapture() {
        isRunning = false;
        if (line != null) {
            if (consolePanel != null) {
                consolePanel.printInfo("Остановка захвата");
            }
            line.stop();
            line.close();
        }
    }

    public AudioDeviceInfo getCurrentDevice() {
        return currentDevice;
    }
    private void checkAudioLevel(byte[] data) {
        if (data == null || data.length < 2) return;

        // Вычисляем RMS (среднеквадратичное значение)
        long sum = 0;
        for (int i = 0; i < data.length; i += 2) {
            short sample = (short) ((data[i+1] << 8) | (data[i] & 0xFF));
            sum += sample * sample;
        }
        double rms = Math.sqrt(sum / (data.length / 2));
        double db = 20 * Math.log10(Math.max(rms, 1) / 32768.0);

        if (consolePanel != null && Math.random() < 0.05) { // Логируем ~5% данных
            consolePanel.printDebug(String.format("Уровень звука: %.1f dB (RMS: %.0f)", db, rms));
        }
    }
    private byte[] amplifyAudio(byte[] data, double gain) {
        if (gain == 1.0) return data;

        byte[] amplified = new byte[data.length];
        for (int i = 0; i < data.length; i += 2) {
            // Преобразуем 2 байта в 16-битный сэмпл
            short sample = (short) ((data[i + 1] << 8) | (data[i] & 0xFF));
            // Усиливаем
            short amplifiedSample = (short) Math.min(32767, Math.max(-32768, sample * gain));
            // Обратно в байты
            amplified[i] = (byte) (amplifiedSample & 0xFF);
            amplified[i + 1] = (byte) ((amplifiedSample >> 8) & 0xFF);
        }
        return amplified;
    }
}