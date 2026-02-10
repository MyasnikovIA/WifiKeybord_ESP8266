; Перехват Caps Lock для смены раскладки (Alt+Shift)
CapsLock::
    Send, {Alt down}{Shift down}
    Sleep 50  ; Короткая задержка для имитации нажатия
    Send, {Shift up}{Alt up}
return

; Отключение стандартного действия Caps Lock
SetCapsLockState, AlwaysOff