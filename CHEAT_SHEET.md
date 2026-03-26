# 🚀 ONE-PAGE CHEAT SHEET - Simple Proxy Solution

## The Problem
❌ Render can't reach AS400 at 172.19.237.6 (private IP)

## The Solution
✅ Run a tiny proxy in your network that forwards to AS400

---

## 3 Commands to Success

### 1️⃣ Run Proxy (any machine in your network)
```bash
docker run -d --name as400-proxy --restart unless-stopped \
  -p 8471:8471 alpine/socat \
  tcp-listen:8471,fork,reuseaddr tcp-connect:172.19.237.6:8471
```

### 2️⃣ Forward Port (on your router)
```
External: 8471 → Internal: [proxy-machine-ip]:8471
```

### 3️⃣ Update Render (environment variable)
```
SPRING_DATASOURCE_URL=jdbc:as400://YOUR_PUBLIC_IP:8471;prompt=false;naming=sql;errors=full;keepAlive=true;loginTimeout=60
```

---

## Quick Test Commands

```bash
# Test proxy works
telnet YOUR_PUBLIC_IP 8471

# Test app works
curl https://mimw.onrender.com/health/db

# Check proxy logs
docker logs -f as400-proxy
```

---

## If Docker Not Available

### Linux:
```bash
sudo apt install -y socat
socat TCP-LISTEN:8471,fork,reuseaddr TCP:172.19.237.6:8471
```

### Windows:
See `QUICKSTART.md` for PowerShell script

---

## Security (After Setup)

```bash
# Firewall - allow only Render IP
sudo ufw allow from RENDER_IP to any port 8471
sudo ufw enable
```

Or get Render Static IP addon ($7-20/month)

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Port in use | `sudo kill -9 $(sudo lsof -t -i:8471)` |
| Can't connect | Check port forwarding |
| Slow | Check network/AS400 status |
| Errors | `docker logs as400-proxy` |

---

## File Guide

- **Quick start:** `QUICKSTART.md`
- **Full guide:** `FINAL_SOLUTION.md`
- **Diagrams:** `NETWORK_DIAGRAM.md`
- **All files:** `README_SOLUTION.md`

---

## What Changed?

✅ Your code (already updated by me)  
✅ Added `/health/db` endpoint  
✅ Environment variable support  
✅ Better error handling  

❌ AS400 (nothing!)  

---

## Time & Cost

⏱️ **Setup:** 5-10 minutes  
💰 **Cost:** $0 (or $7-20 for static IP)  
🔧 **Maintenance:** None (auto-restart)  

---

## Success Check

Visit: `https://mimw.onrender.com/health/db`

Should return:
```json
{"success": true, "message": "Database connection OK"}
```

✅ **You're done!**

---

**Need help?** Read `QUICKSTART.md` or `FINAL_SOLUTION.md`

