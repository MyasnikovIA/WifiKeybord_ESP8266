package ru.miacomsoft;

import ru.miacomsoft.audio.AudioCapture;
import ru.miacomsoft.audio.AudioDeviceInfo;
import ru.miacomsoft.audio.WindowsSystemAudioCapture;
import ru.miacomsoft.config.SettingsManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;

public class Main {
    private static final int SAMPLE_RATE = 16000;
    private static String serverUrl = "http://192.168.15.3:8080";
    private static AudioCapture audioCapture;
    private static ConsoleRecognitionWorker recognitionWorker;
    private static boolean isRunning = true;
    private static SettingsManager settings;

    public static void main(String[] args) {
        // Загрузка настроек
        settings = SettingsManager.getInstance();

        // Парсинг аргументов командной строки
        if (args.length > 0) {
            serverUrl = args[0];
            if (!serverUrl.startsWith("http://")) {
                serverUrl = "http://" + serverUrl;
            }
            if (!serverUrl.contains(":")) {
                serverUrl = serverUrl + ":8080";
            }
        }

        System.out.println("========================================");
        System.out.println("   Стенограф - Консольная версия");
        System.out.println("========================================");
        System.out.println("Сервер: " + serverUrl);
        System.out.println("Имя клиента: " + settings.getClientName());
        System.out.println("Путь к модели: " + settings.getModelPath());
        System.out.println("========================================\n");

        // Проверка модели
        if (!validateModelPath(settings.getModelPath())) {
            System.out.println("ВНИМАНИЕ: Путь к модели не указан или не существует!");
            System.out.println("Пожалуйста, укажите путь к модели Vosk в настройках.\n");
        }

        showMenu();
    }

    private static boolean validateModelPath(String modelPath) {
        if (modelPath == null || modelPath.isEmpty()) {
            return false;
        }
        File modelDir = new File(modelPath);
        return modelDir.exists() && modelDir.isDirectory();
    }

