# Simplest Solution - Database Proxy (No AS400 Changes Required)

## What This Does

Creates a simple TCP proxy that:
- Runs on ANY computer in your AS400 network
- Listens on a public IP (or port forward)
- Forwards all traffic to AS400 (172.19.237.6:8471)
- **Doesn't require touching AS400 at all**

## Requirements

- Any Linux/Windows computer in your network that can reach 172.19.237.6
- Ability to give it a public IP or port forward from your router
- 10 minutes setup time

---

## Option 1: Socat Proxy (Simplest - 5 minutes)

### On Any Linux Machine in Your Network:

```bash
# Install socat
sudo apt-get update && sudo apt-get install -y socat

# Run the proxy (forwards port 8471 to AS400)
socat TCP-LISTEN:8471,fork,reuseaddr TCP:172.19.237.6:8471
```

### Make it run on startup:

Create `/etc/systemd/system/as400-proxy.service`:
```ini
[Unit]
Description=AS400 Database Proxy
After=network.target

[Service]
Type=simple
User=root
ExecStart=/usr/bin/socat TCP-LISTEN:8471,fork,reuseaddr TCP:172.19.237.6:8471
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable it:
```bash
sudo systemctl enable as400-proxy
sudo systemctl start as400-proxy
```

### Configure Firewall:
```bash
# Allow port 8471 from specific IPs only (security!)
sudo ufw allow from RENDER_IP to any port 8471
```

### Update Render:
```bash
SPRING_DATASOURCE_URL=jdbc:as400://YOUR_PROXY_PUBLIC_IP:8471;prompt=false;naming=sql;errors=full
```

**That's it!** No AS400 changes, no Tailscale, just one small proxy.

---

## Option 2: Python Proxy (If you prefer Python)

Create `as400_proxy.py` on any machine in your network:

```python
#!/usr/bin/env python3
import socket
import threading
import sys

AS400_HOST = "172.19.237.6"
AS400_PORT = 8471
LISTEN_PORT = 8471

def forward(source, destination):
    try:
        while True:
            data = source.recv(4096)
            if not data:
                break
            destination.sendall(data)
    except:
        pass
    finally:
        source.close()
        destination.close()

def handle_client(client_socket):
    print(f"Connection from {client_socket.getpeername()}")
    try:
        # Connect to AS400
        as400_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        as400_socket.connect((AS400_HOST, AS400_PORT))
        print(f"Connected to AS400")
        
        # Start forwarding
        threading.Thread(target=forward, args=(client_socket, as400_socket), daemon=True).start()
        threading.Thread(target=forward, args=(as400_socket, client_socket), daemon=True).start()
    except Exception as e:
        print(f"Error: {e}")
        client_socket.close()

def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", LISTEN_PORT))
    server.listen(5)
    print(f"AS400 Proxy listening on port {LISTEN_PORT}")
    print(f"Forwarding to {AS400_HOST}:{AS400_PORT}")
    
    try:
        while True:
            client, addr = server.accept()
            threading.Thread(target=handle_client, args=(client,), daemon=True).start()
    except KeyboardInterrupt:
        print("Shutting down...")
        server.close()

if __name__ == "__main__":
    main()
```

Run it:
```bash
python3 as400_proxy.py
```

Run it as a service:
```bash
# Create systemd service
sudo nano /etc/systemd/system/as400-proxy.service
```

```ini
[Unit]
Description=AS400 Database Proxy
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt
ExecStart=/usr/bin/python3 /opt/as400_proxy.py
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

---

## Option 3: Windows Proxy (If you have Windows server)

PowerShell script `AS400-Proxy.ps1`:

```powershell
$AS400_IP = "172.19.237.6"
$AS400_PORT = 8471
$LISTEN_PORT = 8471

$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $LISTEN_PORT)
$listener.Start()
Write-Host "AS400 Proxy listening on port $LISTEN_PORT"
Write-Host "Forwarding to $AS400_IP`:$AS400_PORT"

