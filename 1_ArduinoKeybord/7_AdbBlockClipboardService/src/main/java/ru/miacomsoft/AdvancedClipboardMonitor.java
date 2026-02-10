package ru.miacomsoft;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.net.*;
import java.util.regex.*;

public class AdvancedClipboardMonitor {
    private static final String SEND_PREFIX = "SEND(";
    private static final String SOUND_PREFIX = "SOUND("; // Новая команда
    private static Clipboard clipboard;
    private static volatile boolean running = true;
    private static String lastClipboardContent = "";

    // Конфигурация
    private static int checkInterval = 500; // Интервал проверки в миллисекундах
    private static int timeout = 5000; // Таймаут соединения
    private static boolean enableLogging = true;
    private static String defaultPort = "8080";

    public static void main(String[] args) {

        // Проверяем, запущены ли мы как служба
        boolean isService = System.getenv("SERVICE_NAME") != null || System.getProperty("service.mode", "false").equals("true");
        if (isService) {
            // Запускаем в отдельном потоке
            Thread serviceThread = new Thread(() -> runApplication(args));
            serviceThread.setDaemon(true);
            serviceThread.start();

            // Даем время на инициализацию
            try {
                Thread.sleep(10000); // 10 секунд
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Выходим из main, но поток продолжает работать
            System.out.println("Service initialized successfully");
            return;
        }

        // Обычный запуск
        runApplication(args);

    }

    private static void runApplication(String[] args) {

        // Парсим аргументы командной строки
        parseArguments(args);

        System.out.println("=== Clipboard Monitor ===");
        System.out.println("Поддерживаемые команды:");
        System.out.println("  SEND(IP:PORT):текст_для_отправки");
        System.out.println("  SOUND(IP:PORT):текст_для_отправки");
        System.out.println("Примеры:");
        System.out.println("  SEND(192.168.15.3:8080):Привет мир!");
        System.out.println("  SOUND(192.168.15.3:8080):Произвольный текст");
        System.out.println("  SEND(192.168.15.3):Привет! (порт по умолчанию: 8080)");
        System.out.println("  SOUND(localhost:3000):Тестовое сообщение");
        System.out.println();
        System.out.println("Настройки:");
        System.out.println("  Интервал проверки: " + checkInterval + " мс");
        System.out.println("  Таймаут соединения: " + timeout + " мс");
        System.out.println("  Порт по умолчанию: " + defaultPort);
        System.out.println();
        System.out.println("Для выхода нажмите Ctrl+C");
        System.out.println("=========================");

        clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        // Обработчик завершения
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            log("Программа завершена");
        }));

        startMonitoring();
    }

    private static void parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-i":
                case "--interval":
                    if (i + 1 < args.length) {
                        try {
                            checkInterval = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Некорректный интервал: " + args[i]);
                        }
                    }
                    break;

                case "-t":
                case "--timeout":
                    if (i + 1 < args.length) {
                        try {
                            timeout = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Некорректный таймаут: " + args[i]);
                        }
                    }
                    break;

                case "-p":
                case "--port":
                    if (i + 1 < args.length) {
                        defaultPort = args[++i];
                    }
                    break;

                case "-q":
                case "--quiet":
                    enableLogging = false;
                    break;

                case "-h":
                case "--help":
                    printHelp();
                    System.exit(0);
                    break;
            }
        }
    }

    private static void printHelp() {
        System.out.println("Clipboard Monitor - отправляет текст из буфера обмена на сервер");
        System.out.println();
        System.out.println("Использование: java -jar ClipboardMonitor.jar [опции]");
        System.out.println();
        System.out.println("Опции:");
        System.out.println("  -i, --interval МС    Интервал проверки буфера (по умолчанию: 500)");
        System.out.println("  -t, --timeout МС     Таймаут соединения (по умолчанию: 5000)");
        System.out.println("  -p, --port ПОРТ      Порт по умолчанию (по умолчанию: 8080)");
        System.out.println("  -q, --quiet          Тихий режим (минимальное логирование)");
        System.out.println("  -h, --help           Показать эту справку");
        System.out.println();
        System.out.println("Поддерживаемые команды в буфере обмена:");
        System.out.println("  SEND(IP:PORT):текст_для_отправки");
        System.out.println("  SOUND(IP:PORT):текст_для_отправки");
        System.out.println();
        System.out.println("Примеры:");
        System.out.println("  java -jar ClipboardMonitor.jar -i 1000 -t 3000");
        System.out.println("  java -jar ClipboardMonitor.jar --quiet");
    }

    private static void startMonitoring() {
        log("Начало мониторинга буфера обмена...");

        while (running) {
            try {
                String currentContent = getClipboardContent();

                if (currentContent != null && !currentContent.equals(lastClipboardContent)) {
                    lastClipboardContent = currentContent;

                    if (currentContent.startsWith(SEND_PREFIX)) {
                        log("Обнаружена команда SEND");
                        processCommand(currentContent, "SEND");
                    } else if (currentContent.startsWith(SOUND_PREFIX)) {
                        log("Обнаружена команда SOUND");
                        processCommand(currentContent, "SOUND");
                    }
                }

                Thread.sleep(checkInterval);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logError("Ошибка мониторинга: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }
    }

    private static String getClipboardContent() {
        try {
            Transferable transferable = clipboard.getContents(null);
            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return (String) transferable.getTransferData(DataFlavor.stringFlavor);
            }
        } catch (Exception e) {
            logError("Ошибка чтения буфера: " + e.getMessage());
        }
        return null;
    }

    private static void processCommand(String text, String commandType) {
        try {
            Pattern pattern = Pattern.compile("^" + commandType + "\\(([^):]+)(?::(\\d+))?\\):(.*)$", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(text.trim());

            if (matcher.matches()) {
                String ip = matcher.group(1);
                String port = matcher.group(2);
                String content = matcher.group(3).trim();

                if (port == null || port.isEmpty()) {
                    port = defaultPort;
                }

                String address = ip + ":" + port;

                log("Команда: " + commandType);
                log("Адрес: " + address);
                log("Длина текста: " + content.length() + " символов");

                if (enableLogging && content.length() > 50) {
                    log("Предпросмотр: " + content.substring(0, 50) + "...");
                }
                if (commandType.equals("SOUND")) {
                    content = "SOUND:"+content;
                }
                sendGetRequest(address, content);
                clearClipboard();

            } else {
                log("Некорректный формат команды " + commandType);
                log("Ожидается: " + commandType + "(IP:PORT):текст");
                log("Получено: " + text);
            }

        } catch (Exception e) {
            logError("Ошибка обработки: " + e.getMessage());
        }
    }

    private static void sendGetRequest(String address, String text) {
        long startTime = System.currentTimeMillis();

        try {
            String encodedText = URLEncoder.encode(text, "UTF-8");
            String url = "http://" + address + "/?text=" + encodedText;

            log("Отправка на: " + url);

            HttpURLConnection connection = null;
            try {
                URL urlObj = new URL(url);
                connection = (HttpURLConnection) urlObj.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(timeout);
                connection.setReadTimeout(timeout);
                connection.setRequestProperty("User-Agent", "ClipboardMonitor/1.0");
                connection.setRequestProperty("Accept", "text/plain");
                connection.setRequestProperty("Connection", "close");

                int responseCode = connection.getResponseCode();
                long duration = System.currentTimeMillis() - startTime;

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    in.close();

                    log(String.format("✓ Успешно отправлено за %d мс", duration));
                    log("Ответ сервера: " + response.toString());

                } else {
                    logError(String.format("✗ Ошибка HTTP %d за %d мс", responseCode, duration));

                    try {
                        BufferedReader errorReader = new BufferedReader(
                                new InputStreamReader(connection.getErrorStream()));
                        StringBuilder error = new StringBuilder();
                        String errorLine;

                        while ((errorLine = errorReader.readLine()) != null) {
                            error.append(errorLine);
                        }
                        errorReader.close();

                        if (error.length() > 0) {
                            logError("Детали: " + error.toString());
                        }
                    } catch (Exception e) {
                        // Игнорируем ошибки чтения потока ошибок
                    }
                }

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

        } catch (MalformedURLException e) {
            logError("Некорректный URL: " + e.getMessage());
        } catch (SocketTimeoutException e) {
            logError("Таймаут соединения (" + timeout + " мс)");
        } catch (ConnectException e) {
            logError("Не удалось подключиться к " + address);
        } catch (IOException e) {
            logError("Ошибка ввода/вывода: " + e.getMessage());
        } catch (Exception e) {
            logError("Неожиданная ошибка: " + e.getMessage());
        }
    }

    private static void clearClipboard() {
        try {
            StringSelection emptySelection = new StringSelection("");
            clipboard.setContents(emptySelection, null);
            lastClipboardContent = "";
            log("Буфер обмена очищен");
        } catch (Exception e) {
            logError("Ошибка очистки буфера: " + e.getMessage());
        }
    }

    private static void log(String message) {
        if (enableLogging) {
            System.out.println("[INFO] " + message);
        }
    }

    private static void logError(String message) {
        System.err.println("[ERROR] " + message);
    }
}