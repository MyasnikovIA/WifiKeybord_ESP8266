chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.action === "executeContentScript") {
        chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
            if (tabs[0]) {
                chrome.scripting.executeScript({
                    target: {tabId: tabs[0].id},
                    files: ['content.js']
                });
                
                // Отправляем сообщение после загрузки скрипта
                setTimeout(() => {
                    const message = request.mode === "text" 
                        ? {action: "toggleTextBlocks"} 
                        : {action: "toggleBlocks"};
                    chrome.tabs.sendMessage(tabs[0].id, message);
                }, 100);
            }
        });
    }
});