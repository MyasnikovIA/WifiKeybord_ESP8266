#include <Wire.h>
#include "Keyboard.h"

// Для ATmega32U4 (Master) - модифицированный код из третьего ответа:
// ATmega32U4 (Master)      ESP8266 (Slave)       Подключение
// -----------------------------------------------------------
// PD1 (SDA)               GPIO4 (SDA)          ------> Синяя линия
// PD0 (SCL)               GPIO5 (SCL)          ------> Желтая линия
// GND                       GND                  ------> Черная линия
// 3.3V/VCC                VIN/3.3V (опционально) --> Красная линия (если нужно питать ESP)

#define I2C_ADDRESS 18  // Должен совпадать с адресом в Master
#define CHAR_DELAY 500   // Задержка между символами в миллисекундах

void receiveEvent(int howMany) {
    while (Wire.available()) {
        char c = Wire.read();
        // Если пришела строка #128 то выполнить команду Keyboard.press(KEY_LEFT_SHIFT)
        // Если пришла строка #0  то выполнить команду Keyboard.releaseAll(); 
        // 
        //Аналогично сделать со всеми спецсимволами
        // KEY_LEFT_CTRL  0x80  128
        // KEY_LEFT_SHIFT  0x81  129
        // KEY_LEFT_ALT  0x82  130
        // KEY_LEFT_GUI  0x83  131
        // KEY_RIGHT_CTRL  0x84  132
        // KEY_RIGHT_SHIFT 0x85  133
        // KEY_RIGHT_ALT 0x86  134
        // KEY_RIGHT_GUI 0x87  135
        // Keyboard.print(c);
        Keyboard.write(c);
        delay(CHAR_DELAY);
    }
}

void setup() {
    delay(5000);
    Keyboard.begin();
    Wire.begin(I2C_ADDRESS);  // Инициализация I2C в режиме Slave
    Wire.onReceive(receiveEvent);
}

void loop() {
  delay(1000);
}