package ru.miacomsoft.audio;

import ru.miacomsoft.ui.ConsolePanel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class SystemAudioCapture {

    private Process process;
    private boolean isRunning = false;
    private ConsolePanel consolePanel;
    private WindowsSystemAudioCapture windowsCapture;
    private boolean isWindows;
    private int sampleRate = 16000;

    public SystemAudioCapture() {
        String osName = System.getProperty("os.name").toLowerCase();
        isWindows = osName.contains("win");

        if (isWindows) {
            windowsCapture = new WindowsSystemAudioCapture(sampleRate);
        }
    }

    public void setConsolePanel(ConsolePanel consolePanel) {
        this.consolePanel = consolePanel;
        if (windowsCapture != null) {
            windowsCapture.setConsolePanel(consolePanel);
        }
    }

    public static boolean isPulseAudioAvailable() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return false;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("pactl", "--version");
            Process process = pb.start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getDefaultMonitor() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return null;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("pactl", "get-default-sink");
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String sink = reader.readLine();
            process.waitFor();
            if (sink != null && !sink.isEmpty()) {
                return sink + ".monitor";
            }
        } catch (Exception e) {
            // Игнорируем
        }
        return null;
    }

    public static java.util.List<String> getAllMonitors() {
        java.util.List<String> monitors = new java.util.ArrayList<>();
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return monitors;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("pactl", "list", "short", "sinks");
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    monitors.add(parts[1] + ".monitor");
                }
            }
            process.waitFor();
        } catch (Exception e) {
            // Игнорируем
        }
        return monitors;
    }

    public void createLoopback() {
        if (isWindows) {
            if (consolePanel != null) {
                consolePanel.printInfo("На Windows loopback не требуется, используем Stereo Mix");
            }
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "pactl", "load-module", "module-loopback",
                    "latency_msec=1",
                    "adjust_time=1"
            );
            Process process = pb.start();
            process.waitFor();
            if (consolePanel != null) {
                consolePanel.printInfo("Создано loopback устройство для захвата системного звука");
            }
        } catch (Exception e) {
            if (consolePanel != null) {
                consolePanel.printError("Ошибка создания loopback: " + e.getMessage());
            }
        }
    }

    public void createNullSinkForAppAudio() {
        if (isWindows) {
            if (consolePanel != null) {
                consolePanel.printInfo("На Windows null-sink не поддерживается");
            }
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "pactl", "load-module", "module-null-sink",
                    "sink_name=stenographer_null",
                    "sink_properties=device.description=Stenographer_AppCapture"
            );
            Process process = pb.start();
            process.waitFor();

            if (consolePanel != null) {
                consolePanel.printInfo("Создан null-sink для захвата звука приложений: stenographer_null.monitor");
            }
        } catch (Exception e) {
            if (consolePanel != null) {
                consolePanel.printError("Ошибка создания null-sink: " + e.getMessage());
            }
        }
    }

    public boolean startCapture(String monitorName, AudioCaptureCallback callback) {
        if (isWindows) {
            return startWindowsCapture(callback);
        } else {
            return startLinuxCapture(monitorName, callback);
        }
    }

    private boolean startWindowsCapture(AudioCaptureCallback callback) {
        if (windowsCapture == null) {
            if (consolePanel != null) {
                consolePanel.printError("Не удалось инициализировать захват на Windows");
            }
            return false;
        }

        if (consolePanel != null) {
            consolePanel.printInfo("Запуск захвата системного звука на Windows");
            consolePanel.printInfo("Используется Stereo Mix или аналогичное устройство");
        }

        return windowsCapture.startCapture(callback);
    }

    private boolean startLinuxCapture(String monitorName, AudioCaptureCallback callback) {
        try {
            if (consolePanel != null) {
                consolePanel.printInfo("Запуск захвата системного звука с монитора: " + monitorName);
            }

            String[] cmd = {
                    "parec", "--format=s16le", "--rate=16000", "--channels=1",
                    "--device=" + monitorName, "--raw"
            };

            if (consolePanel != null) {
                consolePanel.printDebug("Команда: " + String.join(" ", cmd));
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            process = pb.start();
            isRunning = true;

            if (consolePanel != null) {
                consolePanel.printSuccess("Процесс parec запущен");
            }

            Thread captureThread = new Thread(() -> {
                int totalBytes = 0;
                try (InputStream inputStream = process.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    while (isRunning) {
                        int bytesRead = inputStream.read(buffer);
                        if (bytesRead > 0) {
                            totalBytes += bytesRead;
                            byte[] data = new byte[bytesRead];
                            System.arraycopy(buffer, 0, data, 0, bytesRead);
                            if (callback != null) {
                                callback.onAudioData(data);
                            }

                            if (totalBytes % 102400 < 4096 && consolePanel != null) {
                                consolePanel.printAudioData("Получено " + (totalBytes / 1024) + " KB системного аудио");
                            }
                        }
                    }
                } catch (IOException e) {
                    if (isRunning && consolePanel != null) {
                        consolePanel.printError("Ошибка чтения аудиопотока: " + e.getMessage());
                    }
                }
                if (consolePanel != null) {
                    consolePanel.printInfo("Захват системного звука остановлен. Всего получено: " + (totalBytes / 1024) + " KB");
                }
            });
            captureThread.setDaemon(true);
            captureThread.start();

            return true;
        } catch (Exception e) {
            if (consolePanel != null) {
                consolePanel.printError("Ошибка захвата системного звука: " + e.getMessage());
            }
            return false;
        }
    }

    public void stopCapture() {
        isRunning = false;

        if (windowsCapture != null) {
            windowsCapture.stopCapture();
        }

        if (process != null) {
            if (consolePanel != null) {
                consolePanel.printInfo("Остановка процесса parec");
            }
            process.destroy();
            try {
                process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return isRunning || (windowsCapture != null && windowsCapture.getCurrentDevice() != null);
    }
}