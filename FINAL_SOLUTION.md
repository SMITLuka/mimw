# 🎯 FINAL SOLUTION - Simple Proxy (No AS400 Changes)

## What You Wanted
✅ Something you can do on your side only  
✅ No AS400 changes  
✅ No Tailscale complexity  
✅ Quick and simple  

## What You're Getting
A tiny proxy server that runs on ANY computer in your network and forwards connections to AS400.

---

## 🚀 Implementation (Choose Your Path)

### PATH 1: Docker (Recommended - 2 Commands)

```bash
# On ANY computer in your network that can reach AS400:

# 1. Run the proxy
docker run -d \
  --name as400-proxy \
  --restart unless-stopped \
  -p 8471:8471 \
  alpine/socat \
  tcp-listen:8471,fork,reuseaddr tcp-connect:172.19.237.6:8471

# 2. Verify it's running
docker logs as400-proxy

# Done! ✅
```

---

### PATH 2: Linux (No Docker - 3 Commands)

```bash
# 1. Install socat
sudo apt-get update && sudo apt-get install -y socat

# 2. Run proxy in background
nohup socat TCP-LISTEN:8471,fork,reuseaddr TCP:172.19.237.6:8471 &

# 3. Make it permanent (survives reboot)
sudo tee /etc/systemd/system/as400-proxy.service > /dev/null <<'EOF'
[Unit]
Description=AS400 Database Proxy
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/socat TCP-LISTEN:8471,fork,reuseaddr TCP:172.19.237.6:8471
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl enable --now as400-proxy
sudo systemctl status as400-proxy

# Done! ✅
```

---

### PATH 3: Windows (PowerShell)

Save this as `C:\AS400-Proxy.ps1`:

```powershell
Write-Host "AS400 Database Proxy - Starting..."
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, 8471)
$listener.Start()
Write-Host "Listening on port 8471 → Forwarding to 172.19.237.6:8471"
Write-Host "Press Ctrl+C to stop"

while ($true) {
    try {
        $client = $listener.AcceptTcpClient()
        $endpoint = $client.Client.RemoteEndPoint
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Connection from $endpoint"
        
        Start-ThreadJob {
            param($client)
            try {
                $as400 = New-Object System.Net.Sockets.TcpClient("172.19.237.6", 8471)
                $clientStream = $client.GetStream()
                $as400Stream = $as400.GetStream()
                
                # Forward data both ways
                $task1 = $clientStream.CopyToAsync($as400Stream)
                $task2 = $as400Stream.CopyToAsync($clientStream)
                [System.Threading.Tasks.Task]::WaitAll($task1, $task2)
            }
            catch {
                Write-Host "Error: $_"
            }
            finally {
                if ($client) { $client.Close() }
                if ($as400) { $as400.Close() }
            }
        } -ArgumentList $client | Out-Null
    }
    catch {
        Write-Host "Listener error: $_"
        Start-Sleep -Seconds 1
    }
}
```

Run it:
```powershell
# Run in foreground (test first)
powershell -ExecutionPolicy Bypass -File C:\AS400-Proxy.ps1

# Or run as Windows Service with NSSM:
# 1. Download NSSM: https://nssm.cc/download
# 2. Install service:
nssm install AS400Proxy powershell -ExecutionPolicy Bypass -File C:\AS400-Proxy.ps1
nssm start AS400Proxy
```

---

## 🌐 Network Configuration

### If Your Proxy Machine Has a Public IP
Nothing to do! Just use that IP directly.

### If Behind Router (Most Common)
Configure port forwarding on your router:

1. Log into your router (usually 192.168.1.1 or 192.168.0.1)
2. Find "Port Forwarding" or "Virtual Server" section
3. Add rule:
   - **External Port:** 8471
   - **Internal IP:** [Your proxy machine IP]
   - **Internal Port:** 8471
   - **Protocol:** TCP
4. Save and apply

Your app will connect to: `YOUR_ROUTER_PUBLIC_IP:8471`

