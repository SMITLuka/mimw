# Setting Up Tailscale VPN on Render to Access Private AS400 Database

This guide shows how to connect your Render deployment to your private AS400 database (172.19.237.6) using Tailscale, a mesh VPN service.

## Why Tailscale?

- ✅ Works with Render's Docker-based deployment
- ✅ No need to expose AS400 to the public internet
- ✅ Encrypted peer-to-peer connections
- ✅ Free tier available (up to 100 devices)
- ✅ Easy to set up and maintain
- ✅ No complex firewall rules needed

## Prerequisites

1. Tailscale account (sign up at https://tailscale.com)
2. Access to your AS400 network (to install Tailscale client)
3. Docker-based deployment on Render
4. Auth key from Tailscale (for headless authentication)

## Step 1: Install Tailscale in Your AS400 Network

### Option A: Install on a Linux/Windows server in the same network
If you have a server in the same network as the AS400:

```bash
# On Ubuntu/Debian:
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up

# On Windows:
# Download and install from https://tailscale.com/download/windows
```

### Option B: Install on a router
Some routers support Tailscale directly. Check https://tailscale.com/kb/1019/subnets/

Once installed, enable subnet routing to expose the AS400:
```bash
# On the Tailscale client in your network:
sudo tailscale up --advertise-routes=172.19.237.0/24 --accept-routes
```

Then in Tailscale admin console, approve the subnet route.

## Step 2: Generate Tailscale Auth Key

1. Go to https://login.tailscale.com/admin/settings/keys
2. Click **Generate auth key**
3. Select:
   - ✅ Reusable
   - ✅ Ephemeral (optional, for short-lived containers)
   - ✅ Pre-authorized
4. Copy the generated key (starts with `tskey-auth-...`)

## Step 3: Update Your Dockerfile

Modify your Dockerfile to include Tailscale:

```dockerfile
FROM openjdk:21-jdk-slim

# Install Tailscale
RUN apt-get update && apt-get install -y \
    curl \
    iptables \
    ca-certificates \
    && curl -fsSL https://tailscale.com/install.sh | sh \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Copy your application
COPY target/*.war /app/app.war

# Create startup script
COPY start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 8081

CMD ["/start.sh"]
```

## Step 4: Create Startup Script

Create `start.sh` in your project root:

```bash
#!/bin/bash
set -e

echo "Starting Tailscale daemon..."
# Start Tailscale daemon in background
/usr/sbin/tailscaled --tun=userspace-networking --socks5-server=localhost:1055 &

# Wait for tailscaled to start
sleep 5

echo "Connecting to Tailscale network..."
# Connect to Tailscale using auth key
/usr/bin/tailscale up --authkey=${TAILSCALE_AUTHKEY} --hostname=mimw-render --accept-routes

# Wait for Tailscale to be fully connected
sleep 5

echo "Tailscale status:"
/usr/bin/tailscale status

echo "Starting Spring Boot application..."
# Start your Spring Boot app
exec java -jar /app/app.war
```

Make it executable:
```bash
chmod +x start.sh
```

## Step 5: Update Render Environment Variables

In your Render dashboard, add these environment variables:

```bash
# Tailscale auth key (from Step 2)
TAILSCALE_AUTHKEY=tskey-auth-xxxxxxxxxxxxx

# Update database URL to use AS400's real IP (Tailscale will route it)
SPRING_DATASOURCE_URL=jdbc:as400://172.19.237.6;prompt=false;naming=sql;errors=full;keepAlive=true;loginTimeout=60

SPRING_DATASOURCE_USERNAME=msecofr
SPRING_DATASOURCE_PASSWORD=CDP0326
```

## Step 6: Deploy to Render

```bash
# Add the startup script to git
git add start.sh Dockerfile
git commit -m "Add Tailscale VPN support"
git push

# Render will automatically redeploy
```

## Step 7: Verify Connection

1. Check Render logs to see if Tailscale connected:
   ```
   Starting Tailscale daemon...
   Connecting to Tailscale network...
   Tailscale status:
   ```

2. Visit your health check endpoint:
   ```
   https://mimw.onrender.com/health/db
   ```

3. Check Tailscale admin console to see your Render instance connected

## Troubleshooting

### Tailscale fails to start
- **Error:** `permission denied` → Render doesn't allow TUN devices
- **Solution:** Use `--tun=userspace-networking` (already in script above)

### Can't reach AS400
- Check if subnet routes are approved in Tailscale admin console
- Verify the Tailscale client in your network has `--advertise-routes` enabled
- Test connectivity: Add this to start.sh before starting app:
  ```bash
  ping -c 3 172.19.237.6 || echo "Cannot reach AS400"
  ```

### Tailscale disconnects
- Use a **reusable** auth key (not ephemeral) 
- Consider using `--authkey=${TAILSCALE_AUTHKEY} --force-reauth` in start.sh

### Application starts before Tailscale connects
- Increase sleep time in start.sh after `tailscale up`
- Add connection verification:
  ```bash
  until tailscale status --json | grep -q '"Online":true'; do
    echo "Waiting for Tailscale to connect..."
    sleep 2
  done
  ```

## Alternative: Without Dockerfile Changes

If you can't modify the Dockerfile, use a **sidecar proxy pattern**:

1. Deploy a separate Render service that runs Tailscale + TCP proxy
2. Have your main app connect to the proxy service
3. The proxy forwards traffic through Tailscale to AS400

## Cost Considerations

- **Tailscale Free tier:** Up to 100 devices, 3 users - likely sufficient
- **Render:** May need to use a paid plan for persistent storage (Tailscale state)
- **Alternative:** Use Render's static IP feature ($7-20/month) if you can expose AS400 with IP whitelisting

## Security Best Practices

1. **Rotate auth keys** periodically
2. **Use ACLs** in Tailscale to restrict which devices can talk to AS400
3. **Enable MFA** on your Tailscale account
4. **Monitor connections** in Tailscale admin console
5. **Use tags** to organize and control access to devices

## Resources

- [Tailscale Documentation](https://tailscale.com/kb/)
- [Tailscale in Docker](https://tailscale.com/kb/1282/docker/)
- [Subnet Routing](https://tailscale.com/kb/1019/subnets/)
- [Render Dockerfile Reference](https://render.com/docs/docker)

## Support

If you encounter issues:
1. Check Render logs for Tailscale connection status
2. Verify in Tailscale admin console that both endpoints are online
3. Test AS400 connectivity from within the container
4. Contact Tailscale support (they're very responsive)

