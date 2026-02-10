document.getElementById('toggleBlocks').addEventListener('click', () => {
    chrome.runtime.sendMessage({action: "executeContentScript", mode: "html"});
});

document.getElementById('toggleTextBlocks').addEventListener('click', () => {
    chrome.runtime.sendMessage({action: "executeContentScript", mode: "text"});
});