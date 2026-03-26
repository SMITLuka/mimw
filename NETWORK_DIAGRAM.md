# Network Diagram - Simple Proxy Solution

## Current Situation (Not Working)
```
┌─────────────────────────────────────────────────────────────────┐
│                       PUBLIC INTERNET                            │
│                                                                   │
│   ┌─────────────────┐                                           │
│   │  Render.com     │                                           │
│   │  mimw app       │   ❌ Cannot reach private IP              │
│   └────────┬────────┘                                           │
│            │                                                      │
│            │ Tries to connect to 172.19.237.6                   │
│            │ (FAILS - private IP not routable)                  │
│            ▼                                                      │
│            ✗                                                      │
└─────────────────────────────────────────────────────────────────┘
                    
                    │ FIREWALL/ROUTER
                    │
┌─────────────────────────────────────────────────────────────────┐
│                     YOUR PRIVATE NETWORK                         │
│                                                                   │
│              ┌──────────────────┐                               │
│              │   AS400 Server   │                               │
│              │  172.19.237.6    │                               │
│              │   Port: 8471     │                               │
│              └──────────────────┘                               │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Solution: Simple Proxy (Working)
```
┌─────────────────────────────────────────────────────────────────┐
│                       PUBLIC INTERNET                            │
│                                                                   │
│   ┌─────────────────┐                                           │
│   │  Render.com     │                                           │
│   │  mimw app       │                                           │
│   └────────┬────────┘                                           │
│            │                                                      │
│            │ jdbc:as400://YOUR_PUBLIC_IP:8471                   │
│            │                                                      │
│            ▼                                                      │
│            ✓ Connects successfully                               │
└─────────────┼─────────────────────────────────────────────────────┘
              │
              │ Port 8471
              │
┌─────────────▼─────────────────────────────────────────────────────┐
│         FIREWALL/ROUTER (Port Forward)                            │
│         External :8471 → 192.168.1.100:8471                      │
└─────────────┬─────────────────────────────────────────────────────┘
              │
              │
┌─────────────▼─────────────────────────────────────────────────────┐
│                     YOUR PRIVATE NETWORK                          │
│                                                                    │
│   ┌───────────────────────┐          ┌──────────────────┐       │
│   │  Proxy Machine        │          │   AS400 Server   │       │
│   │  192.168.1.100        │ forwards │  172.19.237.6    │       │
│   │                       │─────────▶│   Port: 8471     │       │
│   │  Docker/socat running │          │                  │       │
│   │  Port: 8471           │          │  NO CHANGES! ✓   │       │
│   └───────────────────────┘          └──────────────────┘       │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

---

## Data Flow
```
1. Render App sends SQL query
   ↓
2. Goes to YOUR_PUBLIC_IP:8471
   ↓
3. Router forwards to Proxy (192.168.1.100:8471)
   ↓
4. Proxy forwards to AS400 (172.19.237.6:8471)
   ↓
5. AS400 processes query
   ↓
6. Response goes back through proxy
   ↓
7. Render App receives result
```

---

## What's Changed?
```
Component              Before          After           Changed?
─────────────────────────────────────────────────────────────
AS400 Server           Running         Running         ❌ No
AS400 Config           Default         Default         ❌ No
AS400 Firewall         Default         Default         ❌ No

Proxy Machine          N/A             Running socat   ✅ Yes
Router                 Default         Port forward    ✅ Yes
Render App URL         172.19.237.6    YOUR_PUBLIC_IP  ✅ Yes
```

---

## Security Layers
```
┌─────────────────────────────────────────────────────────────────┐
│ Layer 1: Router Firewall (blocks unwanted IPs)                  │
├─────────────────────────────────────────────────────────────────┤
│ Layer 2: Port Forward (only port 8471)                          │
├─────────────────────────────────────────────────────────────────┤
│ Layer 3: Proxy Machine Firewall (allow only Render IP)          │
├─────────────────────────────────────────────────────────────────┤
│ Layer 4: AS400 Authentication (username/password)               │
└─────────────────────────────────────────────────────────────────┘
```

