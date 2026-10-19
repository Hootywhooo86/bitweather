// Bitweather app — widget-first pixel weather.
// Data: Open-Meteo (forecast + air quality, no key), Photon/Open-Meteo geocoding,
// Environment Canada + US NWS official alerts, RainViewer radar.
(function () {
  const { icon, moonPhaseIcon } = window.PixelIcons;
  const $ = (s, el = document) => el.querySelector(s);
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  // ---------------------------------------------------------------- state
  const STORE = 'bw.v1';
  const SIZE_NAMES = { T: 'THIN', S: 'SMALL', W: 'WIDE', L: 'LARGE' };
  const WIDGETS = {
    alerts:     { name: 'Alerts',          glyph: '!', sizes: ['T', 'W', 'S'] },
    brief:      { name: 'Weather brief',   glyph: '&#9636;', sizes: ['W', 'T', 'L'] },
    insight:    { name: 'Weather insight', glyph: '&#8599;', sizes: ['T', 'S'] },
    hourly:     { name: 'Hourly forecast', glyph: '&#9719;', sizes: ['W', 'L'] },
    daily:      { name: '10-day forecast', glyph: '&#9638;', sizes: ['L', 'W'] },
    precip:     { name: 'Precipitation',   glyph: '&#8942;', sizes: ['S', 'W'] },
    wind:       { name: 'Wind',            glyph: '&#8776;', sizes: ['S', 'W'] },
    sun:        { name: 'Sunrise & sunset', glyph: '&#9788;', sizes: ['S', 'W'] },
    uv:         { name: 'UV index',        glyph: '&#9728;', sizes: ['S', 'W'] },
    aqi:        { name: 'Air quality',     glyph: '&#8779;', sizes: ['S', 'W'] },
    visibility: { name: 'Visibility',      glyph: '&#9673;', sizes: ['S', 'W'] },
    humidity:   { name: 'Humidity',        glyph: '&#9679;', sizes: ['S', 'W'] },
    pressure:   { name: 'Pressure',        glyph: '&#8661;', sizes: ['S', 'W'] },
    feels:      { name: 'Feels like',      glyph: '&#9832;', sizes: ['S', 'W'] },
    moon:       { name: 'Moon',            glyph: '&#9790;', sizes: ['S', 'W'] },
    radar:      { name: 'Radar',           glyph: '&#9678;', sizes: ['L', 'W'] },
  };
  const DEFAULT_LAYOUT = [
    ['alerts', 'T'], ['brief', 'W'], ['insight', 'T'], ['hourly', 'W'], ['daily', 'L'],
    ['precip', 'S'], ['wind', 'S'], ['sun', 'S'], ['uv', 'S'], ['aqi', 'S'], ['visibility', 'S'],
    ['humidity', 'S'], ['pressure', 'S'], ['feels', 'S'], ['moon', 'S'], ['radar', 'L'],
  ].map(([id, size]) => ({ id, size, hidden: false }));
  const DEFAULT_LOC = { id: 'edson-ab', name: 'Edson', admin: 'Alberta', country: 'Canada', cc: 'CA', lat: 53.5859, lon: -116.4428 };

  function loadState() {
    let s = {};
    try { s = JSON.parse(localStorage.getItem(STORE)) || {}; } catch (e) { /* private mode */ }
    const layout = Array.isArray(s.layout) ? s.layout.filter(w => WIDGETS[w.id]) : DEFAULT_LAYOUT.map(w => ({ ...w }));
    DEFAULT_LAYOUT.forEach(d => { if (!layout.some(w => w.id === d.id)) layout.push({ ...d, hidden: true }); });
    return {
      locations: Array.isArray(s.locations) && s.locations.length ? s.locations : [DEFAULT_LOC],
      activeId: s.activeId,
      units: { temp: 'C', wind: 'kmh', precip: 'mm', pressure: 'hPa', dist: 'km', clock: '24', ...(s.units || {}) },
      aqScale: s.aqScale || 'aqhi',
      thresholds: { cold: -20, hot: 30, gust: 70, snow: 10, ...(s.thresholds || {}) },
      gpsAuto: s.gpsAuto !== false,
      firstRun: !s.locations,
      layout,
    };
  }
  const state = loadState();
  if (!state.locations.some(l => l.id === state.activeId)) state.activeId = state.locations[0].id;
  const save = () => {
    const { firstRun, ...rest } = state;
    try { localStorage.setItem(STORE, JSON.stringify(rest)); } catch (e) { /* ignore */ }
  };
  const activeLoc = () => state.locations.find(l => l.id === state.activeId) || state.locations[0];
  let editing = false;

  // ---------------------------------------------------------------- units & format
  const U = state.units;
  const fT = c => (c == null ? '--' : Math.round(U.temp === 'F' ? c * 9 / 5 + 32 : c)) + '°';
  const WIND = { kmh: [1, 'km/h'], mph: [0.621371, 'mph'], ms: [1 / 3.6, 'm/s'], kn: [0.539957, 'kn'] };
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
  const naive = s => Date.parse(s.length === 10 ? s + 'T00:00Z' : s + 'Z');
  const DOW = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const dowOf = d => DOW[new Date(d + 'T12:00Z').getUTCDay()];
  const compass = deg => ['N', 'NNE', 'NE', 'ENE', 'E', 'ESE', 'SE', 'SSE', 'S', 'SSW', 'SW', 'WSW', 'W', 'WNW', 'NW', 'NNW'][Math.round(((deg % 360) / 22.5)) % 16];
  const compass8 = deg => ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'][Math.round(((deg % 360) / 45)) % 8];

  // ---------------------------------------------------------------- weather codes
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
  const ico = (c, day = 1) => icon((WMO[c] || [0, 'clear'])[1], !!day);
  const isSnow = c => (c >= 71 && c <= 77) || c === 85 || c === 86;
  const isWet = c => c >= 51;

  // ---------------------------------------------------------------- data
  const CACHE_KEY = id => 'bw.cache.' + id;
  const FRESH_MS = 10 * 60 * 1000;
  const mem = {};
  function readCache(id) {
    if (mem[id]) return mem[id];
    try { const c = JSON.parse(localStorage.getItem(CACHE_KEY(id))); if (c && c.w) return (mem[id] = c); } catch (e) { /* */ }
    return null;
  }
  function writeCache(id, data) {
    mem[id] = data;
    try { localStorage.setItem(CACHE_KEY(id), JSON.stringify(data)); } catch (e) { /* quota */ }
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

  const inflight = {};
  async function fetchAll(l, force) {
    const c = readCache(l.id);
    if (!force && c && Date.now() - c.t < FRESH_MS) return c;
    if (inflight[l.id]) return inflight[l.id];
    inflight[l.id] = (async () => {
      const [w, aq, al] = await Promise.allSettled([getJSON(forecastURL(l)), getJSON(airURL(l)), officialAlerts(l)]);
      if (w.status !== 'fulfilled') {
        if (c) return { ...c, stale: true };
        throw w.reason;
      }
      const data = {
        t: Date.now(), w: w.value,
        aq: aq.status === 'fulfilled' ? aq.value : c && c.aq,
        alerts: al.status === 'fulfilled' ? al.value : (c && c.alerts) || [],
      };
      writeCache(l.id, data);
      return data;
    })();
    try { return await inflight[l.id]; } finally { delete inflight[l.id]; }
  }

  // ---------------------------------------------------------------- geocoding
  async function searchPlaces(q) {
    const out = [];
    const add = r => {
      if (out.some(o => Math.abs(o.lat - r.lat) < 0.05 && Math.abs(o.lon - r.lon) < 0.05)) return;
      out.push(r);
    };
    const [ph, om] = await Promise.allSettled([
      getJSON(`https://photon.komoot.io/api/?q=${encodeURIComponent(q)}&limit=8&lang=en&osm_tag=place`, 8000),
      getJSON(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(q)}&count=8&language=en&format=json`, 8000),
    ]);
    if (ph.status === 'fulfilled') (ph.value.features || []).forEach(f => {
      const p = f.properties;
      if (!/city|town|village|hamlet|locality|suburb|county|state|island|district|borough|municipality/.test(p.osm_value + ' ' + p.type)) return;
      add({ name: p.name, admin: p.state || '', country: p.country, cc: (p.countrycode || '').toUpperCase(), lat: f.geometry.coordinates[1], lon: f.geometry.coordinates[0] });
    });
    if (om.status === 'fulfilled') (om.value.results || []).filter(r => r.name.toLowerCase().startsWith(q.trim().toLowerCase().split(/[ ,]/)[0])).forEach(r => add({ name: r.name, admin: r.admin1 || '', country: r.country || '', cc: (r.country_code || '').toUpperCase(), lat: r.latitude, lon: r.longitude }));
    return out.slice(0, 12);
  }
  async function reverseGeocode(lat, lon) {
    try {
      const j = await getJSON(`https://photon.komoot.io/reverse?lat=${lat}&lon=${lon}&lang=en`, 8000);
      const p = (j.features[0] || {}).properties || {};
      return { name: p.city || p.town || p.village || p.locality || p.county || p.name || 'My location', admin: p.state || '', country: p.country || '', cc: (p.countrycode || '').toUpperCase() };
    } catch (e) {
      return { name: 'My location', admin: `${lat.toFixed(2)}, ${lon.toFixed(2)}`, country: '', cc: '' };
    }
  }
  function getPosition() {
    return new Promise((res, rej) => {
      if (!navigator.geolocation) return rej(new Error('Location is not available on this device'));
      navigator.geolocation.getCurrentPosition(p => res(p.coords), e => rej(new Error(e.code === 1 ? 'Location permission denied' : 'Could not get location')), { enableHighAccuracy: false, timeout: 15000, maximumAge: 5 * 60 * 1000 });
    });
  }
  async function useMyLocation(select = true) {
    const c = await getPosition();
    const lat = +c.latitude.toFixed(4), lon = +c.longitude.toFixed(4);
    const prev = state.locations.find(l => l.id === 'gps');
    const moved = !prev || Math.abs(prev.lat - lat) > 0.01 || Math.abs(prev.lon - lon) > 0.01;
    const place = moved ? await reverseGeocode(lat, lon) : prev;
    const loc = { ...place, id: 'gps', gps: true, lat, lon };
    if (prev) Object.assign(prev, loc); else state.locations.unshift(loc);
    if (moved) { delete mem.gps; try { localStorage.removeItem(CACHE_KEY('gps')); } catch (e) { /* */ } }
    if (select) state.activeId = 'gps';
    save();
    return loc;
  }

  // ---------------------------------------------------------------- derived data
  function ctxFor(loc, data) {
    const w = data.w, H = w.hourly, D = w.daily;
    const off = w.utc_offset_seconds * 1000;
    const nowN = Date.now() + off; // "naive" local ms of the location
    let i0 = H.time.findIndex(t => naive(t) > nowN) - 1;
    if (i0 < 0) i0 = 0;
    const todayStr = new Date(nowN).toISOString().slice(0, 10);
    let d0 = D.time.indexOf(todayStr);
    if (d0 < 0) d0 = 0;
    return { loc, data, w, H, D, cur: w.current, aq: data.aq, alerts: data.alerts || [], i0, d0, nowN, off };
  }
  const hoursAhead = (c, n) => { const out = []; for (let i = c.i0; i < Math.min(c.H.time.length, c.i0 + n); i++) out.push(i); return out; };
  const dayHours = (c, d) => c.H.time.map((t, i) => [t, i]).filter(([t]) => t.startsWith(c.D.time[d])).map(([, i]) => i);

  function customAlerts(c) {
    const T = state.thresholds, out = [];
    const hrs = hoursAhead(c, 48);
    const minI = hrs.reduce((a, i) => (c.H.temperature_2m[i] < c.H.temperature_2m[a] ? i : a), hrs[0]);
    const maxI = hrs.reduce((a, i) => (c.H.temperature_2m[i] > c.H.temperature_2m[a] ? i : a), hrs[0]);
    const gI = hrs.reduce((a, i) => (c.H.wind_gusts_10m[i] > c.H.wind_gusts_10m[a] ? i : a), hrs[0]);
    const when = i => dowOf(c.H.time[i].slice(0, 10)) + ' ' + fHour(c.H.time[i]);
    if (T.cold != null && c.H.temperature_2m[minI] <= T.cold) out.push({ type: 'custom', title: `Below ${fT(T.cold)}`, name: `Drops to ${fT(c.H.temperature_2m[minI])} ${when(minI)}`, text: 'Your cold threshold alert.' });
    if (T.hot != null && c.H.temperature_2m[maxI] >= T.hot) out.push({ type: 'custom', title: `Above ${fT(T.hot)}`, name: `Reaches ${fT(c.H.temperature_2m[maxI])} ${when(maxI)}`, text: 'Your heat threshold alert.' });
    if (T.gust != null && c.H.wind_gusts_10m[gI] >= T.gust) out.push({ type: 'custom', title: `Gusts over ${fW(T.gust)}`, name: `Gusts to ${fW(c.H.wind_gusts_10m[gI])} ${when(gI)}`, text: 'Your wind threshold alert.' });
    const snow = hrs.reduce((s, i) => s + (c.H.snowfall[i] || 0), 0);
    if (T.snow != null && snow >= T.snow) out.push({ type: 'custom', title: `Snow ${Math.round(snow)} cm`, name: `About ${Math.round(snow)} cm of snow in the next 48 h`, text: 'Your snowfall threshold alert.' });
    return out;
  }
  const allAlerts = c => [...c.alerts, ...customAlerts(c)];

  function tempWord(t) { return t <= -20 ? 'bitterly cold' : t <= -5 ? 'very cold' : t <= 5 ? 'cold' : t <= 14 ? 'cool' : t <= 22 ? 'mild' : t <= 28 ? 'warm' : 'hot'; }
  function trend(c) {
    const D = c.D, d = c.d0;
    if (d + 2 >= D.time.length) return null;
    const diff = (D.temperature_2m_max[d + 1] + D.temperature_2m_max[d + 2]) / 2 - D.temperature_2m_max[d];
    if (diff >= 3) return 'warming'; if (diff <= -3) return 'cooling';
    return null;
  }
  function partOfDay(s) { const h = +s.slice(11, 13); return h < 5 ? 'overnight' : h < 12 ? 'this morning' : h < 17 ? 'this afternoon' : h < 21 ? 'this evening' : 'tonight'; }
  function brief(c) {
    const hrs = hoursAhead(c, 8);
    const codes = hrs.map(i => c.H.weather_code[i]);
    const dom = codes.sort((a, b) => codes.filter(x => x === b).length - codes.filter(x => x === a).length)[0];
    const avg = hrs.reduce((s, i) => s + c.H.temperature_2m[i], 0) / hrs.length;
    const sky = condName(dom).toLowerCase();
    let s = `Expect ${sky} and ${tempWord(avg)} conditions ${partOfDay(c.H.time[c.i0])}`;
    const tr = trend(c);
    s += tr ? `, with ${tr} trends anticipated over the next two days.` : '.';
    const wetI = hoursAhead(c, 12).find(i => c.H.precipitation_probability[i] >= 50);
    if (wetI != null) s += ` ${isSnow(c.H.weather_code[wetI]) ? 'Snow' : 'Precipitation'} likely from around ${fHour(c.H.time[wetI])}.`;
    const g = Math.max(...hoursAhead(c, 12).map(i => c.H.wind_gusts_10m[i]));
    if (g >= 50) s += ` Gusty winds up to ${fW(g)}.`;
    return s;
  }
  function briefExtra(c) {
    const D = c.D, d = c.d0, out = [];
    out.push(`Tonight: low ${fT(D.temperature_2m_min[Math.min(d + 1, D.time.length - 1)])}${D.temperature_2m_min[d + 1] <= 0 ? ', below freezing' : ''}.`);
    if (d + 1 < D.time.length) out.push(`Tomorrow: ${condName(D.weather_code[d + 1]).toLowerCase()}, high ${fT(D.temperature_2m_max[d + 1])}, ${D.precipitation_probability_max[d + 1] ?? 0}% chance of precipitation.`);
    const nextWet = D.time.findIndex((t, k) => k > d && D.precipitation_probability_max[k] >= 50);
    if (nextWet > 0) out.push(`Next wet day: ${dowOf(D.time[nextWet])} (${D.precipitation_probability_max[nextWet]}%).`);
    return out;
  }
  function insight(c) {
    const D = c.D, d = c.d0;
    const a = allAlerts(c);
    if (a.length) return { glyph: '!', text: a[0].title + (a[0].name && a[0].type === 'custom' ? ' — ' + a[0].name : '') };
    const snowDay = D.time.findIndex((t, k) => k >= d && k < d + 4 && D.snowfall_sum[k] >= 1);
    if (snowDay >= 0) return { glyph: '*', text: `Snow expected ${snowDay === d ? 'today' : dowOf(D.time[snowDay])} — about ${Math.round(D.snowfall_sum[snowDay])} cm` };
    const tr = trend(c);
    if (tr) return { glyph: tr === 'warming' ? '&#8599;' : '&#8600;', text: `${tr === 'warming' ? 'Warming' : 'Cooling'} expected over the next 2 days` };
    if (D.temperature_2m_min[d] <= 0 && D.temperature_2m_max[d] > 0) return { glyph: '*', text: 'Freezing overnight — watch for frost and ice' };
    if (D.uv_index_max[d] >= 6) return { glyph: '&#9728;', text: `High UV today (${Math.round(D.uv_index_max[d])}) — sunscreen up` };
    const rainy = D.time.findIndex((t, k) => k > d && D.precipitation_probability_max[k] >= 60);
    if (rainy > 0) return { glyph: '&#8942;', text: `Wet weather likely ${dowOf(D.time[rainy])}` };
    return { glyph: '&#10003;', text: 'Steady weather — no big changes ahead' };
  }

  // AQHI (Canada) from 3-hour average O3, NO2 (ppb) and PM2.5 (µg/m³)
  function aqhi(c) {
    const A = c.aq && c.aq.hourly;
    if (!A) return null;
    const offA = c.aq.utc_offset_seconds * 1000;
    const nowN = Date.now() + offA;
    let k = A.time.findIndex(t => naive(t) > nowN) - 1;
    if (k < 2) return null;
    const avg = arr => { const v = [arr[k], arr[k - 1], arr[k - 2]].filter(x => x != null); return v.length ? v.reduce((s, x) => s + x, 0) / v.length : null; };
    const o3 = avg(A.ozone), no2 = avg(A.nitrogen_dioxide), pm = avg(A.pm2_5);
    if (o3 == null || no2 == null || pm == null) return null;
    const v = (1000 / 10.4) * ((Math.exp(0.000537 * o3 / 1.96) - 1) + (Math.exp(0.000871 * no2 / 1.88) - 1) + (Math.exp(0.000487 * pm) - 1));
    return Math.max(1, Math.round(v));
  }
  const AQHI_COL = ['#00ccff', '#0099cc', '#006699', '#ffff00', '#ffcc00', '#ff9933', '#ff6666', '#ff0000', '#cc0000', '#990000', '#660000'];
  function aqInfo(c) {
    if (state.aqScale === 'aqhi') {
      const v = aqhi(c);
      if (v == null) return null;
      const risk = v <= 3 ? 'Low' : v <= 6 ? 'Moderate' : v <= 10 ? 'High' : 'Very high';
      return { v: v > 10 ? '10+' : v, n: Math.min(v, 11), max: 11, colors: AQHI_COL, label: risk + ' health risk', scale: 'AQHI' };
    }
    const v = c.aq && c.aq.current && c.aq.current.us_aqi;
    if (v == null) return null;
    const bands = [[50, 'Good', '#00e400'], [100, 'Moderate', '#ffff00'], [150, 'Unhealthy for sensitive groups', '#ff7e00'], [200, 'Unhealthy', '#ff0000'], [300, 'Very unhealthy', '#8f3f97'], [9999, 'Hazardous', '#7e0023']];
    const b = bands.find(x => v <= x[0]);
    const cols = []; bands.forEach(x => { cols.push(x[2], x[2]); });
    return { v: Math.round(v), n: Math.min(12, Math.ceil(v / 25) || 1), max: 12, colors: cols, label: b[1], scale: 'US AQI' };
  }
  const uvInfo = v => (v < 3 ? ['Low', '#69f0ae'] : v < 6 ? ['Moderate', '#ffeb3b'] : v < 8 ? ['High', '#ff9f1c'] : v < 11 ? ['Very high', '#ff5252'] : ['Extreme', '#b388ff']);
  const beaufort = k => ['Calm', 'Light air', 'Light breeze', 'Gentle breeze', 'Moderate breeze', 'Fresh breeze', 'Strong breeze', 'Near gale', 'Gale', 'Strong gale', 'Storm', 'Violent storm', 'Hurricane'][[1, 6, 12, 20, 29, 39, 50, 62, 75, 89, 103, 118, Infinity].findIndex(x => k < x)];
  function moonPhase(ms) {
    const syn = 29.530588853, ref = Date.UTC(2000, 0, 6, 18, 14);
    const p = (((ms - ref) / 86400000) % syn + syn) % syn / syn;
    const names = ['New moon', 'Waxing crescent', 'First quarter', 'Waxing gibbous', 'Full moon', 'Waning gibbous', 'Last quarter', 'Waning crescent'];
    return { p, name: names[Math.round(p * 8) % 8], illum: Math.round((1 - Math.cos(p * 2 * Math.PI)) / 2 * 100), nextFull: Math.round(((0.5 - p + 1) % 1) * syn) };
  }

  // ---------------------------------------------------------------- small svg helpers
  function segBar(n, max, colors, markAt) {
    let s = '<div class="seg">';
    for (let i = 0; i < max; i++) s += `<span class="${i < n ? 'on' : ''}${markAt === i ? ' mark' : ''}" style="background:${colors[Math.min(i, colors.length - 1)]}"></span>`;
    return s + '</div>';
  }
  function bars(vals, max, cls = '') {
    const m = max || Math.max(1, ...vals);
    return `<div class="bars ${cls}">${vals.map(v => `<span class="${v <= 0 ? 'z' : ''}" style="height:${Math.max(4, Math.round(v / m * 100))}%"></span>`).join('')}</div>`;
  }
  // pixel arc gauge: blocks along a 270° arc, fraction f lit
  function arcGauge(f, color, label, sub, blocks = 22) {
    let r = '';
    for (let i = 0; i < blocks; i++) {
      const a = (135 + i * 270 / (blocks - 1)) * Math.PI / 180;
      const x = 50 + Math.cos(a) * 40, y = 50 + Math.sin(a) * 40;
      r += `<rect x="${(x - 3).toFixed(1)}" y="${(y - 3).toFixed(1)}" width="6" height="6" fill="${i / (blocks - 1) <= f ? color : '#2c2c48'}"/>`;
    }
    return `<svg class="gauge" viewBox="0 0 100 92" shape-rendering="crispEdges">${r}
      <text x="50" y="52" text-anchor="middle" font-family="Press Start 2P" font-size="11" fill="#fff">${label}</text>
      <text x="50" y="68" text-anchor="middle" font-family="Press Start 2P" font-size="5" fill="#9e9e9e">${sub}</text></svg>`;
  }
  function compassSvg(deg, speed, unit) {
    let ticks = '';
    for (let i = 0; i < 16; i++) {
      const a = i * 22.5 * Math.PI / 180, big = i % 4 === 0;
      const x = 50 + Math.sin(a) * 40, y = 50 - Math.cos(a) * 40;
      ticks += `<rect x="${(x - (big ? 3 : 2)).toFixed(1)}" y="${(y - (big ? 3 : 2)).toFixed(1)}" width="${big ? 6 : 4}" height="${big ? 6 : 4}" fill="${big ? '#b388ff' : '#3d3d5c'}"/>`;
    }
    // arrow points where wind is going (from deg + 180)
    const to = (deg + 180) % 360;
    let arrow = '';
    for (let d = -30; d <= 30; d += 5) {
      const a = to * Math.PI / 180;
      arrow += `<rect x="${(50 + Math.sin(a) * d - 2).toFixed(1)}" y="${(50 - Math.cos(a) * d - 2).toFixed(1)}" width="4" height="4" fill="#ff9f1c"/>`;
    }
    const a = to * Math.PI / 180, hx = 50 + Math.sin(a) * 30, hy = 50 - Math.cos(a) * 30;
    const px = a2 => [50 + Math.sin(a2) * 22, 50 - Math.cos(a2) * 22];
    const [l1, l2] = px(a - 0.35), [r1, r2] = px(a + 0.35);
    arrow += `<polygon points="${hx},${hy} ${l1},${l2} ${r1},${r2}" fill="#ff9f1c"/>`;
    return `<svg class="gauge" viewBox="0 0 100 100" shape-rendering="crispEdges">${ticks}
      <text x="50" y="14" text-anchor="middle" font-family="Press Start 2P" font-size="5" fill="#9e9e9e">N</text>
      ${arrow}<rect x="30" y="40" width="40" height="22" fill="#1a1a2e"/>
      <text x="50" y="54" text-anchor="middle" font-family="Press Start 2P" font-size="11" fill="#fff">${speed}</text>
      <text x="50" y="62" text-anchor="middle" font-family="Press Start 2P" font-size="4" fill="#9e9e9e">${unit}</text></svg>`;
  }
  function sunArc(c, d) {
    const rise = naive(c.D.sunrise[d]), set = naive(c.D.sunset[d]);
    const f = (c.nowN - rise) / (set - rise);
    let dots = '';
    for (let i = 0; i <= 24; i++) {
      const t = i / 24, x = 8 + t * 84, y = 52 - Math.sin(t * Math.PI) * 38;
      dots += `<rect x="${(x - 2).toFixed(1)}" y="${(y - 2).toFixed(1)}" width="4" height="4" fill="${t <= f ? '#ff9f1c' : '#3d3d5c'}"/>`;
    }
    let sun = '';
    if (f >= 0 && f <= 1) {
      const x = 8 + f * 84, y = 52 - Math.sin(f * Math.PI) * 38;
      sun = `<rect x="${x - 6}" y="${y - 6}" width="12" height="12" fill="#ffc107" stroke="#0d0d1a" stroke-width="2"/>`;
    } else {
      sun = `<text x="50" y="44" text-anchor="middle" font-family="Press Start 2P" font-size="6" fill="#b388ff">${f < 0 ? 'BEFORE DAWN' : 'AFTER DUSK'}</text>`;
    }
    return `<svg class="gauge" viewBox="0 0 100 60" shape-rendering="crispEdges"><rect x="0" y="52" width="100" height="2" fill="#55556e"/>${dots}${sun}</svg>`;
  }
  function tempChart(c, idx) {
    const t = idx.map(i => c.H.temperature_2m[i]), p = idx.map(i => c.H.precipitation_probability[i] || 0);
    const lo = Math.min(...t), hi = Math.max(...t), span = Math.max(1, hi - lo);
    const W = 240, H = 70, bw = W / t.length;
    let s = '';
    p.forEach((v, k) => { if (v > 0) s += `<rect x="${(k * bw).toFixed(1)}" y="${H - v / 100 * 22}" width="${(bw - 1).toFixed(1)}" height="${v / 100 * 22}" fill="#4fc3f7" opacity=".5"/>`; });
    t.forEach((v, k) => {
      const y = 8 + (1 - (v - lo) / span) * (H - 34);
      s += `<rect x="${(k * bw).toFixed(1)}" y="${y.toFixed(1)}" width="${(bw).toFixed(1)}" height="4" fill="#ff9f1c"/>`;
      if (k % 6 === 0) s += `<text x="${(k * bw + 1).toFixed(1)}" y="${(y - 3).toFixed(1)}" font-family="Press Start 2P" font-size="6" fill="#fff">${fT(v)}</text>`;
    });
    return `<svg class="gauge" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" shape-rendering="crispEdges">${s}</svg>`;
  }

  // ---------------------------------------------------------------- widgets
  const head = (id) => `<div class="wh"><span class="gl">${WIDGETS[id].glyph}</span>${esc(WIDGETS[id].name.toUpperCase())}</div>`;
  const R = {};

  R.alerts = (c, size) => {
    const a = allAlerts(c);
    if (!a.length) return `${head('alerts')}<div class="wb"><div class="txt">&#10003; All clear — no alerts${c.loc.cc === 'CA' || c.loc.cc === 'US' ? '' : ' (official alerts: Canada & US)'}.</div></div>`;
    if (size === 'T' || size === 'S') {
      const top = a[0];
      return `${head('alerts')}<div class="wb"><div class="alert ${top.type}" data-alert="0"><b>${esc(top.title)}</b>${a.length > 1 ? ` <span class="s">+${a.length - 1} more</span>` : ''}</div></div>`;
    }
    return `${head('alerts')}<div class="wb" style="overflow:auto">${a.map((x, k) => `<div class="alert ${x.type}" data-alert="${k}"><b>${esc(x.title)}</b><div class="s">${esc(x.name || '')}${x.until ? ' · until ' + esc(new Date(x.until).toLocaleString([], { weekday: 'short', hour: 'numeric', minute: '2-digit' })) : ''}</div></div>`).join('')}</div>`;
  };
  R.brief = (c, size) => `${head('brief')}<div class="wb" style="overflow:auto"><div class="txt">${esc(brief(c))}</div>${size === 'L' ? `<div class="txt" style="margin-top:12px;color:var(--dim)">${briefExtra(c).map(esc).join('<br>')}</div>` : ''}</div>`;
  R.insight = (c, size) => {
    const x = insight(c);
    return `${head('insight')}<div class="wb${size === 'S' ? ' center' : ''}"><div class="txt">${size === 'S' ? `<div class="big" style="color:var(--orange)">${x.glyph}</div><br>` : ''}${esc(x.text)}</div></div>`;
  };
  R.hourly = (c, size) => {
    const idx = hoursAhead(c, size === 'L' ? 48 : 24);
    const strip = `<div class="strip">${idx.map((i, k) => `<div class="hcell${k === 0 ? ' now' : ''}"><div class="t">${fT(c.H.temperature_2m[i])}</div><div class="ic">${ico(c.H.weather_code[i], c.H.is_day[i])}</div><div class="p">${c.H.precipitation_probability[i] ? c.H.precipitation_probability[i] + '%' : '&nbsp;'}</div><div class="h">${k === 0 ? 'Now' : fHour(c.H.time[i])}</div></div>`).join('')}</div>`;
    if (size !== 'L') return head('hourly') + `<div class="wb">${strip}</div>`;
    return head('hourly') + `<div class="wb"><div style="flex:1;min-height:0">${tempChart(c, idx.slice(0, 24))}</div><div class="axis"><span>Now</span><span>+6h</span><span>+12h</span><span>+18h</span><span>+24h</span></div><div style="height:50%;display:flex;margin-top:8px">${strip}</div></div>`;
  };
  R.daily = (c, size) => {
    const D = c.D, ds = D.time.map((t, k) => k).filter(k => k >= c.d0).slice(0, 10);
    if (size === 'W') {
      return head('daily') + `<div class="wb"><div class="strip">${ds.map(k => `<div class="pill" data-day="${k}"><div class="hi">${fT(D.temperature_2m_max[k])}</div><div class="lo">${fT(D.temperature_2m_min[k])}</div><div class="ic">${ico(D.weather_code[k])}</div><div class="p">${D.precipitation_probability_max[k] ? D.precipitation_probability_max[k] + '%' : ''}</div><div class="d">${k === c.d0 ? 'Today' : dowOf(D.time[k])}</div><div class="dt">${D.time[k].slice(5)}</div></div>`).join('')}</div></div>`;
    }
    const lo = Math.min(...ds.map(k => D.temperature_2m_min[k])), hi = Math.max(...ds.map(k => D.temperature_2m_max[k])), span = Math.max(1, hi - lo);
    const curT = c.cur.temperature_2m;
    return head('daily') + `<div class="wb"><div class="dlist">${ds.map(k => {
      const a = (D.temperature_2m_min[k] - lo) / span * 100, b = (D.temperature_2m_max[k] - lo) / span * 100;
      const dot = k === c.d0 ? `<i style="left:calc(${((curT - lo) / span * 100).toFixed(1)}% - 2px)"></i>` : '';
      return `<div class="drow" data-day="${k}"><span>${k === c.d0 ? 'Today' : dowOf(D.time[k])}</span><span class="ic">${ico(D.weather_code[k])}</span><span class="p">${D.precipitation_probability_max[k] ? D.precipitation_probability_max[k] + '%' : ''}</span><span class="lo">${fT(D.temperature_2m_min[k])}</span><span class="rbar"><b style="left:${a.toFixed(1)}%;right:${(100 - b).toFixed(1)}%"></b>${dot}</span><span class="hi">${fT(D.temperature_2m_max[k])}</span></div>`;
    }).join('')}</div></div>`;
  };
  R.precip = (c, size) => {
    const d = c.d0, D = c.D, sum = D.precipitation_sum[d] || 0, snow = D.snowfall_sum[d] || 0;
    const kind = snow > 0 && snow * 10 >= (D.rain_sum[d] || 0) ? 'snow' : 'rain';
    const main = `<div class="big">${sum > 0 && sum < 0.1 ? '&lt;' : ''}${fP(sum)}<small>${pU()}</small></div><div class="lbl" style="margin-top:8px">Total ${kind} for the day<br>${D.precipitation_probability_max[d] ?? 0}% chance${snow > 0 ? ` · ${snow.toFixed(1)} cm snow` : ''}</div>`;
    if (size === 'S') return head('precip') + `<div class="wb" style="justify-content:center">${main}</div>`;
    const idx = hoursAhead(c, 24);
    return head('precip') + `<div class="wb"><div class="row" style="align-items:flex-start"><div class="grow">${main}</div></div>${bars(idx.map(i => c.H.precipitation_probability[i] || 0), 100)}<div class="axis"><span>Now</span><span>Chance next 24 h</span><span>+24h</span></div></div>`;
  };
  R.wind = (c, size) => {
    const k = c.cur.wind_speed_10m, dir = c.cur.wind_direction_10m;
    const g = `<div style="flex:1;min-height:0">${compassSvg(dir, fWnum(k), WIND[U.wind][1])}</div><div class="lbl" style="text-align:center">From ${compass(dir)}</div>`;
    if (size === 'S') return head('wind') + `<div class="wb">${g}</div>`;
    const d = c.d0;
    return head('wind') + `<div class="wb"><div class="row" style="flex:1;min-height:0"><div style="width:50%;height:100%;display:flex;flex-direction:column">${g}</div><div class="grow kv" style="grid-template-columns:1fr">
      <div>Now<b>${esc(beaufort(k))}</b></div><div>Gusts<b>${fW(c.cur.wind_gusts_10m)}</b></div><div>Today max<b>${fW(c.D.wind_speed_10m_max[d])}</b></div><div>Peak gust<b>${fW(c.D.wind_gusts_10m_max[d])} ${compass8(c.D.wind_direction_10m_dominant[d])}</b></div></div></div></div>`;
  };
  R.sun = (c, size) => {
    const d = c.d0, D = c.D;
    const times = `<div class="lbl" style="color:#fff">&#9788; ${fClock(D.sunrise[d])}<br>&#9790; ${fClock(D.sunset[d])}</div>`;
    if (size === 'S') return head('sun') + `<div class="wb"><div style="flex:1;min-height:0">${sunArc(c, d)}</div>${times}</div>`;
    const len = D.daylight_duration[d], dl = d + 1 < D.time.length ? Math.round((D.daylight_duration[d + 1] - len) / 60) : 0;
    const h = Math.floor(len / 3600), m = Math.round(len % 3600 / 60);
    return head('sun') + `<div class="wb"><div style="flex:1;min-height:0">${sunArc(c, d)}</div><div class="kv" style="grid-template-columns:repeat(3,1fr)"><div>Sunrise<b>${fClock(D.sunrise[d])}</b></div><div>Sunset<b>${fClock(D.sunset[d])}</b></div><div>Daylight<b>${h}h ${m}m</b></div></div><div class="lbl" style="margin-top:6px">${dl === 0 ? 'Same daylight tomorrow' : `${Math.abs(dl)} min ${dl > 0 ? 'more' : 'less'} daylight tomorrow`}</div></div>`;
  };
  R.uv = (c, size) => {
    const v = c.cur.uv_index ?? 0, [lbl] = uvInfo(v);
    const cols = Array.from({ length: 12 }, (_, i) => uvInfo(i)[1]);
    const main = `<div class="big">${Math.round(v)}</div><div class="lbl">${lbl}</div>${segBar(0, 12, cols, Math.min(11, Math.round(v)))}`;
    if (size === 'S') return head('uv') + `<div class="wb" style="justify-content:center">${main}<div class="lbl">Max today ${Math.round(c.D.uv_index_max[c.d0])}</div></div>`;
    const idx = dayHours(c, c.d0).filter(i => +c.H.time[i].slice(11, 13) >= 6 && +c.H.time[i].slice(11, 13) <= 20);
    const peak = idx.reduce((a, i) => (c.H.uv_index[i] > c.H.uv_index[a] ? i : a), idx[0]);
    return head('uv') + `<div class="wb">${main}${bars(idx.map(i => c.H.uv_index[i]), 11)}<div class="axis"><span>6:00</span><span>Peak ${Math.round(c.H.uv_index[peak])} at ${fHour(c.H.time[peak])}</span><span>20:00</span></div></div>`;
  };
  R.aqi = (c, size) => {
    const a = aqInfo(c);
    if (!a) return head('aqi') + `<div class="wb center"><div class="lbl">Air quality data unavailable</div></div>`;
    const main = `<div class="big">${a.v}<small>${a.scale}</small></div>${segBar(a.max, a.max, a.colors, a.n - 1)}<div class="lbl">${esc(a.label)}</div>`;
    if (size === 'S') return head('aqi') + `<div class="wb" style="justify-content:center">${main}</div>`;
    const cu = c.aq.current;
    return head('aqi') + `<div class="wb">${main}<div class="spacer"></div><div class="kv" style="grid-template-columns:repeat(3,1fr)"><div>PM2.5<b>${Math.round(cu.pm2_5)}</b></div><div>PM10<b>${Math.round(cu.pm10)}</b></div><div>O&#8323;<b>${Math.round(cu.ozone)}</b></div><div>NO&#8322;<b>${Math.round(cu.nitrogen_dioxide)}</b></div><div>SO&#8322;<b>${Math.round(cu.sulphur_dioxide)}</b></div><div>CO<b>${Math.round(cu.carbon_monoxide)}</b></div></div><div class="axis"><span>µg/m³</span><span>US AQI ${Math.round(cu.us_aqi)}</span></div></div>`;
  };
  R.visibility = (c, size) => {
    const m = c.cur.visibility, [v, u] = fD(m);
    const desc = m >= 20000 ? 'Perfectly clear' : m >= 10000 ? 'Clear' : m >= 4000 ? 'Light haze' : m >= 1000 ? 'Haze / mist' : 'Fog';
    const main = `<div class="big">${v}<small>${u}</small></div><div class="lbl" style="margin-top:8px">${desc}</div>`;
    if (size === 'S') return head('visibility') + `<div class="wb" style="justify-content:center">${main}</div>`;
    const idx = hoursAhead(c, 24);
    return head('visibility') + `<div class="wb">${main}${bars(idx.map(i => Math.min(c.H.visibility[i], 30000)), 30000)}<div class="axis"><span>Now</span><span>Next 24 h</span><span>+24h</span></div></div>`;
  };
  R.humidity = (c, size) => {
    const h = c.cur.relative_humidity_2m, dp = c.cur.dew_point_2m;
    const fill = Math.round(h / 100 * 8);
    const blocks = `<div class="seg" style="height:14px">${Array.from({ length: 8 }, (_, i) => `<span class="${i < fill ? 'on' : ''}" style="background:var(--cyan)"></span>`).join('')}</div>`;
    const main = `<div class="big">${Math.round(h)}%</div>${blocks}<div class="lbl">Dew point ${fT(dp)}</div>`;
    if (size === 'S') return head('humidity') + `<div class="wb" style="justify-content:center">${main}</div>`;
    const feel = dp < 10 ? 'Dry and comfortable' : dp < 16 ? 'Comfortable' : dp < 20 ? 'Slightly humid' : dp < 24 ? 'Humid' : 'Oppressive';
    const idx = hoursAhead(c, 24);
    return head('humidity') + `<div class="wb">${main}<div class="lbl">${feel}</div>${bars(idx.map(i => c.H.relative_humidity_2m[i]), 100)}<div class="axis"><span>Now</span><span>+24h</span></div></div>`;
  };
  R.pressure = (c, size) => {
    const p = c.cur.pressure_msl, f = Math.min(1, Math.max(0, (p - 960) / 90));
    const past = c.H.pressure_msl[Math.max(0, c.i0 - 3)], d = p - past;
    const tr = d > 1 ? '&#9650; Rising' : d < -1 ? '&#9660; Falling' : '&#9654; Steady';
    const g = `<div style="flex:1;min-height:0">${arcGauge(f, '#b388ff', fPr(p), U.pressure)}</div><div class="lbl" style="text-align:center">${tr}</div>`;
    if (size === 'S') return head('pressure') + `<div class="wb">${g}</div>`;
    const idx = []; for (let i = Math.max(0, c.i0 - 24); i <= Math.min(c.H.time.length - 1, c.i0 + 24); i++) idx.push(i);
    const vals = idx.map(i => c.H.pressure_msl[i]), lo = Math.min(...vals) - 1;
    return head('pressure') + `<div class="wb"><div class="row" style="flex:1;min-height:0"><div style="width:45%;height:100%;display:flex;flex-direction:column">${g}</div><div class="grow" style="height:100%;display:flex;flex-direction:column">${bars(vals.map(v => v - lo), Math.max(...vals) - lo)}<div class="axis"><span>-24h</span><span>now</span><span>+24h</span></div></div></div></div>`;
  };
  R.feels = (c, size) => {
    const t = c.cur.temperature_2m, a = c.cur.apparent_temperature, k = c.cur.wind_speed_10m;
    const why = a < t - 1 ? (k > 5 ? 'Wind chill makes it feel colder' : 'Feels colder than actual') : a > t + 1 ? (c.cur.relative_humidity_2m > 50 ? 'Humidity makes it feel warmer' : 'Sun makes it feel warmer') : 'Similar to the actual temperature';
    const main = `<div class="big">${fT(a)}</div><div class="lbl" style="margin-top:8px">Actual ${fT(t)}<br>${why}</div>`;
    if (size === 'S') return head('feels') + `<div class="wb" style="justify-content:center">${main}</div>`;
    const D = c.D, d = c.d0;
    return head('feels') + `<div class="wb">${main}<div class="spacer"></div><div class="kv"><div>Feels-like high<b>${fT(D.apparent_temperature_max[d])}</b></div><div>Feels-like low<b>${fT(D.apparent_temperature_min[d])}</b></div></div></div>`;
  };
  R.moon = (c, size) => {
    const m = moonPhase(Date.now());
    if (size === 'S') return head('moon') + `<div class="wb center"><div style="width:56px;height:56px">${moonPhaseIcon(m.p)}</div><div class="lbl" style="margin-top:8px;color:#fff">${m.name}</div><div class="lbl">${m.illum}% lit</div></div>`;
    return head('moon') + `<div class="wb"><div class="row" style="flex:1"><div style="width:72px;height:72px;flex:none">${moonPhaseIcon(m.p)}</div><div class="grow kv" style="grid-template-columns:1fr"><div>Phase<b>${m.name}</b></div><div>Illumination<b>${m.illum}%</b></div><div>Next full moon<b>${m.nextFull === 0 ? 'Tonight' : 'in ' + m.nextFull + ' days'}</b></div></div></div></div>`;
  };
  R.radar = () => head('radar') + `<div class="wb"><div class="radar" data-radar><div class="ctl"><button class="pxbtn" data-play>&#9654;</button><span data-rtime>loading…</span></div></div></div>`;

  // ---------------------------------------------------------------- radar (lazy Leaflet + RainViewer)
  let leafletP = null;
  function loadLeaflet() {
    if (window.L) return Promise.resolve();
    if (leafletP) return leafletP;
    leafletP = new Promise((res, rej) => {
      const css = document.createElement('link');
      css.rel = 'stylesheet'; css.href = 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css';
      document.head.appendChild(css);
      const s = document.createElement('script');
      s.src = 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js';
      s.onload = res; s.onerror = () => { leafletP = null; rej(new Error('map failed to load')); };
      document.head.appendChild(s);
    });
    return leafletP;
  }
  let radarMap = null, radarTimer = null;
  async function mountRadar(el, c) {
    const label = $('[data-rtime]', el);
    try {
      await loadLeaflet();
      const meta = await getJSON('https://api.rainviewer.com/public/weather-maps.json');
      const frames = [...(meta.radar.past || []), ...(meta.radar.nowcast || [])];
      if (radarMap) { radarMap.remove(); clearInterval(radarTimer); }
      const L = window.L;
      const map = radarMap = L.map(el, { zoomControl: false, attributionControl: true, dragging: true, scrollWheelZoom: false }).setView([c.loc.lat, c.loc.lon], 7);
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 12, className: 'darktiles', attribution: '&copy; OpenStreetMap · RainViewer' }).addTo(map);
      L.circleMarker([c.loc.lat, c.loc.lon], { radius: 5, color: '#ff9f1c', weight: 3, fillColor: '#0d0d1a', fillOpacity: 1 }).addTo(map);
      const layers = frames.map(f => L.tileLayer(`${meta.host}${f.path}/256/{z}/{x}/{y}/2/1_1.png`, { opacity: 0, maxNativeZoom: 7, maxZoom: 12, tileSize: 256 }).addTo(map));
      let cur = layers.length - 1 - ((meta.radar.nowcast || []).length);
      const show = k => {
        layers.forEach((ly, j) => ly.setOpacity(j === k ? 0.75 : 0));
        const d = new Date(frames[k].time * 1000);
        label.textContent = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: U.clock === '12' }) + (k >= (meta.radar.past || []).length ? ' FCST' : '');
      };
      show(cur);
      const play = $('[data-play]', el);
      play.onclick = e => {
        e.stopPropagation();
        if (radarTimer) { clearInterval(radarTimer); radarTimer = null; play.innerHTML = '&#9654;'; return; }
        play.innerHTML = '&#10074;&#10074;';
        radarTimer = setInterval(() => { cur = (cur + 1) % layers.length; show(cur); }, 600);
      };
    } catch (e) {
      label.textContent = 'Radar unavailable offline';
    }
  }

  // ---------------------------------------------------------------- rendering
  const app = $('#app');
  let current = null; // ctx for active location

  function skyFor(c) {
    const code = c ? c.cur.weather_code : 2, day = c ? c.cur.is_day : 1;
    const k = (WMO[code] || [0, 'clear'])[1];
    let bands;
    if (!day) bands = ['#0b0b24', '#141436', '#1d1d4a', '#28285e'];
    else if (k === 'clear' || k === 'mostly') bands = ['#1e3a8a', '#2850b0', '#3a6ad0', '#5a8ae6'];
    else if (k === 'partly') bands = ['#283593', '#3949ab', '#5463b8', '#7280c4'];
    else if (k === 'thunder') bands = ['#1a1030', '#2a1a48', '#3a2860', '#4a3878'];
    else if (k === 'snow') bands = ['#4a5578', '#5d6890', '#7480a6', '#8e98ba'];
    else bands = ['#2c3350', '#3a4266', '#4a547a', '#5c668e'];
    return `linear-gradient(180deg, ${bands.map((b, i) => `${b} ${i * 25}%, ${b} ${(i + 1) * 25}%`).join(', ')})`;
  }

  function renderShell() {
    app.innerHTML = `
      <div class="top">
        <button class="pxbtn" id="bLoc" aria-label="Locations">&#9776;</button>
        <div class="where"><div class="name" id="locName"></div><div class="upd" id="upd"></div></div>
        <button class="pxbtn" id="bRef" aria-label="Refresh">&#10227;</button>
        <button class="pxbtn" id="bEdit" aria-label="Edit widgets">&#9998;</button>
        <button class="pxbtn" id="bSet" aria-label="Settings">&#9881;</button>
      </div>
      <div class="hero" id="hero"></div>
      <div class="gridwrap"><div class="grid" id="grid"></div><div id="tray"></div></div>`;
    $('#bLoc').onclick = openLocations;
    $('#bSet').onclick = openSettings;
    $('#bRef').onclick = () => refresh(true);
    $('#bEdit').onclick = () => { editing = !editing; renderWidgets(); };
    bindSwipe($('#hero'));
  }

  function renderHeader(c, err) {
    const l = activeLoc();
    $('#locName').innerHTML = (l.gps ? '&#9678; ' : '') + esc(l.name).toUpperCase();
    const data = c && c.data;
    $('#upd').textContent = err ? err : data ? `${l.admin ? l.admin + ' · ' : ''}Updated ${new Date(data.t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: U.clock === '12' })}${data.stale ? ' (offline)' : ''}` : 'Loading…';
    const hero = $('#hero');
    hero.style.background = skyFor(c);
    const dots = state.locations.map(x => `<i class="${x.gps ? 'gps ' : ''}${x.id === l.id ? 'on' : ''}"></i>`).join('');
    if (!c) { hero.innerHTML = `<div class="empty" style="color:#fff">${err ? esc(err) : 'Reading the skies…'}</div><div class="dots">${dots}</div>`; return; }
    const D = c.D, d = c.d0;
    hero.innerHTML = `
      <div class="bigicon">${ico(c.cur.weather_code, c.cur.is_day)}</div>
      <div class="temp">${fT(c.cur.temperature_2m)}</div>
      <div class="cond">${esc(condName(c.cur.weather_code))}</div>
      <div class="sub">Feels like ${fT(c.cur.apparent_temperature)}<br>High ${fT(D.temperature_2m_max[d])} · Low ${fT(D.temperature_2m_min[d])}</div>
      <div class="dots">${dots}</div>
      ${state.locations.length > 1 ? '<div class="swipehint">&#9664; swipe for saved places &#9654;</div>' : ''}`;
  }

  function renderWidgets() {
    const grid = $('#grid'), tray = $('#tray');
    $('#bEdit').classList.toggle('on', editing);
    grid.parentElement.classList.toggle('edit', editing);
    if (radarTimer) { clearInterval(radarTimer); radarTimer = null; }
    if (radarMap) { radarMap.remove(); radarMap = null; }
    const c = current;
    if (!c) { grid.innerHTML = ''; tray.innerHTML = ''; return; }
    const vis = state.layout.filter(w => !w.hidden);
    grid.innerHTML = vis.map((w, k) => {
      let body;
      try { body = R[w.id](c, w.size); } catch (e) { console.error(w.id, e); body = head(w.id) + '<div class="wb center"><div class="lbl">No data</div></div>'; }
      const ed = editing ? `<div class="wedit"><div class="t">${esc(WIDGETS[w.id].name)}<br>${SIZE_NAMES[w.size]}</div><div class="btns">
        <button class="pxbtn" data-act="up" data-k="${k}" aria-label="Move earlier">&#9664;</button>
        ${WIDGETS[w.id].sizes.length > 1 ? `<button class="pxbtn" data-act="size" data-k="${k}">SIZE</button>` : ''}
        <button class="pxbtn" data-act="down" data-k="${k}" aria-label="Move later">&#9654;</button>
        <button class="pxbtn warn" data-act="hide" data-k="${k}" aria-label="Remove">&#10005;</button></div></div>` : '';
      return `<section class="w size-${w.size}" data-w="${w.id}">${body}${ed}</section>`;
    }).join('');
    const hidden = state.layout.filter(w => w.hidden);
    tray.innerHTML = editing ? `<div class="tray"><h3>+ ADD WIDGETS</h3><div class="chips">${hidden.length ? hidden.map(w => `<button class="pxbtn" data-add="${w.id}">+ ${esc(WIDGETS[w.id].name)}</button>`).join('') : '<span class="lbl">All widgets are on screen.</span>'}</div>
      <div class="opt" style="margin-top:14px"><button class="pxbtn primary" data-done>DONE</button><button class="pxbtn" data-reset>RESET LAYOUT</button></div>
      <p class="note">Tap SIZE to cycle small / wide / large. Arrows reorder. &#10005; removes.</p></div>` : '';
    if (!editing) {
      const r = $('[data-radar]', grid);
      if (r) mountRadar(r, c);
    }
  }

  // widget interactions (delegated)
  document.addEventListener('click', e => {
    const t = e.target.closest('[data-act],[data-add],[data-done],[data-reset],[data-day],[data-alert]');
    if (!t || !app.contains(t)) return;
    if (t.dataset.done != null) { editing = false; renderWidgets(); return; }
    if (t.dataset.reset != null) { state.layout = DEFAULT_LAYOUT.map(w => ({ ...w })); save(); renderWidgets(); return; }
    if (t.dataset.add) { const w = state.layout.find(x => x.id === t.dataset.add); w.hidden = false; state.layout.splice(state.layout.indexOf(w), 1); state.layout.push(w); save(); renderWidgets(); return; }
    if (t.dataset.act) {
      const vis = state.layout.filter(w => !w.hidden), w = vis[+t.dataset.k];
      const i = state.layout.indexOf(w);
      if (t.dataset.act === 'size') { const s = WIDGETS[w.id].sizes; w.size = s[(s.indexOf(w.size) + 1) % s.length]; }
      if (t.dataset.act === 'hide') w.hidden = true;
      if (t.dataset.act === 'up' || t.dataset.act === 'down') {
        const target = vis[+t.dataset.k + (t.dataset.act === 'up' ? -1 : 1)];
        if (target) { const j = state.layout.indexOf(target); state.layout[i] = target; state.layout[j] = w; }
      }
      save(); renderWidgets(); return;
    }
    if (editing) return;
    if (t.dataset.day != null) openDay(+t.dataset.day);
    if (t.dataset.alert != null) openAlerts(+t.dataset.alert);
  });

  // ---------------------------------------------------------------- sheets
  function sheet(title, html, onMount) {
    closeSheet();
    const bg = document.createElement('div');
    bg.className = 'sheet-bg'; bg.id = 'sheet';
    bg.innerHTML = `<div class="sheet" role="dialog" aria-label="${esc(title)}"><div class="sheet-h"><h2>${esc(title)}</h2><button class="pxbtn" data-close aria-label="Close">&#10005;</button></div><div class="sheet-b">${html}</div></div>`;
    bg.addEventListener('click', e => { if (e.target === bg || e.target.closest('[data-close]')) closeSheet(); });
    document.body.appendChild(bg);
    if (onMount) onMount($('.sheet-b', bg));
    return bg;
  }
  function closeSheet() { const s = $('#sheet'); if (s) s.remove(); }
  document.addEventListener('keydown', e => { if (e.key === 'Escape') closeSheet(); });
  function toast(msg) {
    const t = document.createElement('div');
    t.className = 'toast'; t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(() => t.remove(), 3200);
  }

  function openDay(k) {
    const c = current; if (!c) return;
    const D = c.D, idx = dayHours(c, k);
    const html = `
      <div class="row" style="margin-bottom:14px"><div style="width:64px;height:64px">${ico(D.weather_code[k])}</div><div class="grow"><div class="big">${fT(D.temperature_2m_max[k])} <small>/ ${fT(D.temperature_2m_min[k])}</small></div><div class="lbl" style="margin-top:6px">${esc(condName(D.weather_code[k]))}</div></div></div>
      <div class="w size-W" style="height:150px;margin-bottom:12px"><div class="wh">HOURLY</div><div class="strip">${idx.map(i => `<div class="hcell"><div class="t">${fT(c.H.temperature_2m[i])}</div><div class="ic">${ico(c.H.weather_code[i], c.H.is_day[i])}</div><div class="p">${c.H.precipitation_probability[i] ? c.H.precipitation_probability[i] + '%' : '&nbsp;'}</div><div class="h">${fHour(c.H.time[i])}</div></div>`).join('')}</div></div>
      <div class="kv" style="grid-template-columns:1fr 1fr">
        <div>Precipitation<b>${fP(D.precipitation_sum[k] || 0)} ${pU()} · ${D.precipitation_probability_max[k] ?? 0}%</b></div>
        <div>Snowfall<b>${(D.snowfall_sum[k] || 0).toFixed(1)} cm</b></div>
        <div>Wind<b>${fW(D.wind_speed_10m_max[k])} ${compass8(D.wind_direction_10m_dominant[k])}</b></div>
        <div>Gusts<b>${fW(D.wind_gusts_10m_max[k])}</b></div>
        <div>Feels like<b>${fT(D.apparent_temperature_max[k])} / ${fT(D.apparent_temperature_min[k])}</b></div>
        <div>UV max<b>${Math.round(D.uv_index_max[k] || 0)} ${uvInfo(D.uv_index_max[k] || 0)[0]}</b></div>
        <div>Sunrise<b>${fClock(D.sunrise[k])}</b></div><div>Sunset<b>${fClock(D.sunset[k])}</b></div>
      </div>`;
    sheet(`${k === c.d0 ? 'TODAY' : dowOf(D.time[k]).toUpperCase()} ${D.time[k].slice(5)}`, html);
  }
  function openAlerts(k) {
    const a = allAlerts(current);
    sheet('ALERTS', a.map((x, i) => `<div class="alert ${x.type}" style="cursor:default${i === k ? ';outline:2px solid #fff' : ''}"><b>${esc(x.title)}</b><div class="s">${esc(x.name || '')}${x.until ? ' · until ' + esc(new Date(x.until).toLocaleString()) : ''}</div>${x.text ? `<pre class="alerttext" style="margin-top:8px">${esc(x.text)}</pre>` : ''}${x.src ? `<div class="s" style="margin-top:6px">Source: ${esc(x.src)}</div>` : ''}</div>`).join('') + '<p class="note">Custom alerts come from your thresholds in Settings.</p>');
  }

  function locRow(l) {
    const cc = readCache(l.id);
    let ic = '', tt = '', sub = esc([l.admin, l.country].filter(Boolean).join(', '));
    if (cc && cc.w) {
      ic = ico(cc.w.current.weather_code, cc.w.current.is_day);
      tt = fT(cc.w.current.temperature_2m);
      sub += ` · ${fT(cc.w.daily.temperature_2m_max[1])}/${fT(cc.w.daily.temperature_2m_min[1])}`;
    }
    const i = state.locations.indexOf(l);
    return `<div class="li${l.id === state.activeId ? ' on' : ''}" data-id="${esc(l.id)}"><div class="ic">${ic}</div><div class="main" data-pick="${esc(l.id)}"><div class="n">${l.gps ? '&#9678; ' : ''}${esc(l.name)}</div><div class="a">${sub}</div></div><div class="tt">${tt}</div>
      <div class="ops"><button class="pxbtn" data-mv="-1" data-id="${esc(l.id)}" ${i === 0 ? 'disabled' : ''} aria-label="Move up">&#9650;</button><button class="pxbtn" data-mv="1" data-id="${esc(l.id)}" ${i === state.locations.length - 1 ? 'disabled' : ''} aria-label="Move down">&#9660;</button><button class="pxbtn warn" data-del="${esc(l.id)}" aria-label="Remove">&#10005;</button></div></div>`;
  }
  function openLocations() {
    sheet('SAVED PLACES', `
      <input class="field" id="q" type="search" placeholder="Search city or town…" autocomplete="off">
      <div class="list" id="results"></div>
      <div class="opt" style="margin-top:12px"><button class="pxbtn primary" id="gpsBtn">&#9678; USE MY LOCATION</button></div>
      <div class="sec">YOUR PLACES (${state.locations.length})</div>
      <div class="list" id="saved"></div>`, body => {
      const saved = $('#saved', body), results = $('#results', body), q = $('#q', body);
      const drawSaved = () => { saved.innerHTML = state.locations.map(locRow).join(''); };
      drawSaved();
      // fill in temps for places we have no cache for
      state.locations.forEach(l => { if (!readCache(l.id)) fetchAll(l).then(() => { if ($('#saved')) drawSaved(); }).catch(() => {}); });
      let timer, found = [];
      q.addEventListener('input', () => {
        clearTimeout(timer);
        const v = q.value.trim();
        if (v.length < 2) { results.innerHTML = ''; return; }
        timer = setTimeout(async () => {
          results.innerHTML = '<div class="lbl">Searching…</div>';
          try {
            found = await searchPlaces(v);
            if (q.value.trim() !== v) return;
            results.innerHTML = found.length ? found.map((r, k) => `<div class="li"><div class="main" data-addloc="${k}"><div class="n">+ ${esc(r.name)}</div><div class="a">${esc([r.admin, r.country].filter(Boolean).join(', '))}</div></div></div>`).join('') : '<div class="lbl">No places found.</div>';
          } catch (e) { results.innerHTML = '<div class="lbl">Search needs a connection.</div>'; }
        }, 300);
      });
      body.addEventListener('click', async e => {
        const t = e.target.closest('[data-addloc],[data-pick],[data-mv],[data-del],#gpsBtn');
        if (!t) return;
        if (t.id === 'gpsBtn') {
          t.textContent = 'LOCATING…';
          try { await useMyLocation(true); closeSheet(); refresh(); } catch (err) { toast(err.message); t.innerHTML = '&#9678; USE MY LOCATION'; }
          return;
        }
        if (t.dataset.addloc != null) {
          const r = found[+t.dataset.addloc];
          let l = state.locations.find(x => !x.gps && Math.abs(x.lat - r.lat) < 0.01 && Math.abs(x.lon - r.lon) < 0.01);
          if (!l) { l = { ...r, id: 'p' + Date.now().toString(36) }; state.locations.push(l); }
          state.activeId = l.id; save(); closeSheet(); refresh(); return;
        }
        if (t.dataset.pick) { state.activeId = t.dataset.pick; save(); closeSheet(); refresh(); return; }
        if (t.dataset.mv) {
          const i = state.locations.findIndex(x => x.id === t.dataset.id), j = i + +t.dataset.mv;
          if (j < 0 || j >= state.locations.length) return;
          [state.locations[i], state.locations[j]] = [state.locations[j], state.locations[i]];
          save(); drawSaved(); renderHeader(current); return;
        }
        if (t.dataset.del) {
          if (state.locations.length === 1) { toast('Keep at least one place.'); return; }
          state.locations = state.locations.filter(x => x.id !== t.dataset.del);
          try { localStorage.removeItem(CACHE_KEY(t.dataset.del)); } catch (err) { /* */ }
          delete mem[t.dataset.del];
          if (state.activeId === t.dataset.del) { state.activeId = state.locations[0].id; save(); refresh(); }
          save(); drawSaved(); $('.sec', body).textContent = `YOUR PLACES (${state.locations.length})`;
        }
      });
      setTimeout(() => q.focus(), 50);
    });
  }

  function openSettings() {
    const opt = (key, val, label) => `<button class="pxbtn${U[key] === val ? ' on' : ''}" data-u="${key}" data-v="${val}">${label}</button>`;
    const th = (k, label, unit) => `<label class="lbl" style="display:flex;align-items:center;gap:8px;margin-top:8px"><span class="grow">${label}</span><input class="field" style="width:96px;padding:8px" type="number" data-th="${k}" value="${state.thresholds[k] ?? ''}" placeholder="off"><span style="width:36px">${unit}</span></label>`;
    sheet('SETTINGS', `
      <div class="sec">TEMPERATURE</div><div class="opt">${opt('temp', 'C', '°C')}${opt('temp', 'F', '°F')}</div>
      <div class="sec">WIND</div><div class="opt">${opt('wind', 'kmh', 'km/h')}${opt('wind', 'mph', 'mph')}${opt('wind', 'ms', 'm/s')}${opt('wind', 'kn', 'knots')}</div>
      <div class="sec">PRECIPITATION</div><div class="opt">${opt('precip', 'mm', 'mm')}${opt('precip', 'in', 'in')}</div>
      <div class="sec">PRESSURE</div><div class="opt">${opt('pressure', 'hPa', 'hPa')}${opt('pressure', 'kPa', 'kPa')}${opt('pressure', 'inHg', 'inHg')}</div>
      <div class="sec">DISTANCE</div><div class="opt">${opt('dist', 'km', 'km')}${opt('dist', 'mi', 'mi')}</div>
      <div class="sec">CLOCK</div><div class="opt">${opt('clock', '24', '24 h')}${opt('clock', '12', '12 h')}</div>
      <div class="sec">AIR QUALITY SCALE</div><div class="opt"><button class="pxbtn${state.aqScale === 'aqhi' ? ' on' : ''}" data-aq="aqhi">AQHI (Canada)</button><button class="pxbtn${state.aqScale === 'us' ? ' on' : ''}" data-aq="us">US AQI</button></div>
      <div class="sec">CUSTOM ALERTS (NEXT 48 H, METRIC)</div>
      ${th('cold', 'Temp drops to', '°C')}${th('hot', 'Temp reaches', '°C')}${th('gust', 'Wind gusts over', 'km/h')}${th('snow', 'Snowfall over', 'cm')}
      <p class="note">Leave a box empty to turn that alert off.</p>
      <div class="sec">LOCATION</div>
      <div class="opt"><button class="pxbtn${state.gpsAuto ? ' on' : ''}" data-gps>&#9678; Update my location on open: ${state.gpsAuto ? 'ON' : 'OFF'}</button></div>
      <div class="sec">ABOUT</div>
      <p class="note">Forecast &amp; air quality: <a href="https://open-meteo.com" target="_blank" rel="noopener">Open-Meteo</a>. Alerts: Environment and Climate Change Canada, US National Weather Service. Places: Photon / OpenStreetMap. Radar: RainViewer. Location is only used on this device to fetch your forecast.</p>
      <div class="opt" style="margin-top:12px"><button class="pxbtn warn" data-wipe>CLEAR CACHED WEATHER</button></div>`, body => {
      body.addEventListener('click', e => {
        const t = e.target.closest('[data-u],[data-aq],[data-gps],[data-wipe]');
        if (!t) return;
        if (t.dataset.u) { U[t.dataset.u] = t.dataset.v; t.parentElement.querySelectorAll('.pxbtn').forEach(b => b.classList.toggle('on', b === t)); }
        if (t.dataset.aq) { state.aqScale = t.dataset.aq; t.parentElement.querySelectorAll('.pxbtn').forEach(b => b.classList.toggle('on', b === t)); }
        if (t.dataset.gps != null) { state.gpsAuto = !state.gpsAuto; t.classList.toggle('on', state.gpsAuto); t.innerHTML = `&#9678; Update my location on open: ${state.gpsAuto ? 'ON' : 'OFF'}`; }
        if (t.dataset.wipe != null) { state.locations.forEach(l => { delete mem[l.id]; try { localStorage.removeItem(CACHE_KEY(l.id)); } catch (err) { /* */ } }); toast('Cache cleared'); refresh(true); }
        save(); rerender();
      });
      body.addEventListener('change', e => {
        const t = e.target.closest('[data-th]');
        if (!t) return;
        state.thresholds[t.dataset.th] = t.value === '' ? null : +t.value;
        save(); rerender();
      });
    });
  }

  // ---------------------------------------------------------------- swipe between saved places
  function bindSwipe(el) {
    let x0 = null, y0 = null;
    el.addEventListener('touchstart', e => { x0 = e.touches[0].clientX; y0 = e.touches[0].clientY; }, { passive: true });
    el.addEventListener('touchend', e => {
      if (x0 == null) return;
      const dx = e.changedTouches[0].clientX - x0, dy = e.changedTouches[0].clientY - y0;
      x0 = null;
      if (Math.abs(dx) < 50 || Math.abs(dy) > Math.abs(dx)) return;
      step(dx < 0 ? 1 : -1);
    });
  }
  function step(dir) {
    const n = state.locations.length;
    if (n < 2) return;
    const i = state.locations.findIndex(l => l.id === state.activeId);
    state.activeId = state.locations[(i + dir + n) % n].id;
    save(); refresh();
  }
  document.addEventListener('keydown', e => {
    if ($('#sheet') || e.target.matches('input')) return;
    if (e.key === 'ArrowRight') step(1);
    if (e.key === 'ArrowLeft') step(-1);
  });

  // ---------------------------------------------------------------- refresh loop
  let reqId = 0;
  function rerender() { if (current) current = ctxFor(current.loc, current.data); renderHeader(current); renderWidgets(); }
  async function refresh(force) {
    const l = activeLoc(), my = ++reqId;
    const cached = readCache(l.id);
    current = cached ? ctxFor(l, cached) : null;
    renderHeader(current); renderWidgets();
    if (!force && cached && Date.now() - cached.t < FRESH_MS) return;
    $('#bRef').classList.add('loading');
    try {
      const data = await fetchAll(l, force);
      if (my !== reqId) return;
      current = ctxFor(l, data);
      renderHeader(current); renderWidgets();
      // warm the next saved places in the background so swiping is instant
      state.locations.filter(x => x.id !== l.id).forEach(x => fetchAll(x).catch(() => {}));
    } catch (e) {
      if (my !== reqId) return;
      renderHeader(current, current ? null : 'Could not load weather — check your connection');
      if (current) toast('Offline — showing saved forecast');
    } finally { $('#bRef').classList.remove('loading'); }
  }
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
  setInterval(() => { if (!document.hidden) refresh(); }, 5 * 60 * 1000);

  // ---------------------------------------------------------------- boot
  renderShell();
  refresh();
  (async () => {
    const hasGps = state.locations.some(l => l.gps);
    let granted = false;
    try { granted = (await navigator.permissions.query({ name: 'geolocation' })).state === 'granted'; } catch (e) { /* unsupported */ }
    if (state.firstRun || (hasGps && state.gpsAuto && granted)) {
      try {
        const before = activeLoc().id;
        await useMyLocation(state.firstRun || before === 'gps');
        // first launch: the GPS place replaces the starter location
        if (state.firstRun) state.locations = state.locations.filter(l => l.id !== DEFAULT_LOC.id);
        refresh();
      } catch (e) { if (state.firstRun) toast('Tip: tap ☰ to add places or use your location'); }
    }
    save();
  })();
  if ('serviceWorker' in navigator && location.protocol === 'https:') navigator.serviceWorker.register('sw.js').catch(() => {});
})();
