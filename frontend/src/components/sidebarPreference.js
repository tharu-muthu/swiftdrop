// Only a layout preference is persisted. Identity and tokens stay in memory.
const key = 'swiftdrop-sidebar';
function readPreference() {
  try { return window.localStorage.getItem(key) !== 'hidden'; }
  catch { return true; }
}
let visible = readPreference();
const listeners = new Set();
const publish = () => listeners.forEach(listener => listener());
function onStorage(event) {
  if (event.key === key || event.key === null) { visible = readPreference(); publish(); }
}
export function subscribeSidebar(listener) {
  if (!listeners.size) window.addEventListener('storage', onStorage);
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
    if (!listeners.size) window.removeEventListener('storage', onStorage);
  };
}
export const sidebarSnapshot = () => visible;
export function setSidebarVisible(next) {
  visible = next;
  try { window.localStorage.setItem(key, next ? 'visible' : 'hidden'); }
  catch { /* Keep the preference in memory when storage is unavailable. */ }
  publish();
}
