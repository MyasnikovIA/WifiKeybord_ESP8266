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
      showNotification('X');
    }).catch(err => {
      console.error('Ошибка копирования:', err);
      showNotification('Ошибка копирования!', true);
    });
  } else {
    showNotification('Блоки не найдены!', true);
  }
}
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

function showNotification(message, isError = false) {
  const notification = document.createElement('div');
  notification.textContent = message;
  notification.style.cssText = `
	  position: fixed;
	  top: 2px;
	  right: 2px;
	  background: ${isError ? '#f44336' : '#4CAF50'};
	  color: white;
	  padding: 10px 20px;
	  border-radius: 5px;
	  z-index: 999999;
	  font-family: Arial, sans-serif;
	  transform: scale(0.1);
	`;
  document.body.appendChild(notification);
  
  setTimeout(() => {
    if (document.body.contains(notification)) {
      document.body.removeChild(notification);
    }
  }, 2000);
}

// Слушатель сообщений от background скрипта
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === "copyBlocks") {
    copyBlocks();
  }
});