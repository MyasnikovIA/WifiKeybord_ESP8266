package ru.miacomsoft.audio;

import ru.miacomsoft.ui.ConsolePanel;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

public class AudioCapture {

    private TargetDataLine line;
    private AudioFormat format;
    private boolean isRunning = false;
    private ConsolePanel consolePanel;

    public AudioCapture(int sampleRate) {
        format = new AudioFormat(sampleRate, 16, 1, true, false);
    }

    public void setConsolePanel(ConsolePanel consolePanel) {
        this.consolePanel = consolePanel;
    }

    public List<AudioDeviceInfo> listMicrophones() {
        List<AudioDeviceInfo> devices = new ArrayList<>();
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();

        int index = 1;
        for (Mixer.Info mixerInfo : mixers) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            Line.Info[] targetLines = mixer.getTargetLineInfo();

            for (Line.Info lineInfo : targetLines) {
                if (lineInfo.getLineClass().equals(TargetDataLine.class)) {
                    boolean isBusy = false;
                    String name = mixerInfo.getName();

                    if (name.contains(".monitor") || name.contains("Monitor")) {
                        continue;
                    }

                    try {
                        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, format);
                        if (!AudioSystem.isLineSupported(dataLineInfo)) {
                            continue;
                        }
                    } catch (Exception e) {
                        isBusy = true;
                    }

                    devices.add(new AudioDeviceInfo(index, name, mixerInfo, isBusy));
                    index++;
                    break;
                }
            }
        }
        return devices;
    }

    public boolean startCapture(int deviceIndex, AudioCaptureCallback callback) {
        try {
            List<AudioDeviceInfo> devices = listMicrophones();
            if (deviceIndex < 1 || deviceIndex > devices.size()) {
                if (consolePanel != null) {
                    consolePanel.printError("Устройство не найдено: " + deviceIndex);
                } else {
                    System.err.println("Устройство не найдено");
                }
                return false;
            }

            AudioDeviceInfo selected = devices.get(deviceIndex - 1);
            Mixer mixer = AudioSystem.getMixer(selected.getMixerInfo());

            if (consolePanel != null) {
                consolePanel.printInfo("Открытие аудиоустройства: " + selected.getName());
            }

            int bufferSize = 8192;
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format, bufferSize);
            line = (TargetDataLine) mixer.getLine(info);
            line.open(format, bufferSize);
            line.start();

            if (consolePanel != null) {
                consolePanel.printSuccess("Аудиоустройство открыто, буфер: " + bufferSize + " байт");
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
                        callback.onAudioData(data);

                        if (totalBytes % 102400 < 4096 && consolePanel != null) {
                            consolePanel.printAudioData("Захвачено " + (totalBytes / 1024) + " KB с микрофона");
                        }
                    }
                }
                if (consolePanel != null) {
                    consolePanel.printInfo("Захват с микрофона остановлен. Всего захвачено: " + (totalBytes / 1024) + " KB");
                }
            });
            captureThread.setDaemon(true);
            captureThread.start();

            return true;
        } catch (Exception e) {
            if (consolePanel != null) {
                consolePanel.printError("Ошибка захвата: " + e.getMessage());
            } else {
                System.err.println("Ошибка захвата: " + e.getMessage());
            }
            return false;
        }
    }

    public void stopCapture() {
        isRunning = false;
        if (line != null) {
            if (consolePanel != null) {
                consolePanel.printInfo("Остановка захвата с микрофона");
            }
            line.stop();
            line.close();
        }
    }
}