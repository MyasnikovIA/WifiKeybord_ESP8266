chrome.action.onClicked.addListener((tab) => {
  chrome.scripting.executeScript({
    target: { tabId: tab.id },
    files: ['content.js']
  }).then(() => {
    chrome.tabs.sendMessage(tab.id, { action: "copyBlocks" });
  }).catch(err => {
    console.error('Ошибка загрузки скрипта:', err);
  });
});