# CasterTV Testing Guide

This guide provides comprehensive testing procedures for CasterTV's discovery and casting functionality.

## Prerequisites

### Required Files
Before testing, ensure you have:
1. **Cling JAR files** in `app/libs/` (from TVRemoteIME/DroidDLNA/libs/)
2. **Android TV device** connected via ADB (IP: 192.168.0.49:5555)
3. **Testing device** (phone) with casting apps installed

### ADB Connection
```bash
# Connect to Android TV
adb connect 192.168.0.49:5555

# Verify connection
adb devices

# Expected output:
# List of devices attached
# 192.168.0.49:5555      device
```

---

## Test 1: Build Verification

### Step 1: Add Cling JAR Files
```bash
# Navigate to project
cd CasterTVService

# Check libs directory
ls app/libs/

# Expected: Multiple .jar files (cling-core, http-*.jar, jetty-*.jar, etc.)
```

### Step 2: Build the APK
```bash
# On Windows with Java 17
set JAVA_HOME=C:\Program Files\Java\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%

# Build
./gradlew assembleDebug

# Expected: BUILD SUCCESSFUL
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Install on TV
```bash
# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Expected: Success
```

---

## Test 2: HTTP Server Verification

### Step 1: Start the Service
```bash
# Start the app (from TV)
adb shell am start -n com.caster.tv/.ui.MainActivity

# Or via the app UI:
# 1. Open CasterTV app on TV
# 2. Click "启动投屏服务" (Start Casting Service)
```

### Step 2: Verify HTTP Server
```bash
# Check if TV IP is accessible
adb shell ip addr show wlan0 | grep inet

# Expected: 192.168.0.49 (or similar)

# Forward port to localhost
adb forward tcp:5001 tcp:5000

# Test HTTP endpoints
curl http://localhost:5001/
curl http://localhost:5001/cast.json
curl http://localhost:5001/setup/eureka_info
curl http://localhost:5001/ssdp/device_info.xml
```

### Expected Responses

#### `/` (Device Description)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
    <device>
        <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
        <friendlyName>CasterTV-XXXX</friendlyName>
        ...
    </device>
</root>
```

#### `/cast.json` (Discovery JSON)
```json
{
    "devices": [
        {
            "name": "CasterTV-XXXX",
            "type": "urn:schemas-upnp-org:device:MediaRenderer:1",
            "uuid": "CasterTV-XXXX",
            ...
        }
    ]
}
```

#### `/setup/eureka_info` (Google Cast Info)
```json
{
    "name": "CasterTV-XXXX",
    "id": "XXXX",
    "model_name": "CasterTV",
    ...
}
```

---

## Test 3: SSDP Discovery Verification

### Step 1: Check SSDP NOTIFY Messages
```bash
# Monitor SSDP traffic (on a separate terminal)
tcpdump -i wlan0 -v port 1900

# Or use adb logcat to see SSDP logs
adb logcat -d | grep -E "SSDP|ssdp"
```

### Step 2: Check SSDP Response
```bash
# Send M-SEARCH from PC (requires SSDP tool)
# Or check logs for M-SEARCH detection
adb logcat -d | grep -E "M-SEARCH"
```

### Expected Log Output
```
SSDP NOTIFY sent to 239.255.255.250:1900
SSDP response sent to 192.168.x.x
```

---

## Test 4: NSD/mDNS Discovery Verification

### Step 1: Check NSD Registration
```bash
# Check NSD service logs
adb logcat -d | grep -E "NSD|nsd|GoogleCast"

# Expected:
# HTTP NSD service registered: CasterTV-XXXX-http on port 5000
# Google Cast NSD service registered: CasterTV-XXXX-googlecast on port 5000
```

### Step 2: Discover NSD Services (from another device)
```bash
# Use a network scanner app on phone
# Look for services:
# - _http._tcp. -> CasterTV-XXXX-http
# - _googlecast._tcp. -> CasterTV-XXXX-googlecast
# - _cast._tcp. -> CasterTV-XXXX-cast
```

---

## Test 5: Cling UPnP Service Verification

### Step 1: Check Cling Initialization
```bash
# Check logs for Cling initialization
adb logcat -d | grep -E "Cling|AndroidRouter|ZxtMediaRenderer|UPnP"

# Expected output:
# AndroidRouter initialized with MulticastLock
# UPnP service initialized
# Media Renderer device registered: CasterTV-XXXX
# Local device added: CasterTV Media Renderer
```

### Step 2: Verify Service Binding
```bash
# Check running services
adb shell dumpsys activity services | grep -i caster

# Expected:
# ServiceRecord{... com.caster.tv/.service.CastCoordinatorService}
# ServiceRecord{... com.caster.tv/.dlna.upnp.AndroidUpnpServiceImpl}
```

---

## Test 6: Casting App Testing

### Test Apps to Use
1. **B站 (Bilibili)** - Most common Chinese casting app
2. **YouTube** - Google Cast compatible
3. **腾讯视频 (Tencent Video)** - DLNA compatible
4. **优酷 (Youku)** - DLNA compatible
5. **Netflix** - DIAL/Chromecast compatible

### Test Procedure

#### Step 1: Prepare Testing Device
1. Ensure phone and TV are on the **same WiFi network**
2. Install and open casting app on phone
3. Play a video in the app

#### Step 2: Initiate Casting
1. Tap the **cast/投屏** button in the app
2. Look for device named **"CasterTV-XXXX"**
3. Select the device

#### Step 3: Verify Discovery
**If device appears in casting list:**
- ✅ Discovery working! Proceed to verify playback

**If device does NOT appear:**
1. Check if HTTP server is responding (Test 2)
2. Check NSD services (Test 4)
3. Check Cling logs (Test 5)
4. Try manual connection via IP

