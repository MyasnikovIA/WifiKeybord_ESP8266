// content.js - Multi-Block Text Copier (с поддержкой имени источника)
(function() {
  'use strict';

  var isSendConsole = false;
  // ========== КОНФИГУРАЦИЯ ==========
  const CONFIG = {
    useSendPrefix: true,
    serverAddress: '192.168.15.3:8080',
    autoSend: true,
    showNotification: false,
    notificationDuration: 2000,
    // Имя источника (можно изменить на любое другое)
    authorName: 'ChromeExtension'
  };

  // ========== ПЕРЕМЕННЫЕ ДЛЯ РЕЖИМА ВЫБОРА ==========
  let isElementSelectionMode = false;
  let highlightedElement = null;
  let originalCursor = '';

  // ========== Основная функция копирования предопределённых блоков ==========
  function copyBlocks() {
    const blocks = [
      { selector: '[aria-labelledby="tab-description"]', name: 'ОПИСАНИЕ' },
      { selector: '.view-lines', name: 'КОД' }
    ];

    let allText = '';

    blocks.forEach((block) => {
      const element = document.querySelector(block.selector);
      if (element) {
        const blockText = convertToText(element);
        if (blockText.trim()) {
          if (allText) allText += '\n\n';
          if (block.name === 'КОД') {
            allText += `// === ${block.name.toUpperCase()} ===\n${transformCode(blockText)}`;
          } else {
            allText += `// === ${block.name.toUpperCase()} ===\n${blockText}`;
          }
        }
      }
    });

    if (allText) {
      copyAndSendToServer(allText, 'предопределённые блоки');
    } else {
      if (isSendConsole) console.error('❌ Блоки не найдены');
    }
  }

  // ========== УНИВЕРСАЛЬНАЯ ФУНКЦИЯ КОПИРОВАНИЯ И ОТПРАВКИ ==========
  function copyAndSendToServer(text, sourceName = 'текст') {
    if (!text || text.trim() === '') {
      if (isSendConsole) console.error('❌ Нет текста для копирования');
      return false;
    }

    let textToCopy = text;
    if (CONFIG.useSendPrefix) {
      textToCopy = `SEND(${CONFIG.serverAddress}):${text}`;
      if (isSendConsole) console.log(`✅ Добавлен SEND префикс для ${sourceName}`);
    }

    navigator.clipboard.writeText(textToCopy).then(() => {
      if (isSendConsole) console.log(`✅ ${sourceName} скопирован в буфер`);

      if (CONFIG.autoSend && CONFIG.useSendPrefix) {
        sendToServer(text);
      }
    }).catch(err => {
      if (isSendConsole) console.error('❌ Ошибка копирования:', err);
    });

    return true;
  }

  // ========== ОТПРАВКА НА СЕРВЕР ЧЕРЕЗ POST ЗАПРОС ==========
  function sendToServer(text) {
    if (!text || text.trim() === '') return;

    // Формируем сообщение с именем источника для сервера
    // Используем формат [PARTIAL][Имя]текст для частичных сообщений
    // или [RECOGNIZED][Имя]текст для финальных
    const messageWithAuthor = `[RECOGNIZED][${CONFIG.authorName}]${text}`;
    
    // Кодируем текст для отправки
    const encodedText = encodeURIComponent(messageWithAuthor);
    const url = `http://${CONFIG.serverAddress}/?text=${encodedText}`;

    if (isSendConsole) console.log('📡 Отправка на сервер (GET):', url);
    if (isSendConsole) console.log(`📡 С именем источника: ${CONFIG.authorName}`);

    // GET запрос (для обратной совместимости)
    fetch(url, {
      method: 'GET',
      mode: 'no-cors',
      headers: { 'Accept': 'text/plain' },
      cache: 'no-cache'
    })
    .then(() => {
      if (isSendConsole) console.log('✅ GET запрос отправлен на сервер');
    })
    .catch(error => {
      if (isSendConsole) console.warn('⚠️ Ошибка отправки GET:', error.message);
    });

    // POST запрос с JSON форматом (основной способ)
    sendViaPost(text);
  }

  // ========== ОТПРАВКА ЧЕРЕЗ POST ЗАПРОС (ОСНОВНОЙ СПОСОБ) ==========
  function sendViaPost(text) {
    if (!text || text.trim() === '') return;

    const messageWithAuthor = `[RECOGNIZED][${CONFIG.authorName}]${text}`;
    
    // Формируем JSON для отправки (поддержка /api/recognize)
    const jsonData = {
      type: "final",
      id: generateId(),
      text: messageWithAuthor,
      timestamp: new Date().toLocaleTimeString()
    };

    const url = `http://${CONFIG.serverAddress}/api/recognize`;

    if (isSendConsole) console.log('📡 POST запрос на:', url);
    if (isSendConsole) console.log(`📡 С данными:`, jsonData);

    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      },
      body: JSON.stringify(jsonData),
      mode: 'cors'
    })
    .then(response => {
      if (isSendConsole) console.log('✅ POST запрос отправлен, статус:', response.status);
    })
    .catch(error => {
      if (isSendConsole) console.warn('⚠️ Ошибка POST отправки:', error.message);
      // Пробуем отправить через GET как fallback
      sendViaGetFallback(text);
    });
  }

  // ========== FALLBACK ОТПРАВКА ЧЕРЕЗ GET ==========
  function sendViaGetFallback(text) {
    const messageWithAuthor = `[RECOGNIZED][${CONFIG.authorName}]${text}`;
    const encodedText = encodeURIComponent(messageWithAuthor);
    const url = `http://${CONFIG.serverAddress}/?text=${encodedText}`;

    fetch(url, {
      method: 'GET',
      mode: 'no-cors',
      cache: 'no-cache'
    })
    .then(() => {
      if (isSendConsole) console.log('✅ GET fallback отправлен');
    })
    .catch(error => {
      if (isSendConsole) console.warn('⚠️ Ошибка GET fallback:', error.message);
    });
  }

  // ========== ГЕНЕРАТОР ID ДЛЯ СООБЩЕНИЙ ==========
  function generateId() {
    return 'msg_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
  }

  // ========== РЕЖИМ ВЫБОРА ЭЛЕМЕНТА (ТОЛЬКО КУРСОР) ==========
  function startElementSelection() {
    if (isElementSelectionMode) {
      stopElementSelection();
      return;
    }

    isElementSelectionMode = true;
    
    // Сохраняем и меняем курсор
    originalCursor = document.body.style.cursor;
    document.body.style.cursor = 'crosshair';
    
    // Добавляем обработчики
    document.addEventListener('mouseover', handleMouseOver);
    document.addEventListener('mouseout', handleMouseOut);
    document.addEventListener('click', handleElementClick, true);
    document.addEventListener('keydown', handleEscapeKey, true);
    
    if (isSendConsole) console.log('🎯 Режим выбора элемента АКТИВИРОВАН (курсор-прицел)');
  }

  function stopElementSelection() {
    if (!isElementSelectionMode) return;

    isElementSelectionMode = false;

    // Убираем подсветку
    if (highlightedElement) {
      highlightedElement.style.outline = '';
      highlightedElement = null;
    }

    // Восстанавливаем курсор
    document.body.style.cursor = originalCursor;

    // Удаляем обработчики
    document.removeEventListener('mouseover', handleMouseOver);
    document.removeEventListener('mouseout', handleMouseOut);
    document.removeEventListener('click', handleElementClick, true);
    document.removeEventListener('keydown', handleEscapeKey, true);

    if (isSendConsole) console.log('🎯 Режим выбора элемента ОТКЛЮЧЕН');
  }

  // Подсветка элемента при наведении
  function handleMouseOver(e) {
    if (!isElementSelectionMode) return;

    // ИГНОРИРУЕМ элементы самого расширения (если есть)
    if (e.target.id === 'multi-block-aim-overlay' || 
        e.target.id === 'multi-block-instruction' ||
        e.target.closest?.('#multi-block-aim-overlay')) {
      return;
    }

    // Убираем подсветку с предыдущего элемента
    if (highlightedElement && highlightedElement !== e.target) {
      highlightedElement.style.outline = '';
    }

    // Подсвечиваем текущий элемент
    highlightedElement = e.target;
    highlightedElement.style.outline = '2px solid #ff4444';
    highlightedElement.style.outlineOffset = '2px';
  }

  function handleMouseOut(e) {
    if (!isElementSelectionMode || !highlightedElement) return;

    if (highlightedElement === e.target) {
      highlightedElement.style.outline = '';
      highlightedElement = null;
    }
  }

  // Обработка ESC
  function handleEscapeKey(event) {
    if (event.key === 'Escape' && isElementSelectionMode) {
      event.preventDefault();
      event.stopPropagation();
      stopElementSelection();
    }
  }

  // Обработка клика - копируем outerHTML выбранного элемента
  function handleElementClick(e) {
    if (!isElementSelectionMode) return;

    // ИГНОРИРУЕМ клики по элементам расширения
    if (e.target.id === 'multi-block-aim-overlay' || 
        e.target.id === 'multi-block-instruction') {
      e.preventDefault();
      e.stopPropagation();
      return;
    }

    e.preventDefault();
    e.stopPropagation();

    const selectedElement = e.target;
    if (isSendConsole) console.log('🎯 Выбран элемент:', selectedElement.tagName);
    if (isSendConsole) console.log('🎯 outerHTML:', selectedElement.outerHTML.substring(0, 200) + '...');
    
    // Получаем outerHTML и преобразуем в текст
    const convertedText = convertElementToStructuredText(selectedElement);
    
    // Добавляем информацию об элементе
    const elementInfo = `// === ВЫБРАННЫЙ ЭЛЕМЕНТ: ${selectedElement.tagName} ===\n// === КЛАССЫ: ${selectedElement.className || 'нет'} ===\n// === ID: ${selectedElement.id || 'нет'} ===\n\n${convertedText}`;
    
    // Копируем и отправляем на сервер
    copyAndSendToServer(elementInfo, `элемент <${selectedElement.tagName}>`);
    
    // После клика ВЫКЛЮЧАЕМ режим выбора
    stopElementSelection();
  }

  // Преобразование элемента в структурированный текст
  function convertElementToStructuredText(element) {
    return convertToText(element);
  }

  // ========== ТРАНСФОРМАЦИЯ КОДА ==========
  function transformCode(inputText) {
    const lines = inputText.split('\n');
    let result = [];
    let inImportSection = false;
    let importLines = [];

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;

      if (line.startsWith('[DIV]')) {
        const restOfLine = line.substring(5).trim();
        if (restOfLine.startsWith('import')) {
          inImportSection = true;
          let importStatement = restOfLine;
          for (let j = i + 1; j < lines.length; j++) {
            const nextLine = lines[j].trim();
            if (nextLine && !nextLine.startsWith('[DIV]')) {
              importStatement += ' ' + nextLine;
              i = j;
            } else break;
          }
          importLines.push(importStatement);
        } else if (restOfLine) {
          if (inImportSection && importLines.length > 0) {
            result.push(...importLines.map(line => '        ' + line + ';'));
            result.push('');
            importLines = [];
            inImportSection = false;
          }
          let codeLine = restOfLine;
          for (let j = i + 1; j < lines.length; j++) {
            const nextLine = lines[j].trim();
            if (nextLine && !nextLine.startsWith('[DIV]')) {
              codeLine += ' ' + nextLine;
              i = j;
            } else break;
          }
          result.push('        ' + codeLine);
        }
      } else if (inImportSection && line.startsWith('import')) {
        let importStatement = line;
        for (let j = i + 1; j < lines.length; j++) {
          const nextLine = lines[j].trim();
          if (nextLine && !nextLine.startsWith('[DIV]')) {
            importStatement += ' ' + nextLine;
            i = j;
          } else break;
        }
        importLines.push(importStatement);
      } else if (line) {
        if (inImportSection && importLines.length > 0) {
          result.push(...importLines.map(line => '        ' + line + ';'));
          result.push('');
          importLines = [];
          inImportSection = false;
        }
        let codeLine = line;
        for (let j = i + 1; j < lines.length; j++) {
          const nextLine = lines[j].trim();
          if (nextLine && !nextLine.startsWith('[DIV]')) {
            codeLine += ' ' + nextLine;
            i = j;
          } else break;
        }
        result.push('        ' + codeLine);
      }
    }

    if (importLines.length > 0) {
      result.push(...importLines.map(line => '        ' + line + ';'));
    }

    return result.join('\n');
  }

  // ========== КОНВЕРТАЦИЯ HTML В ТЕКСТ ==========
  function convertToText(element) {
    const clone = element.cloneNode(true);
    
    // Удаляем скрытые элементы
    const hiddenElements = clone.querySelectorAll('[style*="display:none"], [style*="display: none"], [hidden]');
    hiddenElements.forEach(el => el.remove());

    function processElement(el, depth = 0) {
      let result = '';
      const indent = ' '.repeat(depth * 2);

      if (el.nodeType === Node.TEXT_NODE) {
        const text = el.textContent.trim();
        if (text) {
          if (element.classList && (element.classList.contains('view-lines') || element.matches?.('.view-lines *'))) {
            const words = text.split(/\s+/);
            let formattedText = '';
            for (let i = 0; i < words.length; i++) {
              const word = words[i];
              const prevWord = words[i - 1];
              if (i > 0) {
                const isSpecialChar = /^[.,;:{}()<>[\]+\-*/=]+$/.test(word);
                const prevIsSpecialChar = /^[.,;:{}()<>[\]+\-*/=]+$/.test(prevWord);
                if (!isSpecialChar && !prevIsSpecialChar) formattedText += ' ';
              }
              formattedText += word;
            }
            return indent + formattedText + '\n';
          } else {
            return indent + text + '\n';
          }
        }
        return result;
      }

      if (el.tagName === 'SCRIPT' || el.tagName === 'STYLE') return '';

      const tagName = el.tagName.toLowerCase();

      switch(tagName) {
        case 'div': case 'section': case 'article': case 'header': case 'footer': case 'nav': case 'aside': case 'main': case 'p':
          result += indent + '[' + tagName.toUpperCase() + ']\n';
          break;
        case 'h1': case 'h2': case 'h3': case 'h4': case 'h5': case 'h6':
          result += indent + '#'.repeat(parseInt(tagName.charAt(1))) + ' ';
          break;
        case 'table':
          result += indent + '[TABLE]\n';
          break;
        case 'tr':
          result += indent + '|';
          break;
        case 'br':
          result += '\n';
          break;
        case 'hr':
          result += indent + '---\n';
          break;
        case 'ul': case 'ol':
          result += indent + '[LIST]\n';
          break;
        case 'li':
          result += indent + '• ';
          break;
        case 'a':
          result += indent + '[LINK: ';
          break;
        default:
          if (!['span', 'strong', 'em', 'b', 'i', 'small', 'mark', 'del', 'ins', 'sub', 'sup'].includes(tagName)) {
            result += indent + '<' + tagName + '>\n';
          }
      }

      for (let child of el.childNodes) {
        result += processElement(child, depth + (tagName === 'td' || tagName === 'th' ? 0 : 1));
      }

      if (tagName === 'a' && el.href) {
        result = result.replace(/\[LINK: $/, `[LINK: ${el.textContent.trim()}](${el.getAttribute('href') || el.href})\n`);
      }
      if (tagName === 'tr') result += '|\n';
      if (tagName === 'td' || tagName === 'th') {
        const cellContent = el.textContent.trim();
        result = cellContent + ' | ';
      }

      if (tagName === 'table') {
        const rows = [];
        const tableRows = el.querySelectorAll('tr');
        tableRows.forEach(row => {
          const cells = Array.from(row.querySelectorAll('td, th')).map(cell =>
            cell.textContent.trim().replace(/\n/g, ' ')
          );
          if (cells.length > 0) rows.push('| ' + cells.join(' | ') + ' |');
        });
        if (rows.length > 0) {
          const separator = '| ' + rows[0].split('|').slice(1, -1).map(() => '---').join(' | ') + ' |';
          result = indent + '[TABLE]\n' + rows.join('\n' + indent) + '\n' + indent + separator + '\n';
        }
      }

      return result;
    }

    const text = processElement(clone);
    return text.split('\n')
      .map(line => line.trimEnd())
      .filter(line => line.length > 0 || line.includes('|'))
      .join('\n')
      .replace(/\n{3,}/g, '\n\n')
      .trim();
  }

  // ========== МЕНЕДЖЕР ГОРЯЧИХ КЛАВИШ ==========
  class HotkeyManager {
    constructor() {
      this.keySequence = [];
      this.maxSequenceTime = 1000;

      this.copyCombinations = new Set([
        'qw', 'qц', 'йw', 'йц', 'QW', 'QЦ', 'ЙW', 'ЙЦ', 'qW', 'qЦ', 'йW', 'йЦ', 'Qw', 'Qц', 'Йw', 'Йц'
      ]);

      // R / K - ВКЛЮЧАЮТ РЕЖИМ ВЫБОРА С КУРСОРОМ-ПРИЦЕЛОМ
      this.elementSelectionKeys = new Set(['r', 'R', 'к', 'К']);

      // T / Е - копирование блоков
      this.copyBlockKeys = new Set(['t', 'T', 'е', 'Е']);

      this.init();
    }

    init() {
      document.addEventListener('keydown', this.handleKeyDown.bind(this));
      if (isSendConsole) console.log('✅ Multi-Block Text Copier загружен');
      if (isSendConsole) console.log('📌 Горячие клавиши:');
      if (isSendConsole) console.log('  - Q+W / Й+Ц → копировать блоки');
      if (isSendConsole) console.log('  - T / Е → копировать блоки');
      if (isSendConsole) console.log('  - R / К → ВКЛЮЧИТЬ РЕЖИМ ВЫБОРА (курсор-прицел)');
      if (isSendConsole) console.log('  - ESC → выключить режим выбора');
      if (isSendConsole) console.log(`📡 Имя источника: ${CONFIG.authorName}`);
    }

    handleKeyDown(event) {
      if (this.isInputField(event.target)) return;

      const key = event.key;

      // R/K - режим выбора элемента
      if (this.elementSelectionKeys.has(key)) {
        event.preventDefault();
        event.stopPropagation();
        if (isSendConsole) console.log('🎯 Нажата клавиша выбора элемента:', key);
        startElementSelection();
        return;
      }

      // T/E - копирование блоков
      if (this.copyBlockKeys.has(key)) {
        event.preventDefault();
        event.stopPropagation();
        if (isSendConsole) console.log('📋 Нажата клавиша копирования блоков:', key);
        copyBlocks();
        return;
      }

      // Q+W комбинация
      this.keySequence.push({ key: key, time: Date.now() });
      this.cleanOldKeys();

      if (this.keySequence.length >= 2) {
        const lastTwo = this.keySequence.slice(-2);
        const combination = lastTwo.map(k => k.key).join('');

        if (this.copyCombinations.has(combination)) {
          event.preventDefault();
          event.stopPropagation();
          if (isSendConsole) console.log('📋 Обнаружена комбинация Q+W');
          copyBlocks();
          this.keySequence = [];
        }
      }
    }

    cleanOldKeys() {
      const now = Date.now();
      this.keySequence = this.keySequence.filter(k => now - k.time < this.maxSequenceTime);
    }

    isInputField(element) {
      return element.tagName === 'INPUT' ||
             element.tagName === 'TEXTAREA' ||
             element.tagName === 'SELECT' ||
             element.isContentEditable ||
             element.getAttribute('role') === 'textbox';
    }
  }

  // ========== Уведомление (заглушка, ничего не показывает) ==========
  function showNotification(message, isError = false) {
    // Полностью отключено - ничего не делаем
    return;
  }

  // ========== ИНИЦИАЛИЗАЦИЯ ==========
  function initializeExtension() {
    window.multiBlockHotkeyManager = new HotkeyManager();
    if (isSendConsole) console.log('🚀 Multi-Block Text Copier готов к работе');
    if (isSendConsole) console.log(`📡 SEND префикс: ${CONFIG.useSendPrefix ? 'ВКЛ' : 'ВЫКЛ'} | Сервер: ${CONFIG.serverAddress}`);
    if (isSendConsole) console.log(`📡 Имя источника: ${CONFIG.authorName}`);
  }

  // ========== ОБРАБОТЧИК СООБЩЕНИЙ ==========
  chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.action === "copyBlocks") {
      copyBlocks();
      sendResponse({ status: "copying" });
    }
    return true;
  });

  // ========== ЗАПУСК ==========
  initializeExtension();

  // Экспорт для отладки
  window.copyBlocks = copyBlocks;
  window.startElementSelection = startElementSelection;
  window.stopElementSelection = stopElementSelection;

})();