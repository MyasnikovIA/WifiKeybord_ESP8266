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
#define BAUD_RATE 115200  // Скорость Serial

void setup() {
  Serial.begin(BAUD_RATE);
  delay(1000);  // Короткая задержка для стабилизации
  
  // Инициализация I2C в режиме Master
  Wire.begin();
  
  Serial.println();
  Serial.println("ESP8266 I2C-Serial Bridge ready");
  Serial.println("Listening for Serial data...");
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
  delay(10); // Небольшая задержка для стабильности
}
