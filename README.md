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

## Android app (`android/`) — the real app

Native Kotlin app (Jetpack Compose) with **real home-screen widgets**. Install the APK from **[Releases](https://github.com/Hootywhooo86/bitweather/releases/latest)**. Every push that changes `android/` builds a new signed APK there.

- **Home-screen widgets** (long-press home screen → Widgets → Bitweather), all resizable. Each one redraws pixel-perfect for its size:
  - **Now:** temperature, conditions and alerts. Make it bigger to add an hourly strip, then the 10-day forecast.
  - **Hourly:** a forecast strip. Make it taller to add a temperature chart.
  - **10-day:** pills when it's short, range-bar rows when it's tall.
  - **Details:** a grid of wind, humidity, UV, air quality, sun, pressure, precipitation and visibility tiles that fills whatever size you give it.
- **Current location:** the app opens on where you are. Location and weather refresh every 15 minutes in the background (WorkManager), so widgets follow you. This needs "Allow all the time" location access.
- **In the app:** every card from the screenshots in 4 sizes (✎ to resize, reorder, remove or add), unlimited saved places with search (swipe to switch), a day detail view, alerts, an animated radar, units, and custom alert thresholds.
- **Notifications:** official alerts (Environment Canada / US NWS) plus custom alerts (each sent once), a pop-up forecast every 6 hours (set by an alarm), and **app updates** (it checks GitHub Releases and taps through to the new APK).
- **Build locally:** `cd android && ./gradlew assembleRelease` (needs the Android SDK). `./gradlew testDebugUnitTest` renders widget and screen previews into `android/app/build/previews/`.
- **Signing:** `android/keystore/bitweather.jks` is a fixed key, committed so CI builds always update the installed app in place. Its password is in `app/build.gradle.kts`. Fine for a sideloaded personal app. Move it to GitHub secrets before any Play Store release.

## Web app (`app/`)

Live at https://hootywhooo86.github.io/bitweather/app/ — an installable web app (PWA) in the landing page's pixel style.

- **Widgets in 4 sizes** (thin, small, wide, large). Tap ✎ to resize, reorder, remove, or add widgets. The layout is saved on the device.
- **Widgets:** alerts, weather brief, weather insight, hourly forecast with a temperature chart, 10-day forecast, precipitation, wind compass and gusts, sunrise and sunset, UV index, air quality (AQHI or US AQI with pollutant breakdown), visibility, humidity and dew point, pressure with its trend, feels like, moon phase, and an animated radar.
- **Current location first:** the app opens on where you are, and your location and weather refresh every 30 minutes.
- **Notifications:** official alerts plus your custom alerts, each sent once, and an optional conditions update every 30 minutes. You also get a notice when the app updates. Turn them on in ⚙ → Notifications. Background checks need the app installed on Android.
- **Locations:** GPS "use my location", place search, and unlimited saved places. Reorder or remove them in ☰, and swipe the top card to switch places.
- **Settings:** °C/°F, km/h, mph, m/s, knots, mm/in, hPa/kPa/inHg, km/mi, 24h/12h, and custom alerts (e.g. a warning when it drops below -20°).
- **Offline:** each place's last forecast is cached, and the app shell works offline.
- **Data (all free, no API keys):** Open-Meteo for the forecast and air quality, Environment Canada and US NWS for official alerts, Photon/OSM and Open-Meteo for place search, and RainViewer for radar.

Real home-screen widgets need the native Android/iOS build. These web widgets are the design reference for that build.

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
- [x] Weather data provider chosen (Open-Meteo + ECCC/NWS alerts)
- [x] Web app with widgets + saved locations (`app/`)
- [x] Native Android app + home-screen widgets (`android/`, APK on Releases)
- [ ] iOS
- [ ] Widget build (per platform call)
- [ ] Full app behind the widget
