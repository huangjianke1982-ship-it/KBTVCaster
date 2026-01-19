# CasterTV Discovery Enhancement - Implementation Summary

## Problem
- **SSDP port 1900 is blocked** by Android TV system (BindException: EADDRINUSE)
- Phone casting apps (B站, YouTube, Netflix) cannot discover the device using standard SSDP
- HTTP server on port 5000 works but is not discoverable

## Solution Implemented
Added **multiple discovery protocols** to ensure maximum compatibility with casting apps:

### 1. Enhanced NSD/mDNS Services
Registered **3 NSD service types** for different discovery methods:
- `_http._tcp.` - Standard HTTP service discovery
- `_googlecast._tcp.` - Google Cast compatible (for Chromecast apps)
- `_cast._tcp.` - Generic casting service

Each service includes device attributes for better identification.

### 2. HTTP Discovery Endpoints
Added multiple HTTP endpoints that apps can poll for device discovery:

| Endpoint | Format | Purpose |
|----------|--------|---------|
| `/cast.json` | JSON | Standard device discovery (existing) |
| `/setup/eureka_info` | JSON | Google Cast compatible |
| `/ssdp/device_info.xml` | XML | DIAL protocol compatible |
| `/host_info` | JSON | Network scanning apps |
| `/apps/` | XML | DIAL app listing |
| `/apps/<appname>` | XML | DIAL app info |

### 3. Google Cast Compatible Response Format
```json
{
    "name": "CasterTV-XXXX",
    "id": "XXXX",
    "model_name": "CasterTV",
    "manufacturer": "CasterTV",
    "capabilities": {
        "video_out": true,
        "audio_out": true
    },
    "protocols": ["cast", "dial"]
}
```

## Files Modified

### `app/src/main/java/com/caster/tv/service/CastCoordinatorService.kt`

**Changes:**
1. Added new fields for multiple service registrations (lines 56-60)
2. Replaced `registerNsdService()` with `registerNsdServices()` (line 126)
3. Added 3 new registration functions:
   - `registerHttpService()` - Standard HTTP NSD
   - `registerGoogleCastService()` - Google Cast compatible
   - `registerCastService()` - Generic cast service
4. Enhanced `handleRequest()` with 5 new endpoints (lines 629-644)
5. Added 5 new response functions:
   - `createGoogleCastInfoResponse()` - Google Cast endpoint
   - `createSsdpDeviceInfoResponse()` - DIAL device info
   - `createHostInfoResponse()` - Network scanning
   - `createDialAppsResponse()` - DIAL app listing
   - `handleDialLaunch()` - DIAL launch handler

## Testing Instructions

### 1. Build and Install
```bash
cd CasterTVService
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Start Service on Android TV
```bash
adb shell am start -n com.caster.tv/.ui.MainActivity
```

### 3. Test HTTP Endpoints from Phone
From your phone (or PC on same network), navigate to:

```bash
# Test cast.json discovery
curl http://192.168.0.49:5000/cast.json

# Test Google Cast endpoint
curl http://192.168.0.49:5000/setup/eureka_info

# Test DIAL device info
curl http://192.168.0.49:5000/ssdp/device_info.xml

# Test host info
curl http://192.168.0.49:5000/host_info

# Test DIAL apps
curl http://192.168.0.49:5000/apps/
```

### 4. Test NSD/mDNS Discovery
Use a network scanning app on your phone to discover services:
- Look for `_googlecast._tcp.` service type
- Look for `_http._tcp.` service type
- Look for `_cast._tcp.` service type

### 5. View Logs
```bash
adb logcat -d | grep -iE "caster|nsd|googlecast"
```

Expected output should show:
- "Google Cast NSD service registered"
- "HTTP NSD service registered"  
- "Cast NSD service registered"

## Expected Results

### Phone Browser Access
All endpoints should return valid responses with device information:
- `/cast.json` → Device list in JSON format
- `/setup/eureka_info` → Google Cast compatible device info
- `/ssdp/device_info.xml` → DLNA device description
- `/host_info` → Host information with service URLs

### NSD Discovery
Apps that use Network Service Discovery should find the device under:
- Service type: `_googlecast._tcp.`
- Service name: `CasterTV-XXXX-googlecast`
- Port: 5000

## Next Steps

1. **Test with Casting Apps**
   - Try B站 (Bilibili) casting
   - Try YouTube casting
   - Try Netflix casting
   
2. **If Apps Still Don't Discover**
   - Implement QR code scanning for manual connection
   - Add manual IP entry feature
   - Consider WiFi Direct for peer-to-peer connection

3. **Monitor Logs**
   ```bash
   adb logcat -d | grep -iE "caster"
   ```

## Technical Notes

### Why Multiple Protocols?
Different casting apps use different discovery methods:
- **Chromecast apps** → mDNS with `_googlecast._tcp.`
- **DLNA apps** → SSDP (blocked) or HTTP polling
- **DIAL apps** → SSDP discovery + HTTP endpoints
- **Custom apps** → Can poll any HTTP endpoint

### Fallback Strategy
- If SSDP is blocked (port 1900), NSD provides alternative discovery
- HTTP endpoints provide polling-based discovery for any app
- Multiple NSD services maximize compatibility

## Troubleshooting

### NSD Registration Fails
- Check if WiFi multicast is enabled
- Verify CHANGE_WIFI_MULTICAST_STATE permission
- Look for errors in logcat

### HTTP Endpoints Return 404
- Verify service is running (check notification)
- Check if port 5000 is correct
- Verify firewall allows port 5000

### Apps Still Can't Discover
- Try using a network scanning app to verify NSD
- Test HTTP endpoints manually from phone
- Check if phone and TV are on same subnet
- Verify no router firewall blocking multicast

## References

- **Google Cast Protocol**: https://developers.google.com/cast/docs/reference/dial
- **DIAL Protocol**: https://dial-multiscreen.org/
- **DLNA/UPnP**: https://www.upnp.org/
- **Android NSD**: https://developer.android.com/training/connect-devices-wlessly/nsd
