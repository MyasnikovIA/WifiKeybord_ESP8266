// background.js
chrome.action.onClicked.addListener((tab) => {
  executeCopyScript(tab);
});

// Обработчик команды из manifest.json
chrome.commands.onCommand.addListener((command) => {
  if (command === "copy_blocks_hotkey") {
    chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
      if (tabs[0]) {
        executeCopyScript(tabs[0]);
      }
    });
  }
});

// Обработчик сообщений от content script
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === "copyBlocksHotkey" && sender.tab) {
    executeCopyScript(sender.tab);
  }
});

// Общая функция для выполнения скрипта
function executeCopyScript(tab) {
  chrome.scripting.executeScript({
    target: { tabId: tab.id },
    files: ['content.js']
  }).then(() => {
    chrome.tabs.sendMessage(tab.id, { action: "copyBlocks" });
  }).catch(err => {
    console.error('Ошибка загрузки скрипта:', err);
    // Пытаемся инжектить hotkey.js как fallback
    chrome.scripting.executeScript({
      target: { tabId: tab.id },
      files: ['hotkey.js']
    });
  });
}

// Инжектим hotkey.js при загрузке каждой страницы
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.status === 'complete' && tab.url && tab.url.startsWith('http')) {
    chrome.scripting.executeScript({
      target: { tabId: tabId },
      files: ['hotkey.js']
    }).catch(err => console.log('Hotkey injection skipped:', err));
  }
});