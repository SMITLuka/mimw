#!/bin/bash
set -e

echo "========================================"
echo "Starting MIMW Application with Tailscale"
echo "========================================"

# Check if Tailscale auth key is provided
if [ -z "$TAILSCALE_AUTHKEY" ]; then
    echo "WARNING: TAILSCALE_AUTHKEY not set. Skipping Tailscale setup."
    echo "Application will start without VPN connection."
    echo "This will work locally but may fail to connect to AS400 on Render."
else
    echo "Starting Tailscale daemon..."
    # Start Tailscale daemon in background with userspace networking
    # (required for containerized environments without root/TUN access)
    /usr/sbin/tailscaled --tun=userspace-networking --socks5-server=localhost:1055 --state=/tmp/tailscale.state &

    # Wait for tailscaled to start
    sleep 5

    echo "Connecting to Tailscale network..."
    # Connect to Tailscale using auth key
    # --hostname: identifies this instance in Tailscale admin
    # --accept-routes: allows accessing advertised subnet routes (for AS400)
    /usr/bin/tailscale up \
        --authkey="${TAILSCALE_AUTHKEY}" \
        --hostname=mimw-render-$(date +%s) \
        --accept-routes \
        --advertise-tags=tag:render

    # Wait for Tailscale to establish connection
    echo "Waiting for Tailscale to connect..."
    RETRY_COUNT=0
    MAX_RETRIES=30
    until /usr/bin/tailscale status 2>/dev/null | grep -q "100\." || [ $RETRY_COUNT -eq $MAX_RETRIES ]; do
        echo "  Attempt $((RETRY_COUNT+1))/$MAX_RETRIES..."
        sleep 2
        RETRY_COUNT=$((RETRY_COUNT+1))
    done

    if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
        echo "ERROR: Failed to connect to Tailscale after $MAX_RETRIES attempts"
        echo "Application will start but may not reach AS400 database"
    else
        echo ""
        echo "✓ Tailscale connected successfully!"
        echo ""
        echo "Tailscale Status:"
        /usr/bin/tailscale status
        echo ""

        # Test connectivity to AS400
        echo "Testing connectivity to AS400 (172.19.237.6)..."
        if timeout 5 bash -c 'cat < /dev/null > /dev/tcp/172.19.237.6/8471' 2>/dev/null; then
            echo "✓ AS400 is reachable on port 8471"
        else
            echo "✗ Cannot reach AS400 on port 8471"
            echo "  Check if subnet routes are approved in Tailscale admin console"
        fi
        echo ""
    fi
fi

echo "========================================"
echo "Starting Spring Boot Application"
echo "========================================"

# Start Spring Boot application
exec java -jar /app/app.war