### Manual Connection Test
If automatic discovery fails:
```bash
# Access via browser
open http://192.168.0.49:5000/

# Copy IP from TV screen
# Manually enter in casting app (if supported)
```

---

## Test 7: Playback Verification

### Step 1: Start Casting
1. Select CasterTV device in casting app
2. Start video playback

### Step 2: Verify Playback Commands
```bash
# Check AVTransport logs
adb logcat -d | grep -E "AVTransport|SetAVTransportURI|Play|Pause|Stop"

# Expected:
# SetAVTransportURI request received
# Extracted media URI: http://...
# Play request received
# Media playback started
```

### Step 3: Verify ExoPlayer
```bash
# Check ExoPlayer logs
adb logcat -d | grep -E "ExoPlayer|media|Player"

# Expected:
# ExoPlayer initialized successfully
# Started playing media: http://...
```

### Step 4: Test Controls
1. **Pause**: Tap pause button in app → Check TV pauses
2. **Resume**: Tap play → Check TV resumes
3. **Stop**: Stop casting → Check TV returns to idle

---

## Test 8: Volume Control Verification

### Step 1: Test Volume Changes
1. In casting app, adjust volume slider
2. Observe volume change on TV

### Step 2: Check Logs
```bash
# Check RenderingControl logs
adb logcat -d | grep -E "Volume|Mute|SetVolume|SetMute"

# Expected:
# SetVolume request received
# Volume set to: XX
```

---

## Test 9: Multiple App Testing

### Test Matrix
| App | Discovery | Playback | Controls | Notes |
|-----|-----------|----------|----------|-------|
| B站 | ☐ | ☐ | ☐ | |
| YouTube | ☐ | ☐ | ☐ | |
| 腾讯视频 | ☐ | ☐ | ☐ | |
| 优酷 | ☐ | ☐ | ☐ | |
| Netflix | ☐ | ☐ | ☐ | |

### Instructions
For each app:
1. ✅ Mark discovery (does device appear?)
2. ✅ Mark playback (does video play?)
3. ✅ Mark controls (do play/pause/volume work?)
4. Add notes for any issues

---

## Debug Commands Reference

### Useful ADB Commands
```bash
# View all logs
adb logcat -d

# Filter by tag
adb logcat -d | grep "CasterTV"

# View crash logs
adb logcat -d | grep -E "FATAL|AndroidRuntime"

# View service status
adb shell dumpsys activity services com.caster.tv

# View network connections
adb shell netstat -an | grep 5000

# Check WiFi status
adb shell dumpsys wifi

# View system logs
adb logcat -d -b system
```

### Logcat Filter Expressions
```bash
# All CasterTV logs
adb logcat -d | grep -E "caster|CastCoordinator|SSDP|NSD|Cling"

# Error logs only
adb logcat -d | grep -E "ERROR|Exception|E/"

# UPnP specific
adb logcat -d | grep -E "UPnP|AVTransport|RenderingControl"

# Network related
adb logcat -d | grep -E "WiFi|Multicast|Socket"
```

---

## Expected Test Results

### Success Criteria
✅ **All tests pass if:**
1. APK builds without errors
2. HTTP server responds on port 5000
3. NSD services are registered
4. Cling service initializes without errors
5. Device appears in casting apps
6. Video playback works
7. Play/pause controls work
8. Volume control works

### Common Issues

#### Issue: Device Not Discovered
**Possible causes:**
- WiFi MulticastLock not acquired
- NSD service registration failed
- Cling service not initialized
- Firewall blocking multicast

**Solutions:**
1. Check `adb logcat | grep MulticastLock`
2. Verify NSD registration logs
3. Restart the service

#### Issue: Playback Fails
**Possible causes:**
- Media URL not accessible
- ExoPlayer initialization failed
- Network timeout

**Solutions:**
1. Check `adb logcat | grep ExoPlayer`
2. Verify media URL is accessible from TV
3. Check network bandwidth

#### Issue: Controls Not Working
**Possible causes:**
- AVTransport service not responding
- HTTP endpoint not accessible

**Solutions:**
1. Check `adb logcat | grep AVTransport`
2. Test HTTP control endpoints manually

---

## Test Report Template

After testing, fill out this report:

```
Date: ___________
Tester: ___________
TV IP: 192.168.0.49
TV Model: ___________

Build Status:
- Build successful: ☐ Yes ☐ No
- APK size: ___________ MB

Discovery Tests:
- HTTP server: ☐ Pass ☐ Fail
- SSDP NOTIFY: ☐ Pass ☐ Fail
- NSD services: ☐ Pass ☐ Fail
- Cling service: ☐ Pass ☐ Fail

Casting App Tests:
- B站: Discovery ☐ Playback ☐ Controls ☐
- YouTube: Discovery ☐ Playback ☐ Controls ☐
- 腾讯视频: Discovery ☐ Playback ☐ Controls ☐

Issues Found:
1. _______________
2. _______________

Overall Result: ☐ Success ☐ Partial ☐ Failed

Notes:
_______________
```

---

## Next Steps Based on Results

### If All Tests Pass
✅ Project is ready for production use!

### If Some Tests Fail
1. Review the failing test section above
2. Check the specific error logs
3. Fix issues and re-run tests

### If Build Fails
1. Verify Cling JAR files are in `app/libs/`
2. Check Java version (JDK 17 required)
3. Review build error messages
4. Ensure all dependencies are compatible

---

## Contact & Support

For issues not covered in this guide:
1. Check existing documentation:
   - `README.md` - Project overview
   - `TECHNICAL_ANALYSIS.md` - Technical details
   - `CLING_INTEGRATION.md` - Cling integration guide
2. Review logs carefully
3. Search for similar issues online
