// Bitweather pixel icons — procedurally drawn on a 16x16 grid, outlined, rendered as crisp SVG.
(function () {
  const PAL = {
    ol: '#0d0d1a', sun: '#ffc107', sunHi: '#ffe082', sunLo: '#ff9f1c',
    moon: '#e8e3ff', moonLo: '#b388ff', cloud: '#eceaf6', cloudLo: '#a7a7c2',
    dark: '#8a8aa8', darkLo: '#5c5c7a', rain: '#4fc3f7', snow: '#ffffff',
    bolt: '#ffeb3b', fog: '#b0b0c8', ice: '#80deea',
  };

  const disc = (cx, cy, r) => {
    const px = [];
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const dx = x + 0.5 - cx, dy = y + 0.5 - cy;
      if (dx * dx + dy * dy <= r * r) px.push([x, y]);
    }
    return px;
  };
  const union = (...lists) => {
    const m = new Map();
    lists.flat().forEach(([x, y]) => { if (x >= 0 && y >= 0 && x < 16 && y < 16) m.set(x + ',' + y, [x, y]); });
    return [...m.values()];
  };
  const minus = (a, b) => { const s = new Set(b.map(p => p + '')); return a.filter(p => !s.has(p + '')); };

  function sunLayers(cx, cy, r, ray) {
    const body = disc(cx, cy, r);
    const rays = [];
    for (let k = 0; k < 8; k++) {
      const a = k * Math.PI / 4;
      for (let d = r + 1.6; d < r + 1.6 + ray; d += 0.9) {
        rays.push([Math.floor(cx + Math.cos(a) * d), Math.floor(cy + Math.sin(a) * d)]);
      }
    }
    return [
      { px: union(rays), fill: () => PAL.sunLo },
      { px: body, fill: (x, y) => (x + 0.5 < cx - r * 0.2 && y + 0.5 < cy - r * 0.2 ? PAL.sunHi : (y + 0.5 > cy + r * 0.45 ? PAL.sunLo : PAL.sun)) },
    ];
  }
  function moonLayer(cx, cy, r) {
    const px = minus(disc(cx, cy, r), disc(cx + r * 0.65, cy - r * 0.45, r * 0.85));
    return { px, fill: (x) => (x + 0.5 < cx - r * 0.45 ? PAL.moon : PAL.moonLo) };
  }
  function cloudLayer(ox, oy, s, dark) {
    const S = v => v * s;
    const bottom = oy + S(12);
    const px = union(
      disc(ox + S(5), oy + S(9.5), S(3)),
      disc(ox + S(9.5), oy + S(7.5), S(4)),
      disc(ox + S(12.6), oy + S(10), S(2.6)),
      disc(ox + S(4.5), oy + S(11), S(1.8)),
    ).filter(([, y]) => y + 0.5 <= bottom);
    const hi = dark ? PAL.dark : PAL.cloud, lo = dark ? PAL.darkLo : PAL.cloudLo;
    return { px, fill: (x, y) => (y + 0.5 > bottom - 1.5 ? lo : hi) };
  }
  const pts = (list, color) => ({ px: list, fill: () => color });

  const RAIN = [[4, 11], [3, 12], [8, 11], [7, 12], [12, 11], [11, 12], [6, 14], [5, 15], [10, 14], [9, 15]];
  const DRIZZLE = [[4, 11], [8, 12], [12, 11], [6, 14], [10, 14]];
  const flake = (x, y) => [[x, y], [x - 1, y], [x + 1, y], [x, y - 1], [x, y + 1]];
  const SNOW = union(flake(4, 12), flake(9, 13), flake(13, 11));
  const BOLT = [[8, 9], [9, 9], [7, 10], [8, 10], [6, 11], [7, 11], [8, 11], [9, 11], [8, 12], [7, 13], [6, 14]];
  const FOG = [];
  [[10, 1, 14], [12, 3, 12], [14, 2, 15]].forEach(([y, a, b]) => { for (let x = a; x <= b; x++) if ((x + y) % 5) FOG.push([x, y]); });

  function layersFor(kind, day) {
    const sky = (cx, cy, r, ray) => (day ? sunLayers(cx, cy, r, ray) : [moonLayer(cx, cy, r + 0.6)]);
    const up = -3; // cloud lifted for precipitation icons
    switch (kind) {
      case 'clear': return sky(8, 8, 4.4, 2.2);
      case 'mostly': return [...sky(7, 7, 3.8, 1.8), cloudLayer(5, 6.5, 0.72)];
      case 'partly': return [...sky(6, 6, 3.4, 1.6), cloudLayer(1.5, 3, 1)];
      case 'cloudy': return [cloudLayer(-1.5, -2.5, 0.85, true), cloudLayer(1, 2, 1)];
      case 'fog': return [cloudLayer(0, -4, 1), pts(FOG, PAL.fog)];
      case 'drizzle': return [cloudLayer(0, up, 1), pts(DRIZZLE, PAL.rain)];
      case 'rain': return [cloudLayer(0, up, 1, true), pts(RAIN, PAL.rain)];
      case 'freezing': return [cloudLayer(0, up, 1, true), pts(DRIZZLE, PAL.ice), pts([[12, 13], [11, 14], [12, 15], [13, 14]], PAL.snow)];
      case 'showers': return [...sky(4.5, 4.5, 3, 1.4), cloudLayer(1, -1.5, 0.9), pts(RAIN.filter(([x]) => x > 4), PAL.rain)];
      case 'snow': return [cloudLayer(0, up, 1), pts(SNOW, PAL.snow)];
      case 'sleet': return [cloudLayer(0, up, 1, true), pts(union(flake(4, 12), flake(12, 13)), PAL.snow), pts([[8, 11], [7, 12], [9, 14], [8, 15]], PAL.rain)];
      case 'thunder': return [cloudLayer(0, up, 1, true), pts([[3, 11], [2, 12], [12, 11], [11, 12], [13, 14], [12, 15]], PAL.rain), pts(BOLT, PAL.bolt)];
      default: return sky(8, 8, 4.4, 2.2);
    }
  }

  function paint(layers) {
    const g = new Map();
    for (const L of layers) {
      const set = new Set(L.px.map(([x, y]) => x + ',' + y));
      for (const [x, y] of L.px) {
        for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
          const nx = x + dx, ny = y + dy, k = nx + ',' + ny;
          if (nx < 0 || ny < 0 || nx > 15 || ny > 15 || set.has(k)) continue;
          g.set(k, PAL.ol);
        }
      }
      for (const [x, y] of L.px) g.set(x + ',' + y, L.fill(x, y));
    }
    return g;
  }

  function toSvg(g) {
    let rects = '';
    for (let y = 0; y < 16; y++) {
      let x = 0;
      while (x < 16) {
        const c = g.get(x + ',' + y);
        if (!c) { x++; continue; }
        let w = 1;
        while (x + w < 16 && g.get((x + w) + ',' + y) === c) w++;
        rects += `<rect x="${x}" y="${y}" width="${w}" height="1" fill="${c}"/>`;
        x += w;
      }
    }
    return `<svg class="px-icon" viewBox="0 0 16 16" shape-rendering="crispEdges" aria-hidden="true">${rects}</svg>`;
  }

  const cache = new Map();
  function icon(kind, day = true) {
    const key = kind + (day ? 'd' : 'n');
    if (!cache.has(key)) cache.set(key, toSvg(paint(layersFor(kind, day))));
    return cache.get(key);
  }

  // Moon phase icon: phase 0..1 (0 new, 0.5 full)
  function moonPhaseIcon(phase) {
    const px = disc(8, 8, 6);
    const g = new Map();
    const lit = (x) => {
      const nx = (x + 0.5 - 8) / 6; // -1..1
      const k = Math.cos(phase * 2 * Math.PI); // 1 new, -1 full
      if (phase < 0.5) return nx > k; // waxing: right side lit
      return nx < -k;
    };
    for (const [x, y] of px) {
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const k = (x + dx) + ',' + (y + dy);
        if (!px.some(([a, b]) => a === x + dx && b === y + dy)) g.set(k, PAL.ol);
      }
    }
    for (const [x, y] of px) g.set(x + ',' + y, lit(x) ? ((x + y) % 7 === 0 ? PAL.moonLo : PAL.moon) : '#2a2a44');
    return toSvg(g);
  }

  window.PixelIcons = { icon, moonPhaseIcon, PAL };
})();
