# Connecting to Private AS400 Database from Render

## Quick Answer: Can VPN be Set Up on Render?

**Short answer:** Render does **NOT** support native VPN connections to external private networks.

**BUT** you can work around this using:
1. ✅ **Tailscale/ZeroTier** - Mesh VPN clients running inside your Docker container (Recommended)
2. ✅ **Wireguard** - VPN client in your container
3. ✅ **Public IP** - Expose AS400 with proper firewall rules
4. ✅ **Database Proxy** - Public-facing proxy that forwards to private AS400

## The Problem

Your application on Render (https://mimw.onrender.com) cannot connect to AS400 at `172.19.237.6` because:

- **172.19.237.6 is a private IP** (only accessible within your local network)
- Render services run on the public internet
- Render doesn't offer site-to-site VPN to external networks

## Recommended Solution: Tailscale

**Tailscale** is a mesh VPN that can run inside your Docker container and connect your Render deployment to your private network.

### Files Created for You

1. **`TAILSCALE_SETUP.md`** - Complete step-by-step guide to set up Tailscale
2. **`Dockerfile.tailscale`** - Updated Dockerfile with Tailscale support
3. **`start.sh`** - Startup script that initializes Tailscale before your app
4. **`NETWORK_CONFIGURATION.md`** - Detailed explanation of the problem and all solutions

### Quick Start (5 Steps)

1. **Sign up for Tailscale** (free): https://tailscale.com
2. **Install Tailscale in your AS400 network** (on any server/router):
   ```bash
   curl -fsSL https://tailscale.com/install.sh | sh
   sudo tailscale up --advertise-routes=172.19.237.0/24
   ```
3. **Generate an auth key** from Tailscale admin console
4. **Update your Dockerfile** on Render:
   - Replace `Dockerfile` with contents of `Dockerfile.tailscale`
   - Or configure Render to use `Dockerfile.tailscale`
5. **Add environment variable** on Render:
   ```
   TAILSCALE_AUTHKEY=tskey-auth-xxxxxxxxxxxxxxxxxx
   ```

That's it! Your Render app will connect to Tailscale, and then access AS400 through the encrypted VPN tunnel.

## Alternative Solutions

### Option 1: Expose AS400 with Public IP
If your AS400 can have a public IP:
```bash
# On Render, set:
SPRING_DATASOURCE_URL=jdbc:as400://YOUR_PUBLIC_IP;prompt=false;naming=sql;errors=full
```

### Option 2: Database Proxy
Run a proxy server in your network with a public IP that forwards to AS400.

### Option 3: Deploy Elsewhere
Consider cloud providers with native VPN support:
- **AWS** - VPN, Direct Connect, PrivateLink
- **Azure** - VPN Gateway, ExpressRoute
- **Google Cloud** - Cloud VPN, Interconnect
- **DigitalOcean** - VPN support with Droplets

## Testing Your Connection

After deploying with Tailscale:

1. **Check Render logs** for:
   ```
   ✓ Tailscale connected successfully!
   ✓ AS400 is reachable on port 8471
   ```

2. **Test the health endpoint**:
   ```bash
   curl https://mimw.onrender.com/health/db
   ```

3. **Check Tailscale admin console** to see your Render instance online

## Security Notes

✅ **With Tailscale:**
- All traffic is encrypted end-to-end
- No need to expose AS400 to public internet
- Can use ACLs to restrict access
- Audit logs of all connections

⚠️ **With Public IP:**
- Must implement strict firewall rules
- Use IP whitelisting (Render's static IP feature)
- Enable AS400's security features
- Monitor for unauthorized access attempts

## Cost Comparison

| Solution | Monthly Cost | Setup Complexity |
|----------|--------------|------------------|
| Tailscale (free tier) | $0 | Medium |
| Tailscale (paid) | $6/user | Medium |
| Render Static IP | $7-20 | Low |
| Public IP + Firewall | Varies | High |
| Database Proxy | Server cost | High |
| AWS with VPN | $50+ | High |

## What Render Actually Supports

❌ **Does NOT support:**
- Native site-to-site VPN to external networks
- AWS PrivateLink / Azure Private Link equivalents
- Direct connection to on-premises networks
- VPN gateway services

✅ **Does support:**
- Outbound connections to any public IP
- Private networking between Render services (internal only)
- Static IP addresses (paid feature)
- Docker containers (where you can install VPN clients)
- Custom Dockerfiles with any software

## Need Help?

1. **Read the guides:**
   - `TAILSCALE_SETUP.md` - Complete Tailscale setup
   - `NETWORK_CONFIGURATION.md` - All solutions explained

2. **Check logs:**
   - Render dashboard → Your service → Logs
   - Look for "Tailscale" messages
   - Check for "AS400 is reachable" confirmation

3. **Test locally first:**
   ```bash
   docker build -f Dockerfile.tailscale -t mimw-test .
   docker run -e TAILSCALE_AUTHKEY=xxx -e SPRING_DATASOURCE_PASSWORD=CDP0326 mimw-test
   ```

4. **Common issues:**
   - Tailscale auth key expired → Generate a new reusable key
   - Subnet routes not approved → Check Tailscale admin console
   - Container lacks permissions → Use `--tun=userspace-networking` (already in start.sh)

## Summary

**Can you set up VPN on Render?**
- Not natively, but **YES with Tailscale/Wireguard running in your container**
- This is the recommended approach for connecting to private networks
- Takes ~30 minutes to set up
- Works reliably and securely

**Next Step:** Follow the guide in `TAILSCALE_SETUP.md`

