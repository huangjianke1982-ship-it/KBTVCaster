# CasterTV Cling Integration Summary

## Overview

This document summarizes the Cling UPnP library integration that was added to CasterTV to improve device discovery and compatibility with casting apps.

## Problem Being Addressed

The original implementation used native Android APIs (NsdManager, MulticastSocket) for SSDP discovery, but encountered the issue where **Android TV systems bind UDP port 1900** (SSDP), preventing third-party apps from receiving M-SEARCH broadcast requests.

## Solution: Cling Library Integration

Based on the successful TVRemoteIME project, we integrated the Cling UPnP library which properly handles Android's network constraints.

## Files Created/Modified

### New Files

1. **`app/src/main/java/com/caster/tv/dlna/upnp/AndroidUpnpServiceImpl.kt`**
   - Main UPnP service implementation
   - Manages UPnP stack with proper WiFi MulticastLock handling
   - Registers network change callbacks for automatic discovery refresh
   - Provides methods to add local devices and execute actions

2. **`app/src/main/java/com/caster/tv/dlna/upnp/router/AndroidRouter.kt`**
   - Android-specific router implementation
   - Properly manages WiFi MulticastLock (enabled/disabled based on WiFi status)
   - Manages WiFi Lock for high-performance streaming
   - Handles network connectivity changes

3. **`app/src/main/java/com/caster/tv/dlna/upnp/AndroidUpnpServiceConfiguration.kt`**
   - Android-specific UPnP service configuration
   - Extends DefaultUpnpServiceConfiguration with Android settings

4. **`app/src/main/java/com/caster/tv/dlna/upnp/router/DefaultRouterConfiguration.kt`**
   - Default router configuration for Android UPnP
   - Sets appropriate TTL (4) and multicast settings

5. **`app/src/main/java/com/caster/tv/dlna/upnp/ZxtMediaRenderer.kt`**
   - Complete DLNA Media Renderer implementation
   - Implements AVTransport service (playback control)
   - Implements RenderingControl service (volume, mute)
   - Implements ConnectionManager service
   - Based on TVRemoteIME's ZxtMediaRenderer

### Modified Files

1. **`app/build.gradle.kts`**
   - Added `implementation(fileTree("libs") { include("*.jar") })` for local JAR dependencies

2. **`app/src/main/AndroidManifest.xml`**
   - Added `AndroidUpnpServiceImpl` service declaration
   - Already had required permissions (INTERNET, CHANGE_WIFI_MULTICAST_STATE, etc.)

3. **`app/src/main/java/com/caster/tv/service/CastCoordinatorService.kt`**
   - Added Cling service initialization (`initializeClingService()`)
   - Added Media Renderer registration
   - Added proper cleanup in `stop()` method

### Documentation

1. **`app/libs/README.md`**
   - Instructions for adding Cling JAR files

## Key Technical Details

### WiFi MulticastLock Management

The Cling integration properly manages WiFi MulticastLock:

```kotlin
// From AndroidRouter.kt
if (isWifi()) {
    setWiFiMulticastLock(true)  // Acquire lock when WiFi is active
    setWifiLock(true)           // Acquire WiFi lock for performance
}

// When disabling
setWiFiMulticastLock(false)     // Release lock
setWifiLock(false)              // Release WiFi lock
```

### Media Renderer Services

The ZxtMediaRenderer implements three UPnP services:

1. **AVTransport** (urn:schemas-upnp-org:service:AVTransport:1)
   - SetAVTransportURI, Play, Pause, Stop, Seek
   - GetTransportInfo, GetPositionInfo

2. **RenderingControl** (urn:schemas-upnp-org:service:RenderingControl:1)
   - GetVolume, SetVolume, GetMute, SetMute
   - Volume range: 0-100

3. **ConnectionManager** (urn:schemas-upnp-org:service:ConnectionManager:1)
   - GetProtocolInfo, GetCurrentConnectionIDs, GetConnectionInfo

## Setup Instructions

### Step 1: Add Cling JAR Files

1. Extract TVRemoteIME-master.zip (from `C:\Users\kaiji\Desktop\`)
2. Navigate to `TVRemoteIME-master/DroidDLNA/libs/`
3. Copy all `.jar` files to `CasterTVService/app/libs/`
4. Required JARs:
   - cling-core.jar (or main cling JAR)
   - http-*.jar (HTTP client libraries)
   - jetty-*.jar (Jetty server)
   - javax.servlet-*.jar (Servlet API)
   - cdi-api.jar, javax.inject.jar

### Step 2: Build and Deploy

```bash
cd CasterTVService
./gradlew assembleDebug
adb connect <TV_IP>:5555
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Test

1. Install and run CasterTV on Android TV
2. Check logs for Cling initialization:
   ```bash
   adb logcat -d | grep -E "Cling|AndroidRouter|MediaRenderer"
   ```
3. Test with casting apps (B站, YouTube, Netflix)

## Expected Behavior

### With Cling Integration

1. **Better Device Discovery**: Cling handles SSDP properly with MulticastLock
2. **Full DLNA Compliance**: Proper UPnP service implementations
3. **Network Change Handling**: Automatic discovery refresh on network changes
4. **WiFi Lock Management**: Proper performance optimization

### Log Output Expected

When Cling is working correctly, you should see:
```
AndroidRouter initialized with MulticastLock
UPnP service initialized
Media Renderer device registered: CasterTV-XXXX
Local device added: CasterTV Media Renderer
```

## Troubleshooting

### If Cling JARs are missing:
- Build will fail with ClassNotFoundException
- Check `app/libs/` directory contains all required JARs

### If devices not discovered:
- Check WiFi connection
- Check MulticastLock is acquired (logs)
- Verify port 1900 is not blocked by other services

### If Media Renderer not working:
- Check ExoPlayer initialization
- Verify ZxtMediaRenderer.createDevice() returns valid device
- Check service binding (onServiceConnected logs)

## Next Steps

1. **Add Cling JAR files** to `app/libs/` directory
2. **Build and test** the application
3. **Verify discovery** with casting apps
4. **Monitor logs** for any issues

## References

- **TVRemoteIME**: https://gitee.com/kingthy/TVRemoteIME.git
- **DroidDLNA**: https://github.com/offbye/DroidDLNA
- **Cling Documentation**: https://github.com/4thline/cling
- **UPnP Spec**: http://www.upnp.org/
