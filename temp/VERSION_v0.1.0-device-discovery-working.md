# Version Snapshot - v0.1.0-device-discovery-working

## Snapshot Date
2026-01-17

## Description
Working DLNA casting implementation with device discovery working. HLS support still under debugging.

## Features Implemented
- DLNA MediaRenderer with Cling 2.0.1 UPnP library
- ExoPlayer (Media3 1.2.1) for media playback
- SSDP discovery with MulticastSocket
- NSD service registration (_cast._tcp.)
- HTTP server (port 5000) for device description
- **Device discovery working** - Mobile apps can find CasterTV device

## Features NOT Yet Working (Debugging In Progress)
- HLS () streaming support -m3u8 URL detection not working for BaiduNet URLs

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
│   ├── CommonUtil.java
│   ├── DevMountInfo.java
│   ├── FileHelper.java
│   ├── FileUtil.java
│   ├── FixedAndroidHandler.java
│   ├── IntentOpenFile.java
│   ├── ShakeListener.java
│   ├── ThreadUtils.java
│   ├── UpnpUtil.java
│   └── Utils.java
├── com/zxt/dlna/control/       # Control point (if exists)
└── org/fourthline/cling/       # UPnP library (from TVRemoteIME)
```

## Current Status
- APK builds successfully (11.2MB)
- Location: `app/build/outputs/apk/debug/app-debug.apk`
- HTTP service responding at port 5000
- NSD service registered
- SSDP multicast broadcasts working
- **Device discovery confirmed working** - Mobile DLNA apps can find CasterTV
- HLS streaming detection **NOT working** - URLs without .m3u8 extension not recognized

## Known Issues
- HLS streaming not working - BaiduNet URLs (with type=M3U8 parameter) not detected as HLS streams
- Need to add URL pattern matching for HLS detection

## How to Restore This Version
1. **From tar.gz**: `tar -xzf castertv-v0.1.0-device-discovery-working.tar.gz`
2. **From APK**: Reinstall from `app/build/outputs/apk/debug/app-debug.apk`
3. **From git**: `git checkout <commit-hash>` (if using git)

## Build Command
```bash
./gradlew assembleDebug
```

## Dependencies
- Kotlin 1.9.x
- Android Gradle Plugin 8.x
- Media3 ExoPlayer 1.2.1
- Cling 2.0.1 (from 4thline.org)
- Timber 5.0.1
- OkHttp 4.12.0

## Debug Notes
For HLS debugging, check:
- `ZxtMediaPlayer.java` - Media source detection logic
- URL patterns containing `type=M3U8` should trigger HlsMediaSource
- Currently ExoPlayer may fail to play these URLs
