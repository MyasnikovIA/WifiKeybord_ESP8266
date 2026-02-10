#include <Wire.h>
#include <ESP8266WiFi.h>
#include <WiFiClient.h>
#include <WiFiServer.h>

// Для ATmega32U4 (Master) - модифицированный код из третьего ответа:
// ATmega32U4 (Master)      ESP8266 (Slave)       Подключение
// -----------------------------------------------------------
// PD1 (SDA)               GPIO4 (SDA)            ------> Синяя линия
// PD0 (SCL)               GPIO5 (SCL)            ------> Желтая линия
// GND                       GND                  ------> Черная линия
// 3.3V/VCC                VIN/3.3V (опционально) --> Красная линия (если нужно питать ESP)

#define SLAVE_ADDR 18  // Адрес slave устройства (ATmega32U4)
#define AP_PORT 8200   // Порт для сокет-сервера точки доступа

// Настройки точки доступа
const char* ap_ssid = "WIFI_keybord_ESP8266";
const char* ap_password = "12345678"; // Пароль для точки доступа (минимум 8 символов)

WiFiServer ap_server(AP_PORT); // Сервер для точки доступа

void setup() {
  Serial.begin(115200);
  delay(1000);  // Короткая задержка для стабилизации
  
  // Инициализация I2C в режиме Master
  Wire.begin();
  
  // Запуск точки доступа
  WiFi.mode(WIFI_AP); // Режим только точка доступа
  WiFi.softAP(ap_ssid, ap_password);
  Serial.println();
  Serial.print("Access Point started, IP address: ");
  Serial.println(WiFi.softAPIP());
  
  // Запуск сервера
  ap_server.begin();
  Serial.println("Socket server started on port: " + String(AP_PORT));
}

// Обработка Serial порта
void readSerialPort() {
  if (Serial.available() > 0) {
    Wire.beginTransmission(SLAVE_ADDR);
    while (Serial.available()) {
      char nextChar = Serial.read();
      Wire.write(nextChar);
    }
    byte error = Wire.endTransmission();
    if (error == 0) {
      Serial.println("Data sent via I2C from Serial");
    } else {
      Serial.print("I2C transmission error from Serial: ");
      Serial.println(error);
    }
  }
}

// Функция для обработки клиентов сокет-сервера
void handleClient(WiFiClient &client) {
  if (client.connected()) {
    while (client.connected()) {
      readSerialPort(); // Обработка Serial порта
      if (client.available() > 0) {
        Wire.beginTransmission(SLAVE_ADDR);
        while (client.available()) {
            char nextChar = client.read();
            Wire.write(nextChar);
        }
        byte error = Wire.endTransmission();
        if (error == 0) {
            Serial.println("Data sent via I2C from socket");
        } else {
            Serial.print("I2C transmission error from socket: ");
            Serial.println(error);
        }
      }
      delay(100);
    }
    client.stop();
    Serial.println("Client disconnected");
  }
}

void loop() {
  readSerialPort(); // Обработка Serial порта
  
  // Обработка клиентов точки доступа
  WiFiClient ap_client = ap_server.available();
  if (ap_client) {
    Serial.println("New client connected from Access Point");
    handleClient(ap_client);
  }
  delay(100); // Небольшая задержка для стабильности
}
