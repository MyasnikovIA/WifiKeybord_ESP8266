# pip install pyserial

import serial
import time
import sys
import select
import re
from typing import Dict

class KeyCodes:
    KEY_CODES: Dict[str, int] = {}
    KEY_CODES_RUS: Dict[str, int] = {}
    
    @staticmethod
    def is_cyrillic(char: str) -> bool:
        """Проверяет, является ли символ кириллическим"""
        if len(char) == 0:
            return False
        code = ord(char)
        return (0x0400 <= code <= 0x04FF) or (0x0500 <= code <= 0x052F)
    
    @staticmethod
    def init_key_codes():
        """Инициализация кодов клавиш"""
        # Английские строчные (a-z) с кодами строчных (97-122)
        KeyCodes.put_byte("A", 97);   KeyCodes.put_byte_rus("ф", 97)
        KeyCodes.put_byte("B", 98);   KeyCodes.put_byte_rus("и", 98)
        KeyCodes.put_byte("C", 99);   KeyCodes.put_byte_rus("с", 99)
        KeyCodes.put_byte("D", 100);  KeyCodes.put_byte_rus("в", 100)
        KeyCodes.put_byte("E", 101);  KeyCodes.put_byte_rus("у", 101)
        KeyCodes.put_byte("F", 102);  KeyCodes.put_byte_rus("а", 102)
        KeyCodes.put_byte("G", 103);  KeyCodes.put_byte_rus("п", 103)
        KeyCodes.put_byte("H", 104);  KeyCodes.put_byte_rus("р", 104)
        KeyCodes.put_byte("I", 105);  KeyCodes.put_byte_rus("ш", 105)
        KeyCodes.put_byte("J", 106);  KeyCodes.put_byte_rus("о", 106)
        KeyCodes.put_byte("K", 107);  KeyCodes.put_byte_rus("л", 107)
        KeyCodes.put_byte("L", 108);  KeyCodes.put_byte_rus("д", 108)
        KeyCodes.put_byte("M", 109);  KeyCodes.put_byte_rus("ь", 109)
        KeyCodes.put_byte("N", 110);  KeyCodes.put_byte_rus("т", 110)
        KeyCodes.put_byte("O", 111);  KeyCodes.put_byte_rus("щ", 111)
        KeyCodes.put_byte("P", 112);  KeyCodes.put_byte_rus("з", 112)
        KeyCodes.put_byte("Q", 113);  KeyCodes.put_byte_rus("й", 113)
        KeyCodes.put_byte("R", 114);  KeyCodes.put_byte_rus("к", 114)
        KeyCodes.put_byte("S", 115);  KeyCodes.put_byte_rus("ы", 115)
        KeyCodes.put_byte("T", 116);  KeyCodes.put_byte_rus("е", 116)
        KeyCodes.put_byte("U", 117);  KeyCodes.put_byte_rus("г", 117)
        KeyCodes.put_byte("V", 118);  KeyCodes.put_byte_rus("м", 118)
        KeyCodes.put_byte("W", 119);  KeyCodes.put_byte_rus("ц", 119)
        KeyCodes.put_byte("X", 120);  KeyCodes.put_byte_rus("ч", 120)
        KeyCodes.put_byte("Y", 121);  KeyCodes.put_byte_rus("н", 121)
        KeyCodes.put_byte("Z", 122);  KeyCodes.put_byte_rus("я", 122)

        # Английские заглавные (A-Z) с кодами заглавных (65-90)
        KeyCodes.put_byte("a", 65);   KeyCodes.put_byte_rus("Ф", 65)
        KeyCodes.put_byte("b", 66);   KeyCodes.put_byte_rus("И", 66)
        KeyCodes.put_byte("c", 67);   KeyCodes.put_byte_rus("С", 67)
        KeyCodes.put_byte("d", 68);   KeyCodes.put_byte_rus("В", 68)
        KeyCodes.put_byte("e", 69);   KeyCodes.put_byte_rus("У", 69)
        KeyCodes.put_byte("f", 70);   KeyCodes.put_byte_rus("А", 70)
        KeyCodes.put_byte("g", 71);   KeyCodes.put_byte_rus("П", 71)
        KeyCodes.put_byte("h", 72);   KeyCodes.put_byte_rus("Р", 72)
        KeyCodes.put_byte("i", 73);   KeyCodes.put_byte_rus("Ш", 73)
        KeyCodes.put_byte("j", 74);   KeyCodes.put_byte_rus("О", 74)
        KeyCodes.put_byte("k", 75);   KeyCodes.put_byte_rus("Л", 75)
        KeyCodes.put_byte("l", 76);   KeyCodes.put_byte_rus("Д", 76)
        KeyCodes.put_byte("m", 77);   KeyCodes.put_byte_rus("Ь", 77)
        KeyCodes.put_byte("n", 78);   KeyCodes.put_byte_rus("Т", 78)
        KeyCodes.put_byte("o", 79);   KeyCodes.put_byte_rus("Щ", 79)
        KeyCodes.put_byte("p", 80);   KeyCodes.put_byte_rus("З", 80)
        KeyCodes.put_byte("q", 81);   KeyCodes.put_byte_rus("Й", 81)
        KeyCodes.put_byte("r", 82);   KeyCodes.put_byte_rus("К", 82)
        KeyCodes.put_byte("s", 83);   KeyCodes.put_byte_rus("Ы", 83)
        KeyCodes.put_byte("t", 84);   KeyCodes.put_byte_rus("Е", 84)
        KeyCodes.put_byte("u", 85);   KeyCodes.put_byte_rus("Г", 85)
        KeyCodes.put_byte("v", 86);   KeyCodes.put_byte_rus("М", 86)
        KeyCodes.put_byte("w", 87);   KeyCodes.put_byte_rus("Ц", 87)
        KeyCodes.put_byte("x", 88);   KeyCodes.put_byte_rus("Ч", 88)
        KeyCodes.put_byte("y", 89);   KeyCodes.put_byte_rus("Н", 89)
        KeyCodes.put_byte("z", 90);   KeyCodes.put_byte_rus("Я", 90)

        # Спецсимволы
        KeyCodes.put_byte("\"", 34);   KeyCodes.put_byte_rus("Э", 34)
        KeyCodes.put_byte("#", 35);    KeyCodes.put_byte_rus("№", 35)
        KeyCodes.put_byte("$", 36);    KeyCodes.put_byte_rus(";", 36)
        KeyCodes.put_byte("%", 37);    KeyCodes.put_byte_rus("%", 37)
        KeyCodes.put_byte("&", 38);    KeyCodes.put_byte_rus("?", 38)
        KeyCodes.put_byte("'", 39);    KeyCodes.put_byte_rus("э", 39)
        KeyCodes.put_byte("(", 40);    KeyCodes.put_byte_rus(")", 40)
        KeyCodes.put_byte(")", 41);    KeyCodes.put_byte_rus("(", 41)
        KeyCodes.put_byte("*", 42);    KeyCodes.put_byte_rus("*", 42)
        KeyCodes.put_byte("+", 43);    KeyCodes.put_byte_rus("+", 43)
        KeyCodes.put_byte(",", 44);    KeyCodes.put_byte_rus("б", 44)
        KeyCodes.put_byte("-", 45);    KeyCodes.put_byte_rus("-", 45)
        KeyCodes.put_byte(".", 46);    KeyCodes.put_byte_rus("ю", 46)
        KeyCodes.put_byte("/", 47);    KeyCodes.put_byte_rus(".", 47)
        KeyCodes.put_byte(":", 58);    KeyCodes.put_byte_rus("Ж", 58)
        KeyCodes.put_byte(";", 59);    # ???
        KeyCodes.put_byte("<", 60);    KeyCodes.put_byte_rus("Б", 60)
        KeyCodes.put_byte("=", 61);    KeyCodes.put_byte_rus("=", 61)
        KeyCodes.put_byte(">", 62);    KeyCodes.put_byte_rus("Ю", 62)
        KeyCodes.put_byte("?", 63);    KeyCodes.put_byte_rus(",", 63)
        KeyCodes.put_byte("@", 64);    KeyCodes.put_byte_rus("\"", 64)
        KeyCodes.put_byte("[", 91);    KeyCodes.put_byte_rus("х", 91)
        KeyCodes.put_byte("\\", 92);   KeyCodes.put_byte_rus("\\", 92)
        KeyCodes.put_byte("]", 93);    KeyCodes.put_byte_rus("ъ", 93)
        KeyCodes.put_byte("^", 94);    KeyCodes.put_byte_rus(":", 94)
        KeyCodes.put_byte("_", 95);    KeyCodes.put_byte_rus("_", 95)
        KeyCodes.put_byte("`", 96);    KeyCodes.put_byte_rus("ё", 96)
        KeyCodes.put_byte("{", 123);   KeyCodes.put_byte_rus("Х", 123)
        KeyCodes.put_byte("|", 124);   KeyCodes.put_byte_rus("/", 124)
        KeyCodes.put_byte("}", 125);   KeyCodes.put_byte_rus("Ъ", 125)
        KeyCodes.put_byte("~", 126);   KeyCodes.put_byte_rus("Ё", 126)

        # Цифры (0-9)
        for i in range(48, 58):
            digit = chr(i)
            KeyCodes.KEY_CODES[digit] = i
            KeyCodes.KEY_CODES_RUS[digit] = i

        KeyCodes.KEY_CODES_RUS["ж"] = 59
        KeyCodes.KEY_CODES_RUS["з"] = 112
        KeyCodes.KEY_CODES_RUS["ф"] = 140
        KeyCodes.KEY_CODES_RUS["и"] = 141
        KeyCodes.KEY_CODES_RUS["с"] = 142
        KeyCodes.KEY_CODES_RUS["в"] = 143
        KeyCodes.KEY_CODES_RUS["у"] = 144
        KeyCodes.KEY_CODES_RUS["а"] = 145
        KeyCodes.KEY_CODES_RUS["п"] = 146
        KeyCodes.KEY_CODES_RUS["р"] = 147
        KeyCodes.KEY_CODES_RUS["ш"] = 148
        KeyCodes.KEY_CODES_RUS["о"] = 149
        KeyCodes.KEY_CODES_RUS["л"] = 150
        KeyCodes.KEY_CODES_RUS["ь"] = 152
        KeyCodes.KEY_CODES_RUS["т"] = 153
        KeyCodes.KEY_CODES_RUS["щ"] = 154
        KeyCodes.KEY_CODES_RUS["з"] = 155
        KeyCodes.KEY_CODES_RUS["й"] = 156
        KeyCodes.KEY_CODES_RUS["к"] = 157
        KeyCodes.KEY_CODES_RUS["ы"] = 158
        KeyCodes.KEY_CODES_RUS["е"] = 159
        KeyCodes.KEY_CODES_RUS["г"] = 160
        KeyCodes.KEY_CODES_RUS["м"] = 161
        KeyCodes.KEY_CODES_RUS["ц"] = 162
        KeyCodes.KEY_CODES_RUS["ч"] = 163
        KeyCodes.KEY_CODES_RUS["н"] = 164
        KeyCodes.KEY_CODES_RUS["я"] = 165
        KeyCodes.KEY_CODES_RUS["1"] = 166
        KeyCodes.KEY_CODES_RUS["2"] = 167
        KeyCodes.KEY_CODES_RUS["3"] = 168
        KeyCodes.KEY_CODES_RUS["4"] = 169
        KeyCodes.KEY_CODES_RUS["5"] = 170
        KeyCodes.KEY_CODES_RUS["6"] = 171
        KeyCodes.KEY_CODES_RUS["7"] = 172
        KeyCodes.KEY_CODES_RUS["8"] = 173
        KeyCodes.KEY_CODES_RUS["9"] = 174
        KeyCodes.KEY_CODES_RUS["0"] = 175
        KeyCodes.KEY_CODES_RUS["\n"] = 176  # Enter
        KeyCodes.KEY_CODES_RUS["0"] = 175

        KeyCodes.KEY_CODES_RUS["\b"] = 178  # BackSpace
        KeyCodes.KEY_CODES_RUS["\t"] = 179  # Tab
        KeyCodes.KEY_CODES_RUS[" "] = 180   # Space
        KeyCodes.KEY_CODES_RUS["-"] = 181
        KeyCodes.KEY_CODES_RUS["="] = 182
        KeyCodes.KEY_CODES_RUS["х"] = 183
        KeyCodes.KEY_CODES_RUS["ъ"] = 184
        KeyCodes.KEY_CODES_RUS["\\"] = 185
        KeyCodes.KEY_CODES_RUS["\\"] = 186
        KeyCodes.KEY_CODES_RUS["ж"] = 187
        KeyCodes.KEY_CODES_RUS["э"] = 188
        KeyCodes.KEY_CODES_RUS["ё"] = 189
        KeyCodes.KEY_CODES_RUS["б"] = 190
        KeyCodes.KEY_CODES_RUS["ю"] = 191
        KeyCodes.KEY_CODES_RUS["."] = 192
        KeyCodes.KEY_CODES_RUS["CapsLock"] = 193
        KeyCodes.KEY_CODES_RUS[" F1"] = 194
        KeyCodes.KEY_CODES_RUS[" F2"] = 195
        KeyCodes.KEY_CODES_RUS[" F3"] = 196
        KeyCodes.KEY_CODES_RUS[" F4"] = 197
        KeyCodes.KEY_CODES_RUS[" F5"] = 198
        KeyCodes.KEY_CODES_RUS[" F6"] = 199
        KeyCodes.KEY_CODES_RUS[" F7"] = 200
        KeyCodes.KEY_CODES_RUS[" F8"] = 201
        KeyCodes.KEY_CODES_RUS[" F9"] = 202
        KeyCodes.KEY_CODES_RUS[" F10"] = 203
        KeyCodes.KEY_CODES_RUS[" F11"] = 204
        KeyCodes.KEY_CODES_RUS[" F12"] = 205

        KeyCodes.KEY_CODES_RUS["inser"] = 209
        KeyCodes.KEY_CODES_RUS["home"] = 210
        KeyCodes.KEY_CODES_RUS["PageUp"] = 211
        KeyCodes.KEY_CODES_RUS["del"] = 212
        KeyCodes.KEY_CODES_RUS["end"] = 213
        KeyCodes.KEY_CODES_RUS["PageDown"] = 214
        KeyCodes.KEY_CODES_RUS["стрелка право"] = 215
        KeyCodes.KEY_CODES_RUS["стрелка лево"] = 216
        KeyCodes.KEY_CODES_RUS["стрелка низ"] = 217
        KeyCodes.KEY_CODES_RUS["стрелка верх"] = 218
        KeyCodes.KEY_CODES_RUS["NumLock"] = 219
        KeyCodes.KEY_CODES_RUS["/"] = 220
        KeyCodes.KEY_CODES_RUS["*"] = 221
        KeyCodes.KEY_CODES_RUS["-"] = 221
        KeyCodes.KEY_CODES_RUS["+"] = 222
        KeyCodes.KEY_CODES_RUS["enter"] = 223  # Enter (доп клавиатура)

        KeyCodes.KEY_CODES_RUS["end"] = 225           # end (доп клавиатура)
        KeyCodes.KEY_CODES_RUS["стрелка низ"] = 226   # (доп клавиатура)
        KeyCodes.KEY_CODES_RUS["PageDown"] = 227      # (доп клавиатура)
        KeyCodes.KEY_CODES_RUS["стрелка лево"] = 228  # (доп клавиатура)
        KeyCodes.KEY_CODES_RUS["5"] = 229             # (доп клавиатура) 5 если включен NumLock
        KeyCodes.KEY_CODES_RUS["стрелка право"] = 230  # (доп клавиатура)
        KeyCodes.KEY_CODES_RUS["home"] = 231          # (доп клавиатура)
        KeyCodes.KEY_CODES_RUS["стрелка верх"] = 232  # (доп клавиатура)
        KeyCodes.KEY_CODES_RUS["PageUp"] = 233        # (доп клавиатура)
        KeyCodes.KEY_CODES_RUS["inser"] = 234         # (доп клавиатура)
        KeyCodes.KEY_CODES_RUS["del"] = 235           # (доп клавиатура)

        KeyCodes.KEY_CODES_RUS["\\"] = 236
        KeyCodes.KEY_CODES_RUS["ContextMenu"] = 237
        KeyCodes.KEY_CODES_RUS["Fn"] = 238  # предположительно

        KeyCodes.KEY_CODES_RUS["win"] = 135
        KeyCodes.KEY_CODES_RUS["CapsLock"] = 134

        # Управляющие клавиши
        KeyCodes.put_byte(" ", 32)
        KeyCodes.put_byte("!", 33)

    @staticmethod
    def put_byte(char_str: str, code: int):
        """Добавляет код для символа"""
        KeyCodes.KEY_CODES[char_str] = code

    @staticmethod
    def put_byte_rus(char_str: str, code: int):
        """Добавляет код для русского символа"""
        KeyCodes.KEY_CODES_RUS[char_str] = code