Find your public IP: https://whatismyipaddress.com

---

## 🔧 Update Render

### Step 1: Find Your Public IP or Domain

```bash
# If you don't know your public IP:
curl ifconfig.me

# Or use a domain if you have one
# e.g., mydomain.com or vpn.mycompany.com
```

### Step 2: Update Environment Variable

Go to: https://dashboard.render.com → Your Service → Environment

**Update this variable:**
```
SPRING_DATASOURCE_URL=jdbc:as400://YOUR_PUBLIC_IP:8471;prompt=false;naming=sql;errors=full;keepAlive=true;loginTimeout=60
```

**Example:**
```
SPRING_DATASOURCE_URL=jdbc:as400://203.0.113.50:8471;prompt=false;naming=sql;errors=full;keepAlive=true;loginTimeout=60
```

### Step 3: Save and Redeploy

Click "Save Changes" - Render will automatically redeploy.

---

## ✅ Testing

### 1. Test Proxy Locally (from proxy machine)
```bash
telnet localhost 8471
# You should see: "Trying 127.0.0.1..."
# Press Ctrl+] then type 'quit'
```

### 2. Test from Your Network
```bash
telnet YOUR_PROXY_IP 8471
```

### 3. Test from Internet (after port forwarding)
```bash
telnet YOUR_PUBLIC_IP 8471
```

### 4. Test Render App
```bash
# Health check endpoint (I created this for you)
curl https://mimw.onrender.com/health/db

# Expected response:
{
  "success": true,
  "message": "Database connection OK",
  "data": {
    "connected": true,
    "valid": true,
    "message": "AS400 database connection successful"
  }
}
```

### 5. Test Your Actual Endpoints
```bash
# Try your real endpoints
curl https://mimw.onrender.com/api/taxpayer-types
curl https://mimw.onrender.com/api/tax-offices
```

---

## 🔒 IMPORTANT: Security Setup

⚠️ **Your AS400 is now accessible from the internet!** You MUST secure it.

### Option 1: Firewall Rules (Free but Manual)

**On Linux proxy machine:**
```bash
# Install firewall
sudo apt-get install -y ufw

# Allow SSH (so you don't lock yourself out!)
sudo ufw allow 22/tcp

# Allow port 8471 from Render's IP only
# Replace RENDER_IP with actual IP (see below)
sudo ufw allow from RENDER_IP to any port 8471

# Enable firewall
sudo ufw enable
```

**Get Render's IP:**
- Contact Render support, OR
- Check your logs for incoming connection IP, OR
- Use Render Static IP addon (see below)

### Option 2: Render Static IP (Recommended - $7-20/month)

1. Go to Render Dashboard → Your Service → Settings
2. Enable "Static Outbound IP"
3. Note the IP address
4. Whitelist only that IP in your firewall

**Benefits:**
- Fixed IP that never changes
- Easy to whitelist
- Professional solution

### Option 3: VPN on Top (Most Secure)

If you want maximum security, you can still use Tailscale for the proxy itself:
- Install Tailscale on proxy machine
- Don't expose port 8471 publicly
- Render connects through Tailscale to proxy
- Proxy forwards to AS400

(See `TAILSCALE_SETUP.md` if interested)

---

## 📊 What Did You Change?

| Component | Change Required |
|-----------|----------------|
| **AS400** | ❌ None! |
| **AS400 Config** | ❌ None! |
| **AS400 Firewall** | ❌ None! |
| **Proxy Machine** | ✅ Run 1 command |
| **Router** | ✅ Port forward (if behind router) |
| **Render** | ✅ Update 1 env variable |
| **Your Code** | ✅ Already updated! |

---

## 💰 Cost Breakdown

| Item | Cost |
|------|------|
| Proxy software (socat/docker) | Free |
| Proxy machine | $0 (use existing) |
| Port forwarding | Free |
| Render Static IP (optional) | $7-20/month |
| **Total** | **$0-20/month** |

---

## 🔧 Maintenance

