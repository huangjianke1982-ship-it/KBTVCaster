#!/bin/bash
# Quick Test Script for CasterTV
# Usage: ./quick_test.sh <TV_IP>

TV_IP=${1:-"192.168.0.49"}
PORT=5000

echo "=========================================="
echo "CasterTV Quick Test Script"
echo "TV IP: $TV_IP"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

test_endpoint() {
    local name=$1
    local path=$2

    if curl -s --connect-timeout 3 "http://$TV_IP:$PORT$path" > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} $name: PASS"
        return 0
    else
        echo -e "${RED}✗${NC} $name: FAIL"
        return 1
    fi
}

echo "1. HTTP Server Endpoints"
echo "------------------------"
test_endpoint "Root URL" "/"
test_endpoint "Device Description" "/description.xml"
test_endpoint "Discovery JSON" "/cast.json"
test_endpoint "Google Cast Info" "/setup/eureka_info"
test_endpoint "SSDP Device Info" "/ssdp/device_info.xml"
test_endpoint "Host Info" "/host_info"
test_endpoint "DIAL Apps" "/apps/"
echo ""

echo "2. NSD Service Discovery"
echo "------------------------"
echo "Check TV logs for NSD registration:"
echo "  adb logcat -d | grep -E 'NSD|GoogleCast'"
echo "Expected: 'HTTP NSD service registered' and 'Google Cast NSD service registered'"
echo ""

echo "3. SSDP Discovery"
echo "-----------------"
echo "Check TV logs for SSDP:"
echo "  adb logcat -d | grep -E 'SSDP|M-SEARCH'"
echo "Expected: 'SSDP NOTIFY sent' and 'M-SEARCH DETECTED'"
echo ""

echo "4. Cling UPnP Service"
echo "---------------------"
echo "Check TV logs for Cling:"
echo "  adb logcat -d | grep -E 'Cling|AndroidRouter|ZxtMediaRenderer'"
echo "Expected: 'UPnP service initialized' and 'Media Renderer device registered'"
echo ""

echo "5. Service Status"
echo "-----------------"
echo "Check running services:"
echo "  adb shell dumpsys activity services | grep caster"
echo ""

echo "6. Quick Log Check"
echo "-----------------"
echo "Recent CasterTV logs:"
echo "  adb logcat -d -t 100 | grep -E 'caster|CastCoordinator'"
echo ""

echo "=========================================="
echo "Testing Complete"
echo "=========================================="
echo ""
echo "Next Steps:"
echo "1. Install APK: ./gradlew assembleDebug && adb install app/build/outputs/apk/debug/app-debug.apk"
echo "2. Start service: adb shell am start -n com.caster.tv/.ui.MainActivity"
echo "3. Test with casting apps (B站, YouTube, etc.)"
echo ""
