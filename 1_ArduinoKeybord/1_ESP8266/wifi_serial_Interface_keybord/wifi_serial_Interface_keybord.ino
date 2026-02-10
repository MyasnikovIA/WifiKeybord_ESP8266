#include <Wire.h>
#include <ESP8266WiFi.h>
#include <WiFiClient.h>
#include <WiFiServer.h>


// 192.168.31.193:8200

// Для ATmega32U4 (Master) - модифицированный код из третьего ответа:
// ATmega32U4 (Master)      ESP8266 (Slave)       Подключение
// -----------------------------------------------------------
// PD1 (SDA)               GPIO4 (SDA)            ------> Синяя линия
// PD0 (SCL)               GPIO5 (SCL)            ------> Желтая линия
// GND                       GND                  ------> Черная линия
// 3.3V/VCC                VIN/3.3V (опционально) --> Красная линия (если нужно питать ESP)


#define SLAVE_ADDR 18  // Адрес slave устройства (ATmega32U4)
#define PORT 8200      // Порт для сокет-сервера

const char* ssid = "XXXXXX";     // Замените на ваш SSID
const char* password = "XXXXXXXXX"; // Замените на ваш пароль

WiFiServer server(PORT);

void setup() {
  Serial.begin(9600);
  delay(1000);  // Короткая задержка для стабилизации
  
  // Инициализация I2C в режиме Master
  Wire.begin();
  
  // Подключение к WiFi
  WiFi.begin(ssid, password);
  Serial.println();
  Serial.println("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  Serial.print("Connected, IP address: ");
  Serial.println(WiFi.localIP());
  
  // Запуск сервера
  server.begin();
  Serial.println("Socket server started");
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

void loop() {
  
  readSerialPort(); // Обработка Serial порта
  // Обработка сокет-клиентов
  WiFiClient client = server.available();
  if (client) {
    Serial.println("New client connected");
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
  delay(100); // Небольшая задержка для стабильности
}
