@echo off
cd "C:\JavaProjects\WifiKeybord_ESP8266\1_ArduinoKeybord\ServerClipboard"
start "ServerClipboard" /B "C:\Java\AxiomJDK-21\bin\javaw.exe" "-javaagent:C:\JetBrains\IntelliJ IDEA 2025.1\lib\idea_rt.jar=62777" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -classpath "C:\JavaProjects\WifiKeybord_ESP8266\1_ArduinoKeybord\ServerClipboard\target\classes;C:\JavaProjects\WifiKeybord_ESP8266\1_ArduinoKeybord\ServerClipboard\lib\json-20230227.jar" ru.miacomsoft.Main
exit