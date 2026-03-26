# Implementation Checklist - Tailscale VPN on Render

Use this checklist to track your progress setting up Tailscale to connect Render to your private AS400 database.

## Prerequisites ✓

- [ ] Render account with your mimw application deployed
- [ ] Access to a server/computer in your AS400 network (where you can install software)
- [ ] AS400 IP address: `172.19.237.6` (confirmed)
- [ ] AS400 JDBC port: `8471` (default for JT400)

---

## Phase 1: Tailscale Account Setup (5 mins)

- [ ] Go to https://tailscale.com
- [ ] Sign up for free account
- [ ] Verify your email
- [ ] Log in to admin console: https://login.tailscale.com/admin

**Status:** ⬜ Not Started | ⬜ In Progress | ⬜ Complete

---

## Phase 2: Install Tailscale in Your Network (15 mins)

### Choose your platform:

#### Option A: Linux Server in AS400 Network
- [ ] SSH into a Linux server in your network (must have access to 172.19.237.6)
- [ ] Run: `curl -fsSL https://tailscale.com/install.sh | sh`
- [ ] Run: `sudo tailscale up --advertise-routes=172.19.237.0/24 --accept-routes`
- [ ] Note the "This device" name shown in terminal
- [ ] Go to Tailscale admin console → Machines
- [ ] Find your device and click "Review route"
- [ ] Approve the subnet route `172.19.237.0/24`

#### Option B: Windows Server/PC in AS400 Network
- [ ] Download Tailscale from https://tailscale.com/download/windows
- [ ] Install and run Tailscale
- [ ] Right-click Tailscale tray icon → Settings
- [ ] Enable "Advertise subnet routes"
- [ ] Add route: `172.19.237.0/24`
- [ ] Go to Tailscale admin console → Machines
- [ ] Approve the subnet route

#### Option C: Docker Container in Your Network
- [ ] Create docker-compose.yml with Tailscale sidecar
- [ ] Configure subnet routing
- [ ] See `TAILSCALE_SETUP.md` for docker-specific instructions

### Verify Subnet Route
- [ ] In Tailscale admin console, verify device shows "Subnet routes: 172.19.237.0/24"
- [ ] Status should be "Approved"

**Status:** ⬜ Not Started | ⬜ In Progress | ⬜ Complete

---

## Phase 3: Generate Auth Key (2 mins)

- [ ] Go to https://login.tailscale.com/admin/settings/keys
- [ ] Click "Generate auth key"
- [ ] Check these options:
  - ✅ Reusable
  - ✅ Ephemeral (optional - for auto-cleanup)
  - ✅ Pre-authorized
- [ ] Set expiration: 90 days (or longer)
- [ ] Click "Generate key"
- [ ] Copy the key (starts with `tskey-auth-...`)
- [ ] Save it securely (you'll need it in Phase 4)

**Key saved in:** ___________________________

**Status:** ⬜ Not Started | ⬜ In Progress | ⬜ Complete

---

## Phase 4: Update Your Render Deployment (20 mins)

### A. Update Dockerfile
- [ ] Review `Dockerfile.tailscale` (already created for you)
- [ ] Option 1: Rename `Dockerfile.tailscale` to `Dockerfile`
  ```bash
  mv Dockerfile Dockerfile.original
  mv Dockerfile.tailscale Dockerfile
  ```
- [ ] Option 2: Configure Render to use `Dockerfile.tailscale`
  - Render Dashboard → Settings → Docker Command
  - Set Dockerfile path: `Dockerfile.tailscale`

### B. Verify start.sh
- [ ] Confirm `start.sh` exists in project root
- [ ] Verify it's executable (it should be)
- [ ] Commit both files to git if you renamed:
  ```bash
  git add Dockerfile start.sh
  git commit -m "Add Tailscale VPN support for AS400 connection"
  ```

### C. Set Environment Variables on Render
- [ ] Go to Render Dashboard → Your Service → Environment
- [ ] Add/Update these variables:

```
TAILSCALE_AUTHKEY = tskey-auth-xxxxxxxxxxxxxxxxxxxxxxx
SPRING_DATASOURCE_URL = jdbc:as400://172.19.237.6;prompt=false;naming=sql;errors=full;keepAlive=true;loginTimeout=60
SPRING_DATASOURCE_USERNAME = msecofr
SPRING_DATASOURCE_PASSWORD = CDP0326
```

- [ ] Click "Save Changes"

### D. Deploy
- [ ] Trigger manual deploy or push to git
- [ ] Wait for deployment to complete (~5-10 minutes)

**Status:** ⬜ Not Started | ⬜ In Progress | ⬜ Complete

---

## Phase 5: Verify Connection (10 mins)

### A. Check Render Logs
- [ ] Go to Render Dashboard → Logs
- [ ] Look for these messages:
  ```
  ✓ Starting Tailscale daemon...
  ✓ Connecting to Tailscale network...
  ✓ Tailscale connected successfully!
  ✓ AS400 is reachable on port 8471
  ✓ Starting Spring Boot Application
  ```
- [ ] If you see "Cannot reach AS400", check Phase 2 (subnet routes)

**Logs Status:**
- Tailscale started: ⬜ Yes | ⬜ No | ⬜ Error
- AS400 reachable: ⬜ Yes | ⬜ No | ⬜ Error
- App started: ⬜ Yes | ⬜ No | ⬜ Error

### B. Check Tailscale Admin Console
- [ ] Go to https://login.tailscale.com/admin/machines
- [ ] Look for a device named `mimw-render-XXXXXXXXXX`
- [ ] Status should be "Connected"
- [ ] IP should be in 100.x.x.x range

**Device visible:** ⬜ Yes | ⬜ No

### C. Test Health Endpoint
- [ ] Open browser or use curl:
  ```bash
  curl https://mimw.onrender.com/health/db
  ```
- [ ] Expected response:
  ```json
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

**Health check:** ⬜ Pass | ⬜ Fail | ⬜ Error

### D. Test Your Application
- [ ] Try your actual app endpoints (e.g., `/api/taxpayer-types`)
- [ ] Verify data is returned from AS400
- [ ] Check that all features work as expected

**Application works:** ⬜ Yes | ⬜ No | ⬜ Partially

**Status:** ⬜ Not Started | ⬜ In Progress | ⬜ Complete

---

## Phase 6: Security & Monitoring (Optional but Recommended)

### A. Secure Your Tailscale Account
- [ ] Enable 2FA on your Tailscale account
- [ ] Set up ACLs (Access Control Lists) to restrict access
- [ ] Review connected devices regularly

### B. Set Up Monitoring
- [ ] Enable Render notifications for deployment failures
- [ ] Set up uptime monitoring (e.g., UptimeRobot)
- [ ] Configure AS400 audit logging
- [ ] Review Tailscale activity logs weekly

### C. Document for Your Team
- [ ] Share Tailscale credentials with team (securely)
- [ ] Document troubleshooting steps
- [ ] Create runbook for common issues
- [ ] Schedule auth key rotation (before expiry)

**Status:** ⬜ Not Started | ⬜ In Progress | ⬜ Complete

---

## Troubleshooting Checklist

If something doesn't work, check these:

### Render Logs Show "Cannot reach AS400"
- [ ] Verify subnet route is approved in Tailscale admin
- [ ] Check that Tailscale device in your network is online
- [ ] Verify AS400 IP is correct: `172.19.237.6`
- [ ] Test from Tailscale device: `telnet 172.19.237.6 8471`

### Tailscale Won't Connect
- [ ] Check auth key is correct and not expired
- [ ] Verify `TAILSCALE_AUTHKEY` environment variable is set
- [ ] Look for error messages in logs
- [ ] Generate new auth key and try again

### Application Starts but DB Connection Fails
- [ ] Check database credentials are correct
- [ ] Verify password format: `CDP0326` (March 2026)
- [ ] Test connection from Tailscale device in your network
- [ ] Check AS400 database is running

### Container Fails to Start
- [ ] Check Dockerfile syntax
- [ ] Verify start.sh has execute permissions
- [ ] Check for build errors in Render logs
- [ ] Try building locally first: `docker build -f Dockerfile.tailscale -t test .`

---

## Success Criteria ✓

You're done when ALL these are true:

- [✓] Tailscale device in your network is online
- [✓] Subnet route `172.19.237.0/24` is approved
- [✓] Render app shows "Tailscale connected successfully" in logs
- [✓] `/health/db` endpoint returns `"connected": true`
- [✓] Your application endpoints return data from AS400
- [✓] Everything works for at least 24 hours without issues

---

## Quick Reference

### Important URLs
- Tailscale Admin: https://login.tailscale.com/admin
- Render Dashboard: https://dashboard.render.com
- Your App: https://mimw.onrender.com
- Health Check: https://mimw.onrender.com/health/db

### Important Files
- `TAILSCALE_SETUP.md` - Detailed setup guide
- `SOLUTION_COMPARISON.md` - Compare alternatives
- `Dockerfile.tailscale` - Your Dockerfile with Tailscale
- `start.sh` - Startup script

### Support
- Tailscale Docs: https://tailscale.com/kb/
- Render Docs: https://render.com/docs
- JT400 Docs: http://jt400.sourceforge.net/

---

## Estimated Time

- ⏱️ Phase 1: 5 minutes
- ⏱️ Phase 2: 15 minutes
- ⏱️ Phase 3: 2 minutes
- ⏱️ Phase 4: 20 minutes
- ⏱️ Phase 5: 10 minutes
- ⏱️ Phase 6: 30 minutes (optional)

**Total: ~1 hour** (2 hours with optional security setup)

---

## Notes

Use this space to track issues, solutions, or important information:

```
Date: _______________

Issue: 

Solution: 


Date: _______________

Issue: 

Solution: 
```

---

**Good luck! You've got this! 🚀**