# Инициализация кодов клавиш
KeyCodes.init_key_codes()

def send_byte(port: serial.Serial, value: int):
    """Отправляет байт через последовательный порт"""
    if value < 0 or value > 255:
        raise ValueError(f"Value must be in range 0-255--{value}")
    
    port.write(bytes([value]))
    time.sleep(0.1)

def main():
    """Основная функция программы"""
    message = """using System;
using System.Linq;

class StatsCalculator
{
    public static string Process(string input)
    {
        // Разбиваем строку на массив чисел
        string[] numbers = input.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        
        int aboveZero = 0;
        int belowZero = 0;
        int zero = 0;
        
        // Проходим по всем числам и считаем категории
        foreach (string number in numbers)
        {
            int num = int.Parse(number);
            
            if (num > 0)
                aboveZero++;
            else if (num < 0)
                belowZero++;
            else
                zero++;
        }
        
        // Формируем строку результата в нужном формате
        return $\"выше нуля: {aboveZero}, ниже нуля: {belowZero}, равна нулю: {zero}\";
    }
}"""

    # Очистка сообщения от управляющих символов (кроме перевода строки)
    message = re.sub(r'[\x00-\x08\x0B-\x1F\x7F]', '', message)
    
    try:
        # Открытие последовательного порта
        port = serial.Serial('COM24', 9600, bytesize=8, parity='N', stopbits=1, timeout=0)
        print(f"Port {port.port} opened successfully")
    except serial.SerialException as e:
        print(f"Error opening serial port: {e}")
        return
    
    is_english = True
    is_paused = False
    is_russian = False

    print("Press Enter to start/pause transmission...")
    
    # Ожидание начального нажатия Enter
    input()
    
    for c in message:
        # Проверка нажатия Enter в консоли без блокировки
        if sys.stdin in select.select([sys.stdin], [], [], 0)[0]:
            line = sys.stdin.readline().strip()
            if line == "":
                is_paused = not is_paused
                print(f"Transmission {'paused' if is_paused else 'resumed'}." +
                      f" Press Enter to {'resume' if is_paused else 'pause'}...")
                
                if is_paused:
                    # Ожидание Enter для возобновления
                    while is_paused:
                        if sys.stdin in select.select([sys.stdin], [], [], 0)[0]:
                            pause_input = sys.stdin.readline().strip()
                            if pause_input == "":
                                is_paused = False
                                print("Transmission resumed. Press Enter to pause...")
                        time.sleep(0.1)

        if is_paused:
            continue
            
        print(c, end='', flush=True)
        
        if c == '\n':  # Перевод строки
            time.sleep(0.1)
            send_byte(port, 32)
            time.sleep(0.1)
            
        is_russian = KeyCodes.is_cyrillic(c)
        
        if is_russian:
            if is_english:
                is_english = False
                time.sleep(0.2)
                send_byte(port, 134)  # CapsLock для переключения на русский
                time.sleep(0.2)
                
            if str(c) in KeyCodes.KEY_CODES_RUS:
                symb = KeyCodes.KEY_CODES_RUS[str(c)]
                send_byte(port, symb)
        else:
            if not is_english:
                is_english = True
                time.sleep(0.2)
                send_byte(port, 134)  # CapsLock для переключения на английский
                time.sleep(0.2)
                
            if str(c) in KeyCodes.KEY_CODES:
                send_byte(port, KeyCodes.KEY_CODES[str(c)])
            else:
                # Если символ не найден в таблице, отправляем как есть
                send_byte(port, ord(c))
                
        if c == '\n':  # Перевод строки
            time.sleep(0.3)
            send_byte(port, 231)  # Home
            time.sleep(0.3)
    
    # Возврат к английской раскладке в конце
    if not is_english:
        time.sleep(0.2)
        send_byte(port, 134)  # CapsLock
        time.sleep(0.2)
        
    port.close()
    print("\nTransmission completed.")

if __name__ == "__main__":
    main()
