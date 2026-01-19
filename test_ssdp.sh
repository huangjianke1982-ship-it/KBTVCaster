#!/system/bin/sh
# Test script for SSDP/HTTP server
echo "Testing HTTP server on port 5000..."

# Use nc if available, otherwise use echo with /dev/tcp
if [ -f /system/bin/nc ]; then
    echo -e "GET /description.xml HTTP/1.1\r\nHost: 127.0.0.1:5000\r\n\r\n" | nc -w 3 127.0.0.1 5000
else
    exec 3<>/dev/tcp/127.0.0.1/5000
    echo -e "GET /description.xml HTTP/1.1\r\nHost: 127.0.0.1:5000\r\n\r\n" >&3
    cat <&3
fi
