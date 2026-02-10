import cv2
import numpy as np

# Open the default camera
cam = cv2.VideoCapture(0)

cam.set(cv2.CAP_PROP_FRAME_WIDTH, 1920)
cam.set(cv2.CAP_PROP_FRAME_HEIGHT, 820)
cam.set(cv2.CAP_PROP_FPS, 30)

# Get the default frame width and height
frame_width = int(cam.get(cv2.CAP_PROP_FRAME_WIDTH))
frame_height = int(cam.get(cv2.CAP_PROP_FRAME_HEIGHT))

low_threshold = 10
high_threshold = 250

# Параметры для контрастности
alpha = 1.5  # Контрастность (1.0 - оригинал, >1.0 - увеличить)
beta = 0     # Яркость

print("Управление:")
print("'+' - увеличить контрастность")
print("'-' - уменьшить контрастность")
print("'a' - увеличить яркость")
print("'z' - уменьшить яркость")
print("'q' - выход")
max(alpha - 0.1, 0.1)
max(alpha - 0.1, 0.1)
max(alpha - 0.1, 0.1)
max(alpha - 0.1, 0.1)
max(alpha - 0.1, 0.1)
while True:
    ret, frame = cam.read()
    if not ret:
        break
        
    resized_frame = cv2.resize(frame, (1920, 1000))

    # Конвертируем в grayscale
    gray = cv2.cvtColor(resized_frame, cv2.COLOR_BGR2GRAY)
    
    # Увеличиваем контрастность
    contrasted_gray = cv2.convertScaleAbs(gray, alpha=alpha, beta=beta)
    
    # Применяем фильтр Канни к изображению с увеличенной контрастностью
    edges = cv2.Canny(contrasted_gray, low_threshold, high_threshold)
    
    # Создаем изображение только с контурами (белые на черном)
    contours_only = np.zeros_like(resized_frame)
    contours_only[edges > 0] = (255, 255, 255)
    
    # Создаем комбинированное изображение
    #combined = np.hstack([resized_frame, 
    #                     cv2.cvtColor(contrasted_gray, cv2.COLOR_GRAY2BGR),
    #                     contours_only])

    # Добавляем информацию о параметрах
    #info_text = f"Contrast: {alpha:.1f}, Brightness: {beta}"
    #cv2.putText(combined, info_text, (10, 30), 
    #           cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)

    # Display the captured frame
    #cv2.imshow('Original | Contrasted Gray | Canny Contours', combined)
    cv2.imshow('Original | Contrasted Gray | Canny Contours', contrasted_gray)

    # Обработка клавиш
    key = cv2.waitKey(1) & 0xFF
    if key == ord('q'):
        break
    elif key == ord('+'):
        alpha = min(alpha + 0.1, 3.0)
    elif key == ord('-'):
        alpha = max(alpha - 0.1, 0.1)
    elif key == ord('a'):
        beta = min(beta + 10, 100)
    elif key == ord('z'):
        beta = max(beta - 10, -100)

# Release the capture object
cam.release()
cv2.destroyAllWindows()
