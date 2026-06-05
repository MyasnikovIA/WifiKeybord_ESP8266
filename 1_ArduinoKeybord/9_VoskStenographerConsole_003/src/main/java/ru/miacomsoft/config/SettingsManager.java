package ru.miacomsoft.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class SettingsManager {
    private static SettingsManager instance;
    private Properties properties;
    private String configFile;

    private static String DEFAULT_MODEL_PATH;

    static {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            DEFAULT_MODEL_PATH = "C:\\ArduinoProject\\WifiKeybord_ESP8266\\1_ArduinoKeybord\\9_VoskStenographerConsole_003\\VoseModel\\vosk-model-small-ru-0.22";
        } else {
            DEFAULT_MODEL_PATH = "/media/myasnikov/512GB_Avito/Model/vosk-model-ru-0.10";
        }
    }

    private SettingsManager() {
        properties = new Properties();
        configFile = System.getProperty("user.home") + File.separator + ".stenographer.conf";
        loadSettings();
    }

    public static SettingsManager getInstance() {
        if (instance == null) {
            instance = new SettingsManager();
        }
        return instance;
    }

    public void loadSettings() {
        File file = new File(configFile);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                properties.load(fis);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (getModelPath() == null || getModelPath().isEmpty()) {
            setModelPath(DEFAULT_MODEL_PATH);
        }

        if (getClientName() == null || getClientName().isEmpty()) {
            setClientName("Клиент");
        }
    }

    public void saveSettings() {
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            properties.store(fos, "Stenographer Settings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getModelPath() {
        return properties.getProperty("model.path", "");
    }

    public void setModelPath(String path) {
        properties.setProperty("model.path", path);
    }

    public String getClientName() {
        return properties.getProperty("client.name", "Клиент");
    }

    public void setClientName(String name) {
        properties.setProperty("client.name", name);
    }

    public String getDefaultModelPath() {
        return DEFAULT_MODEL_PATH;
    }
}