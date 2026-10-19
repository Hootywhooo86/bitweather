// Bitweather service worker: offline app shell, background weather checks and notifications.
importScripts('shared.js');
const VERSION = '2';
const CHANGES = 'Follows your current location, refreshes every 30 min, and sends alert notifications.';
const SHELL = 'bw-shell-v' + VERSION;
const FILES = ['./', 'index.html', 'style.css', 'shared.js', 'icons.js', 'app.js', 'icon.svg', 'icon-192.png', 'icon-512.png', 'badge-96.png', 'manifest.webmanifest'];

self.addEventListener('install', e => { e.waitUntil(caches.open(SHELL).then(c => c.addAll(FILES))); self.skipWaiting(); });
self.addEventListener('activate', e => {
  e.waitUntil((async () => {
    const keys = await caches.keys();
    const old = keys.filter(k => k.startsWith('bw-shell-') && k !== SHELL);
    await Promise.all(old.map(k => caches.delete(k)));
    await self.clients.claim();
    if (old.length) {
      const cfg = await BWCore.readConfig();
      if (cfg && cfg.notify && cfg.notify.app && self.Notification && Notification.permission === 'granted') {
        await self.registration.showNotification(`Bitweather updated (v${VERSION})`, { body: CHANGES, tag: 'bw-app-update', icon: 'icon-192.png', badge: 'badge-96.png', data: { url: './' } });
      }
    }
  })());
});

self.addEventListener('fetch', e => {
  const u = new URL(e.request.url);
  if (e.request.method !== 'GET' || u.origin !== location.origin) return;
  // network first so updates land, cache as fallback for offline
  e.respondWith(fetch(e.request).then(r => {
    const copy = r.clone();
    if (r.ok) caches.open(SHELL).then(c => c.put(e.request, copy));
    return r;
  }).catch(() => caches.match(e.request)));
});

// Background check (installed app on Android/Chrome): fetch weather for the current location and notify.
async function backgroundCheck() {
  const cfg = await BWCore.readConfig();
  if (!cfg || !cfg.loc || !(self.Notification && Notification.permission === 'granted')) return;
  const [w, al] = await Promise.allSettled([BWCore.getJSON(BWCore.forecastURL(cfg.loc)), BWCore.officialAlerts(cfg.loc)]);
  if (w.status !== 'fulfilled') return;
  cfg.seen = await BWCore.notifyFrom(self.registration, cfg, cfg.loc, w.value, al.status === 'fulfilled' ? al.value : []);
  cfg.lastBackground = Date.now();
  await BWCore.writeConfig(cfg);
}
self.addEventListener('periodicsync', e => { if (e.tag === 'bw-refresh') e.waitUntil(backgroundCheck()); });

self.addEventListener('notificationclick', e => {
  e.notification.close();
  const url = new URL((e.notification.data && e.notification.data.url) || './', self.registration.scope).href;
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(cs => {
    const c = cs.find(x => x.url.startsWith(self.registration.scope));
    return c ? c.focus() : self.clients.openWindow(url);
  }));
});