---

## Example Setup

### Your Network Details (Example):
```
Public IP: 203.0.113.50
Router: 192.168.1.1
Proxy Machine: 192.168.1.100
AS400: 172.19.237.6
```

### Router Configuration:
```
Port Forwarding Rule:
┌────────────────────────────────────────┐
│ External Port:    8471                 │
│ Internal IP:      192.168.1.100        │
│ Internal Port:    8471                 │
│ Protocol:         TCP                  │
│ Description:      AS400 Proxy          │
└────────────────────────────────────────┘
```

### Proxy Machine Command:
```bash
docker run -d --name as400-proxy --restart unless-stopped \
  -p 8471:8471 \
  alpine/socat \
  tcp-listen:8471,fork,reuseaddr tcp-connect:172.19.237.6:8471
```

### Render Environment Variable:
```
SPRING_DATASOURCE_URL=jdbc:as400://203.0.113.50:8471;prompt=false;naming=sql;errors=full;keepAlive=true;loginTimeout=60
```

---

## Comparison: Before vs After

### Before (Not Working):
```
Render ──X──> 172.19.237.6 (Private IP - unreachable)
```

### After (Working):
```
Render ──✓──> 203.0.113.50:8471 (Public IP)
              ↓
         Router forwards to proxy
              ↓
         Proxy ──✓──> 172.19.237.6:8471 (AS400)
```

---

## Alternative: Tailscale (For Comparison)

```
┌─────────────────────────────────────────────────────────────────┐
│                       PUBLIC INTERNET                            │
│                                                                   │
│   ┌─────────────────┐          ┌──────────────────┐            │
│   │  Render.com     │          │   Tailscale      │            │
│   │  + Tailscale    │═════════▶│   Coordination   │            │
│   │  Client         │  VPN     │   Server         │            │
│   └────────┬────────┘          └──────────────────┘            │
│            ║                                                     │
│            ║ Encrypted Tunnel                                   │
│            ║                                                     │
└────────────╬─────────────────────────────────────────────────────┘
             ║
             ║ Encrypted
             ║
┌────────────╬─────────────────────────────────────────────────────┐
│            ▼                                                      │
│   ┌───────────────────────┐          ┌──────────────────┐      │
│   │  Machine in Network   │          │   AS400 Server   │      │
│   │  + Tailscale Client   │          │  172.19.237.6    │      │
│   │  + Subnet Routing     │─────────▶│   Port: 8471     │      │
│   └───────────────────────┘          └──────────────────┘      │
│                                                                   │
└───────────────────────────────────────────────────────────────────┘

Note: More secure but requires more setup
```

---

## Quick Decision Tree

```
Do you want maximum security?
│
├─ YES → Use Tailscale (see TAILSCALE_SETUP.md)
│         + Encrypted
│         + Zero-trust
│         + No port forwarding
│         - More setup
│
└─ NO  → Use Simple Proxy (see QUICKSTART.md)
          + 5 minute setup
          + One command
          + No complexity
          - Need firewall rules for security
```

---

## Monitoring Your Setup

```
┌──────────────────────────────────────────────────────────────┐
│                        HEALTH CHECK                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Test Proxy:     telnet YOUR_PUBLIC_IP 8471                │
│                                                              │
│  Test App:       curl https://mimw.onrender.com/health/db   │
│                                                              │
│  Check Logs:     docker logs -f as400-proxy                 │
│                                                              │
│  Monitor:        Watch Render logs for DB connections       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## Summary

**Simple Proxy Solution:**
- ✅ No AS400 changes
- ✅ One small proxy
- ✅ Port forward on router
- ✅ Update Render URL
- ✅ 5-10 minutes setup
- ✅ $0 cost

**Result:** Render ⟷ Public IP ⟷ Proxy ⟷ AS400 ✓

