// Обработчик двойного клика
document.addEventListener('dblclick', async function(event) {
  // Проверяем, кликнули ли на текстовый элемент
  const target = event.target;
  const isTextInput =
    target.tagName === 'INPUT' &&
    (target.type === 'text' || target.type === 'search' || target.type === 'url' ||
     target.type === 'tel' || target.type === 'email' || target.type === 'password') ||
    target.tagName === 'TEXTAREA' ||
    target.isContentEditable;

  if (!isTextInput) {
    return;
  }

  try {
    // Читаем текст из буфера обмена
    const text = await navigator.clipboard.readText();

    // Вставляем текст в поле
    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') {
      const start = target.selectionStart;
      const end = target.selectionEnd;
      const currentValue = target.value;

      target.value = currentValue.substring(0, start) +
                     text +
                     currentValue.substring(end);

      // Обновляем позицию курсора
      target.selectionStart = target.selectionEnd = start + text.length;
    } else if (target.isContentEditable) {
      // Для contenteditable элементов
      document.execCommand('insertText', false, text);
    }

    // Триггерим события для обновления состояния
    target.dispatchEvent(new Event('input', { bubbles: true }));
    target.dispatchEvent(new Event('change', { bubbles: true }));

    // Визуальная обратная связь
    showFeedback(target);

    // Эмулируем нажатие Enter после небольшой задержки
    setTimeout(() => {
      emulateEnterKey(target);
    }, 50);

  } catch (error) {
    console.error('Ошибка при вставке из буфера:', error);
    showErrorFeedback(target);
  }
});

// Функция для эмуляции нажатия клавиши Enter
function emulateEnterKey(element) {
  // Создаем событие нажатия клавиши Enter
  const enterEvent = new KeyboardEvent('keydown', {
    key: 'Enter',
    code: 'Enter',
    keyCode: 13,
    which: 13,
    bubbles: true,
    cancelable: true
  });

  // Диспатчим события в следующей последовательности:
  // 1. keydown
  const keydownDispatched = element.dispatchEvent(enterEvent);

  if (!keydownDispatched) {
    console.log('Событие keydown было предотвращено');
    return;
  }

  // 2. keypress (для совместимости)
  const keypressEvent = new KeyboardEvent('keypress', {
    key: 'Enter',
    code: 'Enter',
    keyCode: 13,
    which: 13,
    bubbles: true,
    cancelable: true
  });
  element.dispatchEvent(keypressEvent);

  // 3. keyup
  setTimeout(() => {
    const keyupEvent = new KeyboardEvent('keyup', {
      key: 'Enter',
      code: 'Enter',
      keyCode: 13,
      which: 13,
      bubbles: true,
      cancelable: false
    });
    element.dispatchEvent(keyupEvent);

    // Также диспатчим событие submit если элемент находится внутри формы
    if (element.form) {
      const submitEvent = new Event('submit', {
        bubbles: true,
        cancelable: true
      });
      const submitDispatched = element.form.dispatchEvent(submitEvent);

      if (submitDispatched && !submitEvent.defaultPrevented) {
        // Если форма не была предотвращена, отправляем ее
        element.form.submit();
      }
    }
  }, 10);
}

// Функция для отображения визуальной обратной связи
function showFeedback(element) {
  const originalBorder = element.style.border;
  const originalOutline = element.style.outline;

  // Мигание зеленой рамкой
  element.style.border = '2px solid #4CAF50';
  element.style.outline = 'none';

  setTimeout(() => {
    element.style.border = originalBorder;
    element.style.outline = originalOutline;
  }, 300);
}

// Функция для отображения ошибки
function showErrorFeedback(element) {
  const originalBorder = element.style.border;
  const originalOutline = element.style.outline;

  // Мигание красной рамкой
  element.style.border = '2px solid #f44336';
  element.style.outline = 'none';

  setTimeout(() => {
    element.style.border = originalBorder;
    element.style.outline = originalOutline;
  }, 300);
}

// Обработчик клавиш для быстрой вставки (опционально)
document.addEventListener('keydown', async function(event) {
  // Ctrl+Shift+V для вставки в активное поле
  if (event.ctrlKey && event.shiftKey && event.key === 'V') {
    const activeElement = document.activeElement;
    const isTextInput =
      activeElement.tagName === 'INPUT' &&
      (activeElement.type === 'text' || activeElement.type === 'search' ||
       activeElement.type === 'url' || activeElement.type === 'tel' ||
       activeElement.type === 'email' || activeElement.type === 'password') ||
      activeElement.tagName === 'TEXTAREA' ||
      activeElement.isContentEditable;

    if (isTextInput) {
      event.preventDefault();
      try {
        const text = await navigator.clipboard.readText();

        if (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA') {
          const start = activeElement.selectionStart;
          const end = activeElement.selectionEnd;
          const currentValue = activeElement.value;

          activeElement.value = currentValue.substring(0, start) +
                               text +
                               currentValue.substring(end);

          activeElement.selectionStart = activeElement.selectionEnd = start + text.length;
        } else if (activeElement.isContentEditable) {
          document.execCommand('insertText', false, text);
        }

        activeElement.dispatchEvent(new Event('input', { bubbles: true }));
        activeElement.dispatchEvent(new Event('change', { bubbles: true }));
        showFeedback(activeElement);

        // Эмулируем нажатие Enter для сочетания клавиш
        setTimeout(() => {
          emulateEnterKey(activeElement);
        }, 50);

      } catch (error) {
        console.error('Ошибка при вставке из буфера:', error);
        showErrorFeedback(activeElement);
      }
    }
  }
});