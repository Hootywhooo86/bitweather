// Bitweather core — shared by the page (app.js) and the service worker (sw.js):
// formatting, weather codes, API calls, alert logic and the notification config store.
(function (root) {
  const WMO = {
    0: ['Clear', 'clear'], 1: ['Mainly clear', 'mostly'], 2: ['Partly cloudy', 'partly'], 3: ['Overcast', 'cloudy'],
    45: ['Fog', 'fog'], 48: ['Freezing fog', 'fog'],
    51: ['Light drizzle', 'drizzle'], 53: ['Drizzle', 'drizzle'], 55: ['Heavy drizzle', 'drizzle'],
    56: ['Freezing drizzle', 'freezing'], 57: ['Freezing drizzle', 'freezing'],
    61: ['Light rain', 'rain'], 63: ['Rain', 'rain'], 65: ['Heavy rain', 'rain'],
    66: ['Freezing rain', 'freezing'], 67: ['Freezing rain', 'freezing'],
    71: ['Light snow', 'snow'], 73: ['Snow', 'snow'], 75: ['Heavy snow', 'snow'], 77: ['Snow grains', 'snow'],
    80: ['Light showers', 'showers'], 81: ['Showers', 'showers'], 82: ['Heavy showers', 'rain'],
    85: ['Snow showers', 'snow'], 86: ['Heavy snow showers', 'snow'],
    95: ['Thunderstorm', 'thunder'], 96: ['Thunderstorm, hail', 'thunder'], 99: ['Thunderstorm, hail', 'thunder'],
  };
  const condName = c => (WMO[c] || ['Unknown'])[0];
  const naive = s => Date.parse(s.length === 10 ? s + 'T00:00Z' : s + 'Z');
  const DOW = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const dowOf = d => DOW[new Date(d + 'T12:00Z').getUTCDay()];
  const WIND = { kmh: [1, 'km/h'], mph: [0.621371, 'mph'], ms: [1 / 3.6, 'm/s'], kn: [0.539957, 'kn'] };

  // Formatters read the units object live, so changing a unit takes effect immediately.
  function makeFmt(U) {
    const fT = c => (c == null ? '--' : Math.round(U.temp === 'F' ? c * 9 / 5 + 32 : c)) + '°';
    const fWnum = k => Math.round(k * WIND[U.wind][0]);
    const fW = k => fWnum(k) + ' ' + WIND[U.wind][1];
    const fP = mm => (U.precip === 'in' ? (mm / 25.4).toFixed(2) : (mm < 10 ? mm.toFixed(1) : Math.round(mm)));
    const pU = () => (U.precip === 'in' ? 'in' : 'mm');
    const fPr = h => (U.pressure === 'inHg' ? (h * 0.02953).toFixed(2) : U.pressure === 'kPa' ? (h / 10).toFixed(1) : Math.round(h).toLocaleString());
    const fD = m => (U.dist === 'mi' ? [+(m / 1609.34).toFixed(m < 16093 ? 1 : 0), 'mi'] : [+(m / 1000).toFixed(m < 10000 ? 1 : 0), 'km']);
    const fClock = s => {
      const hh = +s.slice(11, 13), mm = s.slice(14, 16);
      if (U.clock === '24') return s.slice(11, 16);
      return ((hh % 12) || 12) + (mm === '00' ? '' : ':' + mm) + (hh < 12 ? 'a' : 'p');
    };
    const fHour = s => (U.clock === '24' ? s.slice(11, 16) : fClock(s));
    return { fT, fWnum, fW, fP, pU, fPr, fD, fClock, fHour, windUnit: () => WIND[U.wind][1] };
  }

  async function getJSON(url, ms = 15000) {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), ms);
    try {
      const r = await fetch(url, { signal: ctl.signal });
      if (!r.ok) throw new Error(r.status + ' ' + url);
      return await r.json();
    } finally { clearTimeout(t); }
  }

  function forecastURL(l) {
    const p = new URLSearchParams({
      latitude: l.lat, longitude: l.lon, timezone: 'auto', forecast_days: 10, past_days: 1,
      current: 'temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,rain,showers,snowfall,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility,dew_point_2m,uv_index',
      hourly: 'temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,precipitation_probability,precipitation,snowfall,weather_code,pressure_msl,visibility,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,is_day',
      daily: 'weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,sunrise,sunset,daylight_duration,uv_index_max,precipitation_sum,rain_sum,snowfall_sum,precipitation_hours,precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant',
    });
    return 'https://api.open-meteo.com/v1/forecast?' + p;
  }
  function airURL(l) {
    const p = new URLSearchParams({
      latitude: l.lat, longitude: l.lon, timezone: 'auto', past_days: 1, forecast_days: 2,
      current: 'us_aqi,european_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide',
      hourly: 'pm2_5,ozone,nitrogen_dioxide,us_aqi',
    });
    return 'https://air-quality-api.open-meteo.com/v1/air-quality?' + p;
  }
  async function officialAlerts(l) {
    const now = Date.now();
    if (l.cc === 'CA') {
      const d = 0.02;
      const j = await getJSON(`https://api.weather.gc.ca/collections/weather-alerts/items?f=json&lang=en&limit=50&bbox=${[l.lon - d, l.lat - d, l.lon + d, l.lat + d].map(v => v.toFixed(4)).join(',')}`, 20000);
      const seen = new Set();
      return (j.features || []).map(f => f.properties).filter(p => {
        const exp = Date.parse(p.expiration_datetime || p.event_end_datetime || 0);
        const k = p.alert_name_en + '|' + (p.alert_text_en || '').slice(0, 80);
        if ((exp && exp < now) || seen.has(k) || /cancel/i.test(p.status_en || '')) return false;
        seen.add(k); return true;
      }).map(p => ({
        type: p.alert_type || 'statement',
        title: (p.alert_short_name_en || p.alert_name_en || 'Weather alert').replace(/^\w/, c => c.toUpperCase()),
        name: p.alert_name_en, text: p.alert_text_en || '',
        until: p.event_end_datetime || p.expiration_datetime, src: 'Environment and Climate Change Canada',
      }));
    }
    if (l.cc === 'US') {
      const j = await getJSON(`https://api.weather.gov/alerts/active?point=${l.lat.toFixed(4)},${l.lon.toFixed(4)}`);
      return (j.features || []).map(f => f.properties).map(p => ({
        type: /warning/i.test(p.event) ? 'warning' : /watch/i.test(p.event) ? 'watch' : 'advisory',
        title: p.event, name: p.headline, text: [p.description, p.instruction].filter(Boolean).join('\n\n'),
        until: p.ends || p.expires, src: 'US National Weather Service',
      }));
    }
    return [];
  }

  // index of the hourly slot containing "now" at the forecast's location
  function currentHour(w) {
    const nowN = Date.now() + w.utc_offset_seconds * 1000;
    const i = w.hourly.time.findIndex(t => naive(t) > nowN) - 1;
    return i < 0 ? 0 : i;
  }
  function todayIndex(w) {
    const d = w.daily.time.indexOf(new Date(Date.now() + w.utc_offset_seconds * 1000).toISOString().slice(0, 10));
    return d < 0 ? 0 : d;
  }

  // Threshold alerts over the next 48 h. T = thresholds in °C / km/h / cm.
  function customAlerts(w, T, f) {
    const H = w.hourly, i0 = currentHour(w), out = [];
    const hrs = [];
    for (let i = i0; i < Math.min(H.time.length, i0 + 48); i++) hrs.push(i);
    if (!hrs.length) return out;
    const pick = (arr, better) => hrs.reduce((a, i) => (better(arr[i], arr[a]) ? i : a), hrs[0]);
    const minI = pick(H.temperature_2m, (x, y) => x < y);
    const maxI = pick(H.temperature_2m, (x, y) => x > y);
    const gI = pick(H.wind_gusts_10m, (x, y) => x > y);
    const when = i => dowOf(H.time[i].slice(0, 10)) + ' ' + f.fHour(H.time[i]);
    const day = i => H.time[i].slice(0, 10);
    if (T.cold != null && H.temperature_2m[minI] <= T.cold) out.push({ type: 'custom', key: 'cold' + T.cold + day(minI), title: `Below ${f.fT(T.cold)}`, name: `Drops to ${f.fT(H.temperature_2m[minI])} ${when(minI)}`, text: 'Your cold threshold alert.' });
    if (T.hot != null && H.temperature_2m[maxI] >= T.hot) out.push({ type: 'custom', key: 'hot' + T.hot + day(maxI), title: `Above ${f.fT(T.hot)}`, name: `Reaches ${f.fT(H.temperature_2m[maxI])} ${when(maxI)}`, text: 'Your heat threshold alert.' });
    if (T.gust != null && H.wind_gusts_10m[gI] >= T.gust) out.push({ type: 'custom', key: 'gust' + T.gust + day(gI), title: `Gusts over ${f.fW(T.gust)}`, name: `Gusts to ${f.fW(H.wind_gusts_10m[gI])} ${when(gI)}`, text: 'Your wind threshold alert.' });
    const snow = hrs.reduce((s, i) => s + (H.snowfall[i] || 0), 0);
    if (T.snow != null && snow >= T.snow) out.push({ type: 'custom', key: 'snow' + T.snow + day(i0), title: `Snow ${Math.round(snow)} cm`, name: `About ${Math.round(snow)} cm of snow in the next 48 h`, text: 'Your snowfall threshold alert.' });
    return out;
  }
  const alertKey = a => a.key || [a.title, a.name, a.until].join('|');

  function nowSummary(w, f) {
    const c = w.current, D = w.daily, d = todayIndex(w);
    return `${f.fT(c.temperature_2m)} ${condName(c.weather_code)} · Feels ${f.fT(c.apparent_temperature)} · H ${f.fT(D.temperature_2m_max[d])} L ${f.fT(D.temperature_2m_min[d])}`;
  }

  // Notification config lives in Cache Storage so the service worker can read it in the background.
  const CFG_CACHE = 'bw-data', CFG_URL = './__bw/config.json';
  async function readConfig() {
    try {
      const r = await (await caches.open(CFG_CACHE)).match(CFG_URL);
      return r ? await r.json() : null;
    } catch (e) { return null; }
  }
  async function writeConfig(cfg) {
    try {
      await (await caches.open(CFG_CACHE)).put(CFG_URL, new Response(JSON.stringify(cfg), { headers: { 'Content-Type': 'application/json' } }));
    } catch (e) { /* storage unavailable */ }
  }

  // Shows notifications for alerts not seen before and (optionally) the current-conditions update.
  // Returns the updated seen list. reg = ServiceWorkerRegistration.
  async function notifyFrom(reg, cfg, loc, w, official) {
    const show = (t, o) => reg.showNotification(t, o).catch(() => {});
    const f = makeFmt(cfg.units || {});
    const alerts = [...(official || []), ...customAlerts(w, cfg.thresholds || {}, f)];
    const seen = new Set(cfg.seen || []);
    const n = cfg.notify || {};
    const where = loc.name || 'Your location';
    if (n.alerts) {
      for (const a of alerts) {
        const k = alertKey(a);
        if (seen.has(k)) continue;
        seen.add(k);
        await show(`${a.type === 'warning' ? '⚠ ' : ''}${a.title} — ${where}`, {
          body: a.name || a.text.slice(0, 140), tag: 'bw-alert-' + k.slice(0, 60), icon: 'icon-192.png', badge: 'badge-96.png',
          requireInteraction: a.type === 'warning', data: { url: './' },
        });
      }
    } else {
      alerts.forEach(a => seen.add(alertKey(a)));
    }
    if (n.updates) {
      await show(where, {
        body: nowSummary(w, f), tag: 'bw-now', silent: true, renotify: false, icon: 'icon-192.png', badge: 'badge-96.png', data: { url: './' },
      });
    }
    return [...seen].slice(-200);
  }

  root.BWCore = {
    WMO, condName, naive, DOW, dowOf, WIND, makeFmt, getJSON, forecastURL, airURL, officialAlerts,
    currentHour, todayIndex, customAlerts, alertKey, nowSummary, readConfig, writeConfig, notifyFrom,
  };
})(self);
