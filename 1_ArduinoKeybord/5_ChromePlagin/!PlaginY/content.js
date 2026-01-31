// content.js - Multi-Block Text Copier
(function() {
  'use strict';

  // ========== Основная функция копирования ==========
  function copyBlocks() {
    const blocks = [
      { selector: '[aria-labelledby="tab-description"]', name: 'ОПИСАНИЕ' },
      { selector: '.view-lines', name: 'КОД' }
    ];

    let allText = '';
    
    blocks.forEach((block, index) => {
      const element = document.querySelector(block.selector);
      if (element) {
        const blockText = convertToText(element);
        if (blockText.trim()) {
          if (allText) {
            allText += '\n\n'; // Разделитель между блоками
          }
          if (block.name === 'КОД') {
            allText += `// === ${block.name.toUpperCase()} ===\n${transformCode(blockText)}`;
          } else {
            allText += `// === ${block.name.toUpperCase()} ===\n${blockText}`;
          }
        }
      }
    });

    if (allText) {
      navigator.clipboard.writeText(allText).then(() => {
        showNotification('');
      }).catch(err => {
        console.error('Ошибка копирования:', err);
        showNotification('err!', true);
      });
    } else {
      showNotification('Блоки не найдены!', true);
    }
  }

  // ========== Трансформация кода ==========
  function transformCode(inputText) {
    // Разделяем текст на строки
    const lines = inputText.split('\n');
    let result = [];
    let inImportSection = false;
    let importLines = [];
    
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      
      // Пропускаем пустые строки
      if (!line) continue;
      
      // Если строка начинается с [DIV]
      if (line.startsWith('[DIV]')) {
        // Получаем оставшуюся часть строки после [DIV]
        const restOfLine = line.substring(5).trim();
        
        // Если после [DIV] есть текст импорта
        if (restOfLine.startsWith('import')) {
          inImportSection = true;
          // Объединяем разбитые import строки
          let importStatement = restOfLine;
          // Собираем многострочные импорты
          for (let j = i + 1; j < lines.length; j++) {
            const nextLine = lines[j].trim();
            if (nextLine && !nextLine.startsWith('[DIV]')) {
              importStatement += ' ' + nextLine;
              i = j; // Пропускаем обработанную строку
            } else {
              break;
            }
          }
          importLines.push(importStatement);
        } 
        // Если после [DIV] есть другой текст (не import)
        else if (restOfLine) {
          // Если мы были в секции импортов, добавляем их
          if (inImportSection && importLines.length > 0) {
            result.push(...importLines.map(line => '        ' + line + ';'));
            result.push(''); // Пустая строка после импортов
            importLines = [];
            inImportSection = false;
          }
          
          // Обрабатываем другие конструкции
          let codeLine = restOfLine;
          // Собираем разбитые строки кода
          for (let j = i + 1; j < lines.length; j++) {
            const nextLine = lines[j].trim();
            if (nextLine && !nextLine.startsWith('[DIV]')) {
              codeLine += ' ' + nextLine;
              i = j;
            } else {
              break;
            }
          }
          result.push('        ' + codeLine);
        }
      } 
      // Если строка не содержит [DIV] но является продолжением import
      else if (inImportSection && line.startsWith('import')) {
        let importStatement = line;
        // Собираем многострочные импорты
        for (let j = i + 1; j < lines.length; j++) {
          const nextLine = lines[j].trim();
          if (nextLine && !nextLine.startsWith('[DIV]')) {
            importStatement += ' ' + nextLine;
            i = j;
          } else {
            break;
          }
        }
        importLines.push(importStatement);
      }
      // Если строка не содержит [DIV] и не import
      else if (line) {
        // Если мы были в секции импортов, добавляем их
        if (inImportSection && importLines.length > 0) {
          result.push(...importLines.map(line => '        ' + line + ';'));
          result.push(''); // Пустая строка после импортов
          importLines = [];
          inImportSection = false;
        }
        
        let codeLine = line;
        // Собираем разбитые строки кода
        for (let j = i + 1; j < lines.length; j++) {
          const nextLine = lines[j].trim();
          if (nextLine && !nextLine.startsWith('[DIV]')) {
            codeLine += ' ' + nextLine;
            i = j;
          } else {
            break;
          }
        }
        result.push('        ' + codeLine);
      }
    }
    
    // Добавляем оставшиеся импорты в конце
    if (importLines.length > 0) {
      result.push(...importLines.map(line => '        ' + line + ';'));
    }
    
    return result.join('\n');
  }

  // ========== Конвертация HTML в текст ==========
  function convertToText(element) {
    const clone = element.cloneNode(true);
    
    // Удаляем все скрытые элементы
    const hiddenElements = clone.querySelectorAll('[style*="display:none"], [style*="display: none"], [hidden]');
    hiddenElements.forEach(el => el.remove());
    
    // Рекурсивная функция для обработки элементов с сохранением структуры
    function processElement(el, depth = 0) {
      let result = '';
      const indent = ' '.repeat(depth * 2);
      
      // Обработка текстовых узлов
      if (el.nodeType === Node.TEXT_NODE) {
        const text = el.textContent.trim();
        if (text) {
          // Для блоков кода обрабатываем специальным образом
          if (element.classList.contains('view-lines') || element.matches('.view-lines *')) {
            // Разделяем слова, но сохраняем правильные пробелы
            const words = text.split(/\s+/);
            let formattedText = '';
            
            for (let i = 0; i < words.length; i++) {
              const word = words[i];
              const prevWord = words[i - 1];
              
              if (i > 0) {
                // Определяем, нужно ли добавлять пробел
                const isSpecialChar = /^[.,;:{}()<>[\]+\-*/=]+$/.test(word);
                const prevIsSpecialChar = /^[.,;:{}()<>[\]+\-*/=]+$/.test(prevWord);
                
                // Добавляем пробел только если оба слова не являются специальными символами
                if (!isSpecialChar && !prevIsSpecialChar) {
                  formattedText += ' ';
                }
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
      
      // Пропускаем скрипты и стили
      if (el.tagName === 'SCRIPT' || el.tagName === 'STYLE') {
        return '';
      }
      
      const tagName = el.tagName.toLowerCase();
      
      // Особые обработчики для разных типов элементов
      switch(tagName) {
        case 'div':
        case 'section':
        case 'article':
        case 'header':
        case 'footer':
        case 'nav':
        case 'aside':
        case 'main':
        case 'p':
          result += indent + '[' + tagName.toUpperCase() + ']\n';
          break;
          
        case 'h1':
        case 'h2':
        case 'h3':
        case 'h4':
        case 'h5':
        case 'h6':
          result += indent + '#'.repeat(parseInt(tagName.charAt(1))) + ' ';
          break;
          
        case 'table':
          result += indent + '[TABLE]\n';
          break;
          
        case 'tr':
          result += indent + '|';
          break;
          
        case 'td':
        case 'th':
          // Обработка ячеек таблицы
          break;
          
        case 'br':
          result += '\n';
          break;
          
        case 'hr':
          result += indent + '---\n';
          break;
          
        case 'ul':
        case 'ol':
          result += indent + '[LIST]\n';
          break;
          
        case 'li':
          result += indent + '• ';
          break;
          
        case 'a':
          result += indent + '[LINK: ';
          break;
          
        case 'code':
        case 'pre':
          // Для кода не добавляем метки, просто содержимое
          break;
          
        default:
          // Для остальных элементов просто добавляем тег
          if (!['span', 'strong', 'em', 'b', 'i', 'small', 'mark', 'del', 'ins', 'sub', 'sup'].includes(tagName)) {
            result += indent + '<' + tagName + '>\n';
          }
      }
      
      // Обрабатываем дочерние элементы
      for (let child of el.childNodes) {
        result += processElement(child, depth + (tagName === 'td' || tagName === 'th' ? 0 : 1));
      }
      
      // Закрывающие теги для некоторых элементов
      switch(tagName) {
        case 'a':
          if (el.href) {
            const hrefText = el.textContent.trim();
            const href = el.getAttribute('href') || el.href;
            result = result.replace(/\[LINK: $/, `[LINK: ${hrefText}](${href})\n`);
          }
          break;
          
        case 'tr':
          result += '|\n';
          break;
          
        case 'td':
        case 'th':
          // Для ячеек таблицы добавляем разделитель
          const cellContent = el.textContent.trim();
          result = cellContent + ' | ';
          break;
      }
      
      // Особый случай для таблиц - собираем строки
      if (tagName === 'table') {
        const rows = [];
        const tableRows = el.querySelectorAll('tr');
        
        tableRows.forEach(row => {
          const cells = Array.from(row.querySelectorAll('td, th')).map(cell => 
            cell.textContent.trim().replace(/\n/g, ' ')
          );
          if (cells.length > 0) {
            rows.push('| ' + cells.join(' | ') + ' |');
          }
        });
        
        if (rows.length > 0) {
          const separator = '| ' + rows[0].split('|').slice(1, -1).map(() => '---').join(' | ') + ' |';
          result = indent + '[TABLE]\n' + rows.join('\n' + indent) + '\n' + indent + separator + '\n';
        }
      }
      
      return result;
    }
    
    // Обрабатываем элемент
    const text = processElement(clone);
    
    // Очищаем лишние пробелы и пустые строки
    return text.split('\n')
      .map(line => line.trimEnd())
      .filter(line => line.length > 0 || line.includes('|')) // Сохраняем строки таблиц даже если они пустые
      .join('\n')
      .replace(/\n{3,}/g, '\n\n') // Заменяем множественные переносы на двойные
      .trim();
  }

  // ========== Уведомление ==========
  function showNotification(message, isError = false) {
    // Удаляем старое уведомление, если есть
    const oldNotification = document.querySelector('.multi-block-notification');
    if (oldNotification) {
      oldNotification.remove();
    }
    
    const notification = document.createElement('div');
    notification.textContent = message;
    //notification.className = 'multi-block-notification';
    notification.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      background: ${isError ? '#f44336' : '#4CAF50'};
      color: white;
      z-index: 999999;
      animation: slideIn 0.3s ease-out;
	  transform: scale(0.1);
    `;
    
    // Добавляем стили для анимации
    const style = document.createElement('style');
    style.textContent = `
      @keyframes slideIn {
        from {
          transform: translateX(100%);
          opacity: 0;
        }
        to {
          transform: translateX(0);
          opacity: 1;
        }
      }
    `;
    document.head.appendChild(style);
    
    document.body.appendChild(notification);
    
    setTimeout(() => {
      if (document.body.contains(notification)) {
        notification.style.animation = 'slideIn 0.3s ease-out reverse';
        setTimeout(() => {
          if (document.body.contains(notification)) {
            document.body.removeChild(notification);
          }
        }, 300);
      }
    }, 2000);
  }

  // ========== Обработчик горячих клавиш ==========
  class HotkeyManager {
    constructor() {
      this.keySequence = [];
      this.maxSequenceTime = 1000; // 1 секунда для комбинации
      this.combinations = new Set([
        'qw', 'qц', 'йw', 'йц',     // нижний регистр
        'QW', 'QЦ', 'ЙW', 'ЙЦ',     // верхний регистр
        'qW', 'qЦ', 'йW', 'йЦ',     // смешанный регистр
        'Qw', 'Qц', 'Йw', 'Йц'      // смешанный регистр
      ]);
      
      this.init();
    }
    
    init() {
      document.addEventListener('keydown', this.handleKeyDown.bind(this));
      // console.log('Multi-Block Text Copier: Hotkey manager initialized');
    }
    
    handleKeyDown(event) {
      // Игнорируем клавиши в полях ввода
      if (this.isInputField(event.target)) {
        return;
      }
      
      const key = event.key;
      this.keySequence.push({
        key: key,
        time: Date.now(),
        target: event.target
      });
      
      // Очищаем старые нажатия
      this.cleanOldKeys();
      
      // Проверяем комбинации из последних 2 нажатий
      if (this.keySequence.length >= 2) {
        const lastTwo = this.keySequence.slice(-2);
        const combination = lastTwo.map(k => k.key).join('');
        
        if (this.combinations.has(combination)) {
          // Комбинация найдена!
          event.preventDefault();
          event.stopPropagation();
          
          // Показываем короткое уведомление о распознавании комбинации
          showNotification('.', false);
          
          // Запускаем копирование с небольшой задержкой
          setTimeout(() => {
            copyBlocks();
          }, 100);
          
          // Очищаем последовательность
          this.keySequence = [];
        }
      }
    }
    
    cleanOldKeys() {
      const now = Date.now();
      this.keySequence = this.keySequence.filter(k => 
        now - k.time < this.maxSequenceTime
      );
    }
    
    isInputField(element) {
      return element.tagName === 'INPUT' || 
             element.tagName === 'TEXTAREA' || 
             element.tagName === 'SELECT' ||
             element.isContentEditable ||
             element.getAttribute('role') === 'textbox';
    }
  }

  // ========== Инициализация ==========
  function initializeExtension() {
    // Инициализируем менеджер горячих клавиш
    window.multiBlockHotkeyManager = new HotkeyManager();
    
    // Добавляем стиль для уведомлений
    const style = document.createElement('style');
    style.id = 'multi-block-styles';
    style.textContent = `
      .multi-block-hotkey-hint {
        position: fixed;
        bottom: 20px;
        right: 20px;
        background: rgba(76, 175, 80, 0.9);
        color: white;
        padding: 8px 12px;
        border-radius: 4px;
        font-size: 12px;
        z-index: 999998;
        animation: fadeInOut 3s ease-in-out;
        scale(0.1);
      }
      
      @keyframes fadeInOut {
        0%, 100% { opacity: 0; }
        10%, 90% { opacity: 1; }
      }
    `;
    document.head.appendChild(style);
    console.log('Multi-Block Text Copier initialized successfully');
  }

  // ========== Обработчик сообщений ==========
  chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.action === "copyBlocks") {
      copyBlocks();
      sendResponse({ status: "copying" });
    }
    return true;
  });

  // ========== НЕМЕДЛЕННАЯ ИНИЦИАЛИЗАЦИЯ ==========
  // Как в проекте 2: скрипт выполняется сразу при загрузке
  console.log('Multi-Block Text Copier loading (q+w)');
  initializeExtension();

  // Экспортируем функции для глобального доступа
  window.copyBlocks = copyBlocks;
  window.convertToText = convertToText;
  window.transformCode = transformCode;
  window.showNotification = showNotification;

})();