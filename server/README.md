# Musik Together server

A room server for the Musik Android app. Each phone plays the same online track through its own player. This server shares room codes, queues, play/pause, seek position, participant lists, and clock samples over WebSockets; it does not download or redistribute audio. The server must run continuously while a room is active.

Use this updated server with the Musik Jam room screen. If you ran the earlier server package, replace its files and restart it; older servers required a token for guests. Keep your existing `.env` file.

## Run on your computer

Install Node.js 22.9 or newer. From PowerShell in the project root:

```powershell
cd server
npm.cmd ci
Copy-Item .env.example .env
node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))"
```

Copy the generated value into `MUSIK_SERVER_TOKEN` in `.env`, replacing the example. Then start the server:

```powershell
npm.cmd start
```

Keep this terminal running. Stop with Ctrl+C. Room state is in memory: restarting the server ends all rooms.

Find the computer's Wi-Fi IPv4 address with `ipconfig` (for example `192.168.1.10`). Connect the phones and computer to the same network and allow the Node.js server through your firewall on the private network if prompted. Phones must be able to reach TCP port 8787; guest Wi-Fi/client isolation can block this.

## Connect the phones

1. Install the updated Musik APK on every phone.
2. On the host, open **Together → Custom Server Settings → Server settings**.
3. Enter your server URL, for example `http://192.168.1.10:8787`, and the token from `.env`. Use your computer's actual address. `localhost` on a phone means the phone itself. This is a one-time host setup.
4. Select **Create Room**, enter a display name, choose **Collaborative** or **Host Only**, and tap **Start Jam Session**.
5. Tap **Share Invite**. The shared HTTP/HTTPS URL opens a small invite page with **Open Musik**. Guests can also paste that URL into **Join Room**; it supplies the server address and six-character room code. Guests do not need the server token. To join by code alone, guests first save the same server URL under Custom Server Settings (leave the token blank).
6. Play an online track on the host. The live room shows its shared queue and listeners. In Collaborative mode anyone can control playback and add tracks; Host Only reserves those actions for the host. The host can change the mode during the session.

Keep the host connected. Leaving or disconnecting the host closes the room; create a new room to reconnect. Pending guests receive playback only after approval. Rooms expire after six hours, allow up to 32 phones, and the server accepts up to 100 rooms by default.

The **Direct LAN connection** option under Custom Server Settings also works without this relay when phones can connect directly to the hosting phone.

## Different Wi-Fi networks or mobile data

The computer's private address only works on the same reachable network. For remote listening, run the server on an internet-reachable host and put it behind an HTTPS reverse proxy with WebSocket support. Set `MUSIK_PUBLIC_URL` to its public HTTPS origin and enter that URL on the host. Shared invites include it for guests.

For example, Caddy can terminate TLS and proxy to a locally running server:

```caddyfile
music.example.com {
    reverse_proxy 127.0.0.1:8787
}
```

Use a domain you control, point its DNS to your server, and allow the required HTTP/HTTPS ports. Configure:

```dotenv
MUSIK_PUBLIC_URL=https://music.example.com
MUSIK_SERVER_TOKEN=your-random-server-token
```

The URL is an origin only (no path/query/credentials). The proxy must preserve `/v1/together/*` and support WebSocket upgrades. HTTPS is used as WSS by the app. Do not use unencrypted public HTTP with the server token.

Optional Docker setup:

```sh
docker build -t musik-together .
docker run --rm --env-file .env -p 127.0.0.1:8787:8787 musik-together
```

That Docker port mapping is for a reverse proxy on the same machine. For LAN access, publish to the appropriate network interface instead.

## Limits and troubleshooting

- All devices need access to the online track. Local imported files are not uploaded or shared. Start an online queue rather than a local-file queue.
- Clock estimates and periodic position correction reduce drift. This is synchronized listening, not sample-accurate multi-speaker output; buffering, Android audio paths, and Bluetooth add different delays.
- The server token protects room creation. Guests resolve a room using its code and authenticate WebSockets with a room-scoped key. Share invites only with intended listeners; they grant room access. The host token is never included in an invite or sent during guest joining. Keep `.env` private and use a trusted LAN or HTTPS.
- `GET /health` returns the service and protocol version without requiring a token. Open `http://COMPUTER_IP:8787/health` on a phone to check reachability.
- **Unauthorized:** token mismatch. **Room not found:** wrong server/code or disconnected host. **Connection refused/timed out:** server stopped, wrong address, firewall, or isolated Wi-Fi.
- Changing server settings requires leaving the current session first.

## Test

```powershell
npm.cmd test
```

The integration tests start a temporary relay and exercise authenticated room creation, token-free guest code resolution, two WebSocket clients, queue and playback updates, seek requests, permission enforcement, clock samples, join approval, banning, malformed messages, local-file rejection, and host-disconnect cleanup.

This is a Musik-specific protocol. It does not connect to BeatSync rooms or use BeatSync's servers.