while ($true) {
    $client = $listener.AcceptTcpClient()
    Write-Host "Connection from $($client.Client.RemoteEndPoint)"
    
    Start-Job -ScriptBlock {
        param($client, $AS400_IP, $AS400_PORT)
        try {
            $as400 = New-Object System.Net.Sockets.TcpClient($AS400_IP, $AS400_PORT)
            $clientStream = $client.GetStream()
            $as400Stream = $as400.GetStream()
            
            # Forward both directions
            $clientStream.CopyToAsync($as400Stream)
            $as400Stream.CopyToAsync($clientStream)
        }
        catch {
            Write-Host "Error: $_"
        }
        finally {
            $client.Close()
            $as400.Close()
        }
    } -ArgumentList $client, $AS400_IP, $AS400_PORT
}
```

Run as Windows Service using NSSM:
```powershell
# Download NSSM from nssm.cc
nssm install AS400Proxy powershell -ExecutionPolicy Bypass -File "C:\path\to\AS400-Proxy.ps1"
nssm start AS400Proxy
```

---

## Network Configuration

### If Proxy Has Public IP:
Just configure firewall to allow port 8471 from specific IPs

### If Behind Router (Port Forward):
1. Forward external port 8471 → proxy machine port 8471
2. Use your router's public IP or domain
3. Update Render environment variable

### Security (Important!):
```bash
# Only allow Render's IP ranges
# Find Render IPs from their documentation or contact support

# iptables example:
sudo iptables -A INPUT -p tcp --dport 8471 -s RENDER_IP_RANGE -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 8471 -j DROP
```

---

## Update Render Environment Variables

```bash
# In Render Dashboard → Environment:
SPRING_DATASOURCE_URL=jdbc:as400://YOUR_PROXY_PUBLIC_IP_OR_DOMAIN:8471;prompt=false;naming=sql;errors=full;keepAlive=true;loginTimeout=60
SPRING_DATASOURCE_USERNAME=msecofr
SPRING_DATASOURCE_PASSWORD=CDP0326
```

---

## Testing

### 1. Test proxy locally:
```bash
telnet YOUR_PROXY_IP 8471
```

### 2. Test from outside:
```bash
telnet YOUR_PUBLIC_IP 8471
```

### 3. Test from Render:
Visit: `https://mimw.onrender.com/health/db`

---

## Comparison: Proxy vs Tailscale

| Feature | Simple Proxy | Tailscale |
|---------|--------------|-----------|
| Setup Time | 5 minutes | 30 minutes |
| AS400 Changes | None | None |
| Network Changes | Minimal (1 port forward) | Install client |
| Security | Firewall only | End-to-end encryption |
| Cost | $0 (use existing server) | $0 (free tier) |
| Maintenance | Low | Low |
| Encrypted | No (unless you add SSL) | Yes |

---

## Which Should You Use?

### Use Simple Proxy If:
- ✅ You want the absolute simplest solution
- ✅ You have a Linux/Windows machine in your network
- ✅ You can port forward or assign public IP
- ✅ You're okay with firewall-only security
- ✅ You want it done in 5 minutes

### Use Tailscale If:
- ✅ You want encryption
- ✅ You want zero-trust security
- ✅ You don't want to manage firewall rules
- ✅ You want audit logs

---

## What You DON'T Need to Do:

- ❌ Install anything on AS400
- ❌ Change AS400 configuration
- ❌ Restart AS400
- ❌ Modify AS400 firewall
- ❌ Touch AS400 at all!

## What You DO Need to Do:

- ✅ Run a tiny proxy on ANY computer in your network (not AS400)
- ✅ Give that proxy a public IP or port forward
- ✅ Update one environment variable on Render

**Total time: 5-10 minutes**

---

## Ready-to-Copy Commands

### Quick Setup (Ubuntu/Debian):
```bash
# 1. Install socat
sudo apt-get update && sudo apt-get install -y socat

# 2. Test it (run in foreground first)
socat TCP-LISTEN:8471,fork,reuseaddr TCP:172.19.237.6:8471

# 3. If it works, make it permanent
sudo tee /etc/systemd/system/as400-proxy.service > /dev/null <<EOF
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

# 4. Enable and start
sudo systemctl enable as400-proxy
sudo systemctl start as400-proxy
sudo systemctl status as400-proxy
```

### Check it's working:
```bash
# From the proxy machine:
telnet 172.19.237.6 8471

# From anywhere:
telnet YOUR_PROXY_PUBLIC_IP 8471
```

### Render Update:
Update this one variable:
```
SPRING_DATASOURCE_URL=jdbc:as400://YOUR_PROXY_PUBLIC_IP:8471;prompt=false;naming=sql;errors=full;keepAlive=true;loginTimeout=60
```

**Done!** 🎉