### Checking if Proxy is Running

**Docker:**
```bash
docker ps | grep as400-proxy
docker logs as400-proxy
```

**Linux Service:**
```bash
sudo systemctl status as400-proxy
sudo journalctl -u as400-proxy -f
```

**Windows:**
```powershell
Get-Service AS400Proxy
# Or check Task Manager → Services
```

### Restarting Proxy

**Docker:**
```bash
docker restart as400-proxy
```

**Linux:**
```bash
sudo systemctl restart as400-proxy
```

**Windows:**
```powershell
Restart-Service AS400Proxy
```

### Updating Proxy

Nothing to update! It's just a TCP forwarder.

---

## 🆘 Troubleshooting

### Proxy Won't Start - Port Already in Use
```bash
# Check what's using port 8471
sudo netstat -tlnp | grep 8471
# Or
sudo lsof -i :8471

# Kill it
sudo kill -9 <PID>
```

### Can't Connect from Render
1. **Check firewall:** Is port 8471 allowed?
2. **Check port forward:** Router configured correctly?
3. **Test from outside:** Can you telnet from your phone (using mobile data)?
4. **Check logs:** What do proxy logs say?

### Connection Timeout
- Increase timeout in application.yaml (already set to 60s)
- Check network latency: `ping YOUR_PUBLIC_IP`
- Check AS400 is reachable from proxy: `telnet 172.19.237.6 8471`

### Slow Performance
- Check network bandwidth
- Consider moving proxy closer to AS400
- Use connection pooling (already configured)

### Proxy Crashes
- Check system resources (CPU, memory)
- Check logs for errors
- Restart proxy: `docker restart as400-proxy`

---

## 📁 Files Reference

You have these files for different solutions:

| File | Purpose |
|------|---------|
| **`QUICKSTART.md`** | Quick reference (this file) |
| `SIMPLE_PROXY_SOLUTION.md` | Detailed proxy guide |
| `docker-compose-proxy.yml` | Docker Compose version |
| `TAILSCALE_SETUP.md` | If you want VPN encryption |
| `SOLUTION_COMPARISON.md` | Compare all options |
| `VPN_ON_RENDER.md` | Original question answered |

---

## ✨ Success Criteria

You're done when:

- [✓] Proxy is running and auto-restarts
- [✓] Port 8471 is accessible from internet
- [✓] Firewall rules protect your network
- [✓] Render environment variable updated
- [✓] `/health/db` returns success
- [✓] Your app works end-to-end

---

## 🎯 Quick Command Reference

```bash
# Docker - Start proxy
docker run -d --name as400-proxy --restart unless-stopped -p 8471:8471 alpine/socat tcp-listen:8471,fork,reuseaddr tcp-connect:172.19.237.6:8471

# Docker - Check logs
docker logs -f as400-proxy

# Docker - Restart
docker restart as400-proxy

# Docker - Stop
docker stop as400-proxy

# Test connection
telnet YOUR_PUBLIC_IP 8471

# Test health
curl https://mimw.onrender.com/health/db

# Render env variable
SPRING_DATASOURCE_URL=jdbc:as400://YOUR_PUBLIC_IP:8471;prompt=false;naming=sql;errors=full;keepAlive=true;loginTimeout=60
```

---

## 🎉 Summary

You wanted a solution on your side only, without touching AS400 or dealing with Tailscale.

**You got it:**
1. Run tiny proxy on any computer in your network (1 command)
2. Forward port 8471 on your router (5 minutes)
3. Update Render environment variable (1 minute)

**Total time: ~10 minutes**  
**Cost: $0** (or $7-20/month for Render Static IP)  
**AS400 changes: None!** ✅

---

**Ready to start?** Pick your path (Docker/Linux/Windows) and follow the commands above!

**Need help?** Check `SIMPLE_PROXY_SOLUTION.md` for more details.

**Questions?** Test with `/health/db` endpoint - it will tell you what's wrong.

Good luck! 🚀

