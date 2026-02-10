// Создаем контекстное меню при установке плагина
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: "copy-with-sound",
    title: "Govorilka_cp",
    contexts: ["selection"]
  });
});

// Обработчик клика по контекстному меню
chrome.contextMenus.onClicked.addListener((info) => {
  if (info.menuItemId === "copy-with-sound") {
    // Форматируем текст согласно требованиям
    const formattedText = `SOUND(192.168.15.3:8080): ${info.selectionText}`;
    
    // Получаем активную вкладку
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      if (tabs[0]?.id) {
        // Исполняем скрипт на активной вкладке для доступа к clipboard
        chrome.scripting.executeScript({
          target: { tabId: tabs[0].id },
          func: copyToClipboard,
          args: [formattedText]
        }).catch(err => {
          console.error('Ошибка выполнения скрипта:', err);
        });
      }
    });
  }
});

// Функция, которая будет выполнена на странице
function copyToClipboard(text) {
  navigator.clipboard.writeText(text).then(() => {
    console.log('Текст скопирован:', text);
  }).catch(err => {
    console.error('Ошибка копирования:', err);
  });
}