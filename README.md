# Bitweather

A widget-first pixel weather app in the **128bit family** — the forecast, but chunkier.

Landing page is live via GitHub Pages: https://hootywhooo86.github.io/bitweather/

## What this is

Bitweather is Daniel's 128bit-family take on weather: current conditions, hourly/daily forecast, and severe-weather alerts with a chunky pixel aesthetic — designed **widget-first**, like Railway Time and Bitcalendar. The home-screen widget is the product: glanceable temp, condition icon, today's high/low.

## Product brief (starter — Daniel's call on the concept)

**Core idea:** a pixel weather experience where the widget is the hero. Full app behind it for radar, hourly breakdowns, and alerts.

**Widget (the main event):**
- Small/medium/large widget sizes — temp + pixel condition icon, high/low, precipitation chance.
- Severe weather alerts with pixel styling (Alberta winter storms deserve chunky snowflakes).
- Tap through to the full app.

**Full app:**
- Hourly + 10-day forecast in pixel style.
- Pixel radar map.
- Custom alerts (e.g. "warn me when it drops below -20").
- Feeds the 128bit event feed — e.g. `weather.alert` events that 128bitlife quests can react to ("blizzard quest: stay inside and log a workout").

**Open product questions for Daniel:**
1. iOS / Android / both? (Widget APIs differ — iOS WidgetKit vs Android widgets.)
2. Which weather data provider? (Open-Meteo is free with no API key — strong default; Environment Canada for Canadian accuracy.)
3. Any signature features — pixel radar, quest-linked weather, railroad-specific conditions (Daniel's a railroader — wind/temp at terminals?)?

## Architecture (128bit family standard)

- **One 128bit account** across all apps from day one.
- **Shared event schema** — Bitweather emits `weather.alert`, `forecast.viewed` into the family feed.
- **Family event feed** — `supabase/family-feed.sql` in this repo (same block as 128bitPlay's setup SQL — keep them in step). Run once in the Supabase SQL Editor.
- **Monetization:** 128bit Pro bundle subscription across all apps (default assumption).

## For Carlos

- Landing page: `index.html` (live weather widget mock — tap forecast days for details). Needs a Formspree ID in the waitlist form before launch.
- The widget is the product — start with the widget, then the full app behind it.
- This README is the starter brief — build the real thing per Daniel's product call above.
- Rocky codes scaffolding and straightforward builds; Carlos takes the harder features and double-checks Rocky's work.

## Status

- [x] Repo created, landing page live
- [ ] Formspree ID in `index.html`
- [ ] Product direction locked (Daniel)
- [ ] Weather data provider chosen
- [ ] Widget build (per platform call)
- [ ] Full app behind the widget
