# Version Snapshot - v0.1.0-hls-ready

## Snapshot Date
2026-01-17

## Description
Initial DLNA casting implementation with HLS streaming support ready for rollback.

## Features Implemented
- DLNA MediaRenderer with Cling 2.0.1 UPnP library
- ExoPlayer (Media3 1.2.1) for media playback
- HLS (m3u8) streaming support with type=M3U8 detection
- SSDP discovery with MulticastSocket
- NSD service registration (_cast._tcp.)
- HTTP server (port 5000) for device description

## Project Structure
```
app/src/main/java/
├── com/caster/tv/              # Application code
│   ├── CasterApplication.kt
│   ├── service/CastCoordinatorService.kt
│   ├── ui/MainActivity.kt
│   ├── dlna/DLNAUtils.java
│   └── receiver/BootReceiver.kt
├── com/zxt/dlna/dmr/           # DLNA renderer services
│   ├── ZxtMediaRenderer.java
│   ├── ZxtMediaPlayer.java
│   ├── AVTransportService.java
│   ├── AudioRenderingControl.java
│   ├── ZxtConnectionManagerService.java
│   └── RenderService.java
├── com/zxt/dlna/util/          # Utility classes
└── org/fourthline/cling/       # UPnP library (from TVRemoteIME)
```

## Current Status
- APK builds successfully (11.2MB)
- Location: `app/build/outputs/apk/debug/app-debug.apk`
- HTTP service responding at port 5000
- NSD service registered
- SSDP multicast broadcasts working
- BaiduNet HLS streaming supported (type=M3U8 detection)

## Known Issues
- Phone DLNA discovery not working (SSDP network routing)
- Needs further debugging for mobile app discovery

## How to Rollback to This Version
1. If using git: `git checkout v0.1.0-hls-ready` or `git reset --hard HEAD~1`
2. If using APK: Reinstall from `app/build/outputs/apk/debug/app-debug.apk`

## Dependencies
- Kotlin 1.9.x
- Android Gradle Plugin 8.x
- Media3 ExoPlayer 1.2.1
- Cling 2.0.1 (from 4thline.org)
- Timber 5.0.1
- OkHttp 4.12.0