    private static void showMenu() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (isRunning) {
            System.out.println("\n========================================");
            System.out.println("Выберите действие:");
            System.out.println("1. Запуск с микрофона");
            System.out.println("2. Запуск с системного звука (Stereo Mix)");
            System.out.println("3. Список доступных микрофонов");
            System.out.println("4. Настройки");
            System.out.println("5. Изменить адрес сервера (текущий: " + serverUrl + ")");
            System.out.println("0. Выход");
            System.out.print("> ");

            try {
                String choice = reader.readLine();
                if (choice == null) break;

                switch (choice.trim()) {
                    case "1":
                        selectMicrophone(reader);
                        break;
                    case "2":
                        startSystemAudioCapture();
                        break;
                    case "3":
                        listMicrophones();
                        break;
                    case "4":
                        showSettingsMenu(reader);
                        break;
                    case "5":
                        changeServerUrl(reader);
                        break;
                    case "0":
                        stopRecognition();
                        isRunning = false;
                        System.out.println("До свидания!");
                        break;
                    default:
                        System.out.println("Неверный выбор. Попробуйте снова.");
                }
            } catch (Exception e) {
                System.err.println("Ошибка: " + e.getMessage());
            }
        }
    }

    private static void showSettingsMenu(BufferedReader reader) {
        while (true) {
            System.out.println("\n========================================");
            System.out.println("НАСТРОЙКИ");
            System.out.println("========================================");
            System.out.println("1. Имя клиента: " + settings.getClientName());
            System.out.println("2. Путь к модели Vosk: " + settings.getModelPath());
            System.out.println("3. Сбросить путь к модели по умолчанию");
            System.out.println("0. Назад");
            System.out.print("> ");

            try {
                String choice = reader.readLine();
                if (choice == null) break;

                switch (choice.trim()) {
                    case "1":
                        changeClientName(reader);
                        break;
                    case "2":
                        changeModelPath(reader);
                        break;
                    case "3":
                        resetModelPath();
                        break;
                    case "0":
                        return;
                    default:
                        System.out.println("Неверный выбор.");
                }
            } catch (Exception e) {
                System.err.println("Ошибка: " + e.getMessage());
            }
        }
    }

    private static void changeClientName(BufferedReader reader) {
        System.out.print("Введите новое имя клиента (будет добавляться в начало сообщений): ");
        try {
            String newName = reader.readLine();
            if (newName != null && !newName.trim().isEmpty()) {
                settings.setClientName(newName.trim());
                settings.saveSettings();
                System.out.println("✅ Имя клиента сохранено: " + newName.trim());
            }
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }

    private static void changeModelPath(BufferedReader reader) {
        System.out.print("Введите полный путь к папке с моделью Vosk: ");
        try {
            String newPath = reader.readLine();
            if (newPath != null && !newPath.trim().isEmpty()) {
                File modelDir = new File(newPath.trim());
                if (modelDir.exists() && modelDir.isDirectory()) {
                    settings.setModelPath(newPath.trim());
                    settings.saveSettings();
                    System.out.println("✅ Путь к модели сохранен: " + newPath.trim());
                } else {
                    System.out.println("❌ Ошибка: Папка не существует! Проверьте путь.");
                }
            }
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }

    private static void resetModelPath() {
        String defaultPath = settings.getDefaultModelPath();
        settings.setModelPath(defaultPath);
        settings.saveSettings();
        System.out.println("✅ Путь к модели сброшен на значение по умолчанию: " + defaultPath);
    }

    private static void listMicrophones() {
        audioCapture = new AudioCapture(SAMPLE_RATE);
        List<AudioDeviceInfo> devices = audioCapture.listMicrophones();

        if (devices.isEmpty()) {
            System.out.println("Нет доступных микрофонов!");
        } else {
            System.out.println("\nДоступные микрофоны:");
            for (AudioDeviceInfo device : devices) {
                System.out.println("  " + device);
            }
        }
    }

    private static void selectMicrophone(BufferedReader reader) {
        if (audioCapture == null) {
            audioCapture = new AudioCapture(SAMPLE_RATE);
        }

        List<AudioDeviceInfo> devices = audioCapture.listMicrophones();

        if (devices.isEmpty()) {
            System.out.println("Нет доступных микрофонов!");
            return;
        }

        System.out.println("\nДоступные микрофоны:");
        for (AudioDeviceInfo device : devices) {
            System.out.println("  " + device);
        }

        System.out.print("\nВведите номер устройства: ");
        try {
            int deviceNum = Integer.parseInt(reader.readLine());

            AudioDeviceInfo selected = null;
            for (AudioDeviceInfo device : devices) {
                if (device.getId() == deviceNum) {
                    selected = device;
                    break;
                }
            }

            if (selected != null) {
                startMicrophoneCapture(selected);
            } else {
                System.out.println("Устройство не найдено!");
            }
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }

    private static void startMicrophoneCapture(AudioDeviceInfo device) {
        if (!validateModelPath(settings.getModelPath())) {
            System.out.println("\n❌ Ошибка: Путь к модели не указан или не существует!");
            System.out.println("Пожалуйста, укажите путь к модели в настройках (пункт 4 меню).");
            System.out.println("\nНажмите Enter для возврата в меню...");
            try {
                System.in.read();
            } catch (Exception e) {}
            return;
        }

        stopRecognition();

        System.out.println("\n========================================");
        System.out.println("Запуск распознавания с микрофона");
        System.out.println("Устройство: " + device.getName());
        System.out.println("Имя клиента: " + settings.getClientName());
        System.out.println("Сервер: " + serverUrl);
        System.out.println("========================================");
        System.out.println("Говорите... (Ctrl+C для остановки)");
        System.out.println("----------------------------------------\n");

        recognitionWorker = new ConsoleRecognitionWorker(
                settings.getModelPath(),
                SAMPLE_RATE,
                serverUrl,
                false,
                device,
                settings.getClientName()
        );

        recognitionWorker.start();
        waitForStop();
    }

    private static void startSystemAudioCapture() {
        if (!validateModelPath(settings.getModelPath())) {
            System.out.println("\n❌ Ошибка: Путь к модели не указан или не существует!");
            System.out.println("Пожалуйста, укажите путь к модели в настройках (пункт 4 меню).");
            System.out.println("\nНажмите Enter для возврата в меню...");
            try {
                System.in.read();
            } catch (Exception e) {}
            return;
        }

        stopRecognition();

        String osName = System.getProperty("os.name").toLowerCase();

        System.out.println("\n========================================");
        System.out.println("Запуск распознавания системного звука");
        System.out.println("Имя клиента: " + settings.getClientName());
        System.out.println("Сервер: " + serverUrl);
        System.out.println("========================================");

        AudioDeviceInfo selectedDevice = null;

        if (osName.contains("win")) {
            WindowsSystemAudioCapture winCapture = new WindowsSystemAudioCapture(SAMPLE_RATE);
            List<AudioDeviceInfo> devices = winCapture.listSystemAudioDevices();

            if (devices.isEmpty()) {
                System.out.println("\n❌ Stereo Mix не найден!");
                System.out.println("\nРешения:");
                System.out.println("1. Включите Stereo Mix в настройках звука Windows:");
                System.out.println("   - ПКМ на динамик в трее → Звуки → Запись");
                System.out.println("   - ПКМ на пустом месте → Показать отключенные устройства");
                System.out.println("   - Включите Stereo Mix или 'Что слышно'");
                System.out.println("2. Используйте микрофон (выберите пункт 1 в меню)");
                System.out.println("\nНажмите Enter для возврата в меню...");

                try {
                    System.in.read();
                } catch (Exception e) {}
                return;
            }

            selectedDevice = devices.get(0);
            System.out.println("\n✅ Найден Stereo Mix: " + selectedDevice.getName());
        } else {
            String monitor = ru.miacomsoft.audio.SystemAudioCapture.getDefaultMonitor();
            if (monitor == null) {
                System.out.println("\n❌ Системный монитор не найден!");
                System.out.println("Убедитесь, что PulseAudio установлен и работает");
                return;
            }
            System.out.println("\n✅ Найден монитор: " + monitor);
        }

        System.out.println("========================================");
        System.out.println("Говорите... (Ctrl+C для остановки)");
        System.out.println("----------------------------------------\n");

        recognitionWorker = new ConsoleRecognitionWorker(
                settings.getModelPath(),
                SAMPLE_RATE,
                serverUrl,
                true,
                selectedDevice,
                settings.getClientName()
        );

        recognitionWorker.start();
        waitForStop();
    }

    private static void waitForStop() {
        try {
            while (recognitionWorker != null && recognitionWorker.isRunning()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void stopRecognition() {
        if (recognitionWorker != null) {
            recognitionWorker.stop();
            recognitionWorker = null;
        }
    }

    private static void changeServerUrl(BufferedReader reader) {
        System.out.print("Введите новый адрес сервера (IP:PORT или URL): ");
        try {
            String newUrl = reader.readLine();
            if (newUrl != null && !newUrl.trim().isEmpty()) {
                if (!newUrl.startsWith("http://")) {
                    newUrl = "http://" + newUrl;
                }
                serverUrl = newUrl;
                System.out.println("Сервер изменен на: " + serverUrl);
            }
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }
}