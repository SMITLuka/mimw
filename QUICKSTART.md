# 🚀 FASTEST SOLUTION - 5 Minute Setup

## What You Asked For

> "I don't want to mess with AS400 and Tailscale, can I just do something on my side?"

**Answer: YES!** Use a simple proxy server.

---

## 🎯 The Absolute Simplest Way

### Option 1: Docker (Easiest - 2 Commands!)

On **any computer in your network** that can reach the AS400:

```bash
# 1. Run this ONE command:
docker run -d --name as400-proxy --restart unless-stopped \
  -p 8471:8471 \
  alpine/socat \
  tcp-listen:8471,fork,reuseaddr tcp-connect:172.19.237.6:8471

# 2. Update Render environment variable:
# SPRING_DATASOURCE_URL=jdbc:as400://YOUR_PROXY_PUBLIC_IP:8471;...
```

**Done!** That's literally it. 2 commands, 2 minutes.

---

### Option 2: Linux Command (If no Docker)

```bash
# 1. Install socat
sudo apt-get install -y socat

# 2. Run proxy
socat TCP-LISTEN:8471,fork,reuseaddr TCP:172.19.237.6:8471
```

Make it permanent:
```bash
# Create service file
sudo tee /etc/systemd/system/as400-proxy.service > /dev/null <<'EOF'
[Unit]
Description=AS400 Database Proxy
After=network.target

[Service]
Type=simple
ExecStart=/usr/bin/socat TCP-LISTEN:8471,fork,reuseaddr TCP:172.19.237.6:8471
Restart=always

[Install]
WantedBy=multi-user.target
EOF

# Enable and start
sudo systemctl enable --now as400-proxy
```

---

### Option 3: Windows PowerShell

Save as `AS400-Proxy.ps1` and run:

```powershell
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, 8471)
$listener.Start()
Write-Host "Proxy listening on port 8471 → forwarding to 172.19.237.6:8471"

while ($true) {
    $client = $listener.AcceptTcpClient()
    Write-Host "Connection from $($client.Client.RemoteEndPoint)"
    
    Start-Job {
        param($c)
        $a = New-Object System.Net.Sockets.TcpClient("172.19.237.6", 8471)
        $cs = $c.GetStream(); $as = $a.GetStream()
        $cs.CopyToAsync($as); $as.CopyToAsync($cs)
    } -ArgumentList $client
}
```

---

## 📋 Complete Steps

### Step 1: Set Up Proxy (Pick ONE)

| Method | Time | Command |
|--------|------|---------|
| **Docker** | 30 seconds | `docker run -d --name as400-proxy --restart unless-stopped -p 8471:8471 alpine/socat tcp-listen:8471,fork,reuseaddr tcp-connect:172.19.237.6:8471` |
| **Linux** | 1 minute | `sudo apt install -y socat && socat TCP-LISTEN:8471,fork TCP:172.19.237.6:8471` |
| **Windows** | 2 minutes | Run PowerShell script above |

### Step 2: Expose Proxy to Internet

**Option A: Proxy has public IP**
- Done! Use that IP directly

**Option B: Behind router (most common)**
- Forward port 8471 on your router to the proxy machine
- Use your router's public IP or domain

### Step 3: Update Render

In Render dashboard → Environment variables:

```bash
SPRING_DATASOURCE_URL=jdbc:as400://YOUR_PUBLIC_IP_OR_DOMAIN:8471;prompt=false;naming=sql;errors=full;keepAlive=true;loginTimeout=60
```

**That's it!** 🎉

---

## 🧪 Testing

```bash
# 1. Test proxy locally (from proxy machine):
telnet localhost 8471

# 2. Test from outside (from your computer):
telnet YOUR_PUBLIC_IP 8471

# 3. Test from Render:
curl https://mimw.onrender.com/health/db
```

---

## 🔒 Security (Important!)

Your AS400 is now accessible from the internet! Protect it:

### Firewall Rules (Required!)

**Linux (iptables):**
```bash
# Only allow Render's IP (get from Render support or use static IP addon)
sudo iptables -A INPUT -p tcp --dport 8471 -s RENDER_IP -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 8471 -j DROP
```

**Or use Render's Static IP addon:**
- Cost: $7-20/month
- Get a fixed IP from Render
- Whitelist only that IP in your firewall

---

## ✅ What You DON'T Touch

- ❌ AS400 (no changes at all!)
- ❌ AS400 configuration
- ❌ AS400 firewall
- ❌ Java code (already updated)
- ❌ Dockerfile (already updated)
- ❌ Tailscale (don't need it!)

## ✅ What You DO

- ✅ Run 1 command on a computer in your network
- ✅ Port forward (or use public IP)
- ✅ Update 1 environment variable on Render

**Total: 5-10 minutes**

---

## 💰 Cost

- **Proxy server:** $0 (use existing computer/VM)
- **Public IP:** $0 (use existing router IP)
- **Render Static IP:** $7-20/month (optional, for security)

**Total: $0-20/month**

---

## 📊 Comparison

| | Simple Proxy | Tailscale | Public AS400 |
|---|---|---|---|
| **Setup time** | 5 min | 30 min | 30 min |
| **AS400 changes** | None | None | Major |
| **Cost** | $0 | $0 | Varies |
| **Security** | Firewall | Encrypted | Risk |
| **Maintenance** | Very low | Low | High |

---

## 🎬 Quick Start Video Guide

### Docker Method (Recommended):

```bash
# 1. SSH to any server in your network
ssh user@your-server

# 2. Run proxy
docker run -d \
  --name as400-proxy \
  --restart unless-stopped \
  -p 8471:8471 \
  alpine/socat \
  tcp-listen:8471,fork,reuseaddr tcp-connect:172.19.237.6:8471

# 3. Verify it's running
docker ps | grep as400-proxy
docker logs as400-proxy

# 4. Test locally
telnet localhost 8471
# (press Ctrl+] then type 'quit')

# 5. Configure port forwarding on your router (if needed)
# Forward external port 8471 → your-server-ip:8471

# 6. Update Render
# Go to https://dashboard.render.com
# Your service → Environment tab
# Update: SPRING_DATASOURCE_URL=jdbc:as400://YOUR_PUBLIC_IP:8471;...

# 7. Test from internet
curl https://mimw.onrender.com/health/db
```

**Done! ✅**

---

## 🆘 Troubleshooting

### Proxy won't start
```bash
# Check if port 8471 is already in use
sudo netstat -tlnp | grep 8471

# Kill existing process if needed
sudo kill -9 $(sudo lsof -t -i:8471)
```

### Can't connect from Render
- Check firewall allows port 8471
- Verify port forwarding is correct
- Test with: `telnet YOUR_PUBLIC_IP 8471` from your computer
- Check proxy logs: `docker logs as400-proxy`

### Connection works but slow
- Add connection pooling (already configured in application.yaml)
- Consider using Render's Static IP (lower latency)
- Check network bandwidth

---

## 📁 Files Created for You

1. **`SIMPLE_PROXY_SOLUTION.md`** - Detailed proxy setup guide
2. **`docker-compose-proxy.yml`** - Docker Compose version
3. **`THIS FILE`** - Quick start guide

---

## 🎯 Summary

**Your question:** "Can I just do something on my side?"

**Answer:** YES - run a tiny proxy in your network (not on AS400):

```bash
# Literally one command:
docker run -d --restart unless-stopped -p 8471:8471 alpine/socat \
  tcp-listen:8471,fork,reuseaddr tcp-connect:172.19.237.6:8471
```

Then update Render environment variable. **Done in 5 minutes!**

---

## Next Steps

1. **Choose your method** (Docker recommended)
2. **Run the command** on any machine in your network
3. **Configure port forwarding** (if behind router)
4. **Update Render** environment variable
5. **Test** with `/health/db` endpoint

**Questions?** All details are in `SIMPLE_PROXY_SOLUTION.md`

---

**Need help?** The health check endpoint will tell you if it's working:
```
https://mimw.onrender.com/health/db
```

Good luck! This is the simplest possible solution. 🚀

