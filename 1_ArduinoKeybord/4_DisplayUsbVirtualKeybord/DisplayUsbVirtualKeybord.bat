@echo off
chcp 65001 >nul
title Камера с виртуальной клавиатурой

echo Запуск камеры с виртуальной клавиатурой...
echo.

set JAVA_HOME=C:\Java\AxiomJDK-21
set CLASSPATH=C:\JavaProjects\WifiKeybord_ESP8266\1_ArduinoKeybord\4_DisplayUsbVirtualKeybord\target\classes
set CLASSPATH=%CLASSPATH%;C:\Users\Myasnikov\.m2\repository\org\bytedeco\javacv-platform\1.5.9\javacv-platform-1.5.9.jar
set CLASSPATH=%CLASSPATH%;C:\Users\Myasnikov\.m2\repository\org\bytedeco\opencv-platform\4.7.0-1.5.9\opencv-platform-4.7.0-1.5.9.jar
set CLASSPATH=%CLASSPATH%;C:\Users\Myasnikov\.m2\repository\org\bytedeco\ffmpeg-platform\6.0-1.5.9\ffmpeg-platform-6.0-1.5.9.jar

"%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -classpath "%CLASSPATH%" ru.miacomsoft.JavaCVCameraWithKeyboard

echo.
echo Программа завершена.
echo Закрытие консоли через 3 секунды...
timeout /t 3 /nobreak >nul
exit