import http from 'node:http';
import { randomBytes, randomInt, randomUUID, timingSafeEqual } from 'node:crypto';
import { performance } from 'node:perf_hooks';
import { pathToFileURL } from 'node:url';
import { WebSocket, WebSocketServer } from 'ws';

const clock = () => Math.floor(performance.now());
const key = () => randomBytes(32).toString('base64url');
const settingsOf = (value = {}) => ({
  allowGuestsToAddTracks: value.allowGuestsToAddTracks !== false,
  allowGuestsToControlPlayback: value.allowGuestsToControlPlayback === true,
  requireHostApprovalToJoin: value.requireHostApprovalToJoin === true,
});
const safeTrack = t => t && typeof t.id === 'string' && /^[a-zA-Z0-9_-]{1,128}$/.test(t.id) &&
  typeof t.title === 'string' && t.title.length <= 1000 &&
  (t.durationSec === undefined || (Number.isSafeInteger(t.durationSec) && t.durationSec >= -1)) &&
  (t.thumbnailUrl == null || (typeof t.thumbnailUrl === 'string' && t.thumbnailUrl.length <= 4000 && /^https?:\/\//.test(t.thumbnailUrl))) &&
  (t.artists === undefined || (Array.isArray(t.artists) && t.artists.length <= 30 && t.artists.every(a => typeof a === 'string' && a.length <= 500)));

export function createRelay({ token, publicUrl, maxRooms = 100, maxParticipants = 32, roomTtlMs = 6 * 60 * 60 * 1000 } = {}) {
  if (typeof token !== 'string' || token.length < 16 || token === 'replace-this-with-a-long-random-token') throw Error('Set MUSIK_SERVER_TOKEN to a random value of at least 16 characters.');
  const publicOrigin = publicUrl ? new URL(publicUrl) : null;
  if (publicOrigin && (!['http:', 'https:'].includes(publicOrigin.protocol) || publicOrigin.username || publicOrigin.password || publicOrigin.pathname !== '/' || publicOrigin.search || publicOrigin.hash)) {
    throw Error('MUSIK_PUBLIC_URL must be an HTTP(S) origin without a path, query or credentials.');
  }
  const rooms = new Map();
  const codes = new Map();
  const limits = new Map();
  const sockets = new Set();
  const authorized = req => {
    const supplied = Buffer.from(req.headers.authorization || '');
    const expected = Buffer.from(`Bearer ${token}`);
    return supplied.length === expected.length && timingSafeEqual(supplied, expected);
  };
  const send = (ws, msg) => {
    if (ws?.readyState === WebSocket.OPEN) {
      if (ws.bufferedAmount > 2 * 1024 * 1024) ws.close(1013, 'Slow connection');
      else ws.send(JSON.stringify(msg));
    }
  };
  const fail = (ws, room, message, code = 'invalid_request') => send(ws, { type: 'server_error', sessionId: room?.id ?? null, message, code });
  const participant = member => ({ id: member.id, name: member.name, isHost: member.host, isPending: member.pending, isConnected: true });
  const everyone = (room, message, includePending = false) => {
    for (const m of room.members.values()) if (includePending || !m.pending) send(m.ws, message);
  };
  const stateMessage = room => {
    if (!room.state) return null;
    const now = clock();
    return { type: 'room_state', state: { ...room.state,
      hostId: room.host?.id ?? '', participants: [...room.members.values()].map(participant), settings: room.settings,
      positionMs: room.state.positionMs + (room.state.isPlaying ? Math.max(0, now - room.state.sentAtElapsedRealtimeMs) : 0),
      sentAtElapsedRealtimeMs: now,
    } };
  };
  const broadcastState = room => { const msg = stateMessage(room); if (msg) everyone(room, msg); };
  const destroyRoom = (room, reason) => {
    if (!rooms.delete(room.id)) return;
    codes.delete(room.code);
    for (const m of room.members.values()) { fail(m.ws, room, reason, 'room_closed'); m.ws.close(1000, reason); }
    room.members.clear();
  };
  const removeMember = (room, member, reason) => {
    if (!room.members.delete(member.id)) return;
    if (member.host) { destroyRoom(room, 'Host disconnected. Create a new room to reconnect.'); return; }
    everyone(room, { type: 'participant_left', sessionId: room.id, participantId: member.id, reason }, true);
    broadcastState(room);
  };
  const json = (res, status, body) => {
    res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify(body));
  };
  const rateLimit = req => {
    const ip = req.socket.remoteAddress;
    const now = Date.now();
    const entry = limits.get(ip);
    if (!entry || now > entry.until) { limits.set(ip, { count: 1, until: now + 60_000 }); return false; }
    return ++entry.count > 60;
  };
  const server = http.createServer(async (req, res) => {
    const path = new URL(req.url, 'http://localhost').pathname;
    if (req.method === 'GET' && path === '/health') return json(res, 200, { ok: true, service: 'musik-together', protocolVersion: 1 });
    if (rateLimit(req)) return json(res, 429, { error: 'Too many requests. Try again in a minute.' });
    if (req.method === 'GET' && path === '/') {
      try {
        const code = new URL(req.url, 'http://localhost').searchParams.get('code')?.toUpperCase() || '';
        if (!/^[A-Z0-9]{6}$/.test(code)) return json(res, 200, { ok: true, service: 'musik-together', invite: 'Open a shared room invite or create a room in Musik.' });
        const origin = publicOrigin || new URL(`http://${req.headers.host}`);
        const appLink = `musik://jam?code=${code}&server=${encodeURIComponent(origin.origin)}`.replaceAll('&', '&amp;');
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store',
          'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'",
          'Referrer-Policy': 'no-referrer', 'X-Content-Type-Options': 'nosniff' });
        res.end(`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Join Musik Jam ${code}</title><style>body{margin:0;background:#141218;color:#eee;font:18px system-ui;display:grid;min-height:100vh;place-items:center}main{max-width:28rem;padding:2rem;text-align:center}h1{font-size:2rem}strong{display:block;font:700 3rem monospace;letter-spacing:.2em;margin:2rem 0;color:#d0bcff}a{display:block;background:#d0bcff;color:#211634;padding:1rem;border-radius:1rem;text-decoration:none;font-weight:700}p{line-height:1.6;color:#ccc}</style><main><h1>Musik Jam</h1><p>You are invited to listen together.</p><strong>${code}</strong><a href="${appLink}">Open Musik · Join Room</a><p>Install Musik from your host, then open this invite. You can also paste this page URL into Together → Join Room.</p></main></html>`);
      } catch { json(res, 400, { error: 'Invalid invite' }); }
      return;
    }
    if (path !== '/v1/together/sessions/resolve' && !authorized(req)) return json(res, 401, { error: 'Server access token does not match.' });
    if (req.method !== 'POST') return json(res, 404, { error: 'Not found' });
    try {
      let body = '';
      for await (const chunk of req) {
        body += chunk;
        if (Buffer.byteLength(body) > 16_384) return json(res, 413, { error: 'Request too large' });
      }
      const input = JSON.parse(body || '{}');
      if (!input || typeof input !== 'object' || Array.isArray(input)) return json(res, 400, { error: 'Invalid JSON object' });
      // An explicit public URL is required behind a reverse proxy; do not trust forwarded headers.
      const origin = publicOrigin || new URL(`http://${req.headers.host}`);
      const wsUrl = `${origin.protocol === 'https:' ? 'wss:' : 'ws:'}//${origin.host}/v1/together/ws`;
      if (path === '/v1/together/sessions') {
        if (rooms.size >= maxRooms) return json(res, 503, { error: 'Server room limit reached' });
        if (typeof input.hostDisplayName !== 'string' || !input.hostDisplayName.trim() || input.hostDisplayName.length > 100) {
          return json(res, 400, { error: 'Enter a display name (up to 100 characters)' });
        }
        let code;
        const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
        do { code = Array.from({ length: 6 }, () => alphabet[randomInt(alphabet.length)]).join(''); } while (codes.has(code));
        const room = { id: randomUUID(), code, hostKey: key(), guestKey: key(), settings: settingsOf(input.settings),
          members: new Map(), host: null, state: null, banned: new Set(), createdAt: Date.now(), hostDeadline: Date.now() + 60_000 };
        rooms.set(room.id, room); codes.set(code, room);
        return json(res, 201, { sessionId: room.id, code, hostKey: room.hostKey, guestKey: room.guestKey, wsUrl, settings: room.settings });
      }
      if (path === '/v1/together/sessions/resolve') {
        const room = codes.get(String(input.code ?? '').trim().toUpperCase());
        if (!room || !room.host) return json(res, 404, { error: 'Room not found. Check the code and that the host is connected.' });
        return json(res, 200, { sessionId: room.id, guestKey: room.guestKey, wsUrl, settings: room.settings });
      }
      json(res, 404, { error: 'Not found' });
    } catch { if (!res.headersSent) json(res, 400, { error: 'Invalid request' }); }
  });
  server.requestTimeout = 15_000;
  const wss = new WebSocketServer({ noServer: true, maxPayload: 1024 * 1024, perMessageDeflate: false });
  server.on('upgrade', (req, socket, head) => {
    // Guests authenticate using their room-scoped key in client_hello, not the server's host token.
    if (new URL(req.url, 'http://localhost').pathname !== '/v1/together/ws' ||
        (req.headers.authorization && !authorized(req)) || rateLimit(req) || sockets.size >= maxRooms * maxParticipants) {
      socket.end('HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n'); return;
    }
    wss.handleUpgrade(req, socket, head, ws => wss.emit('connection', ws));
  });
  wss.on('connection', ws => {
    sockets.add(ws);
    let room, member;
    let windowStart = Date.now(), messages = 0, alive = true;
    const helloTimeout = setTimeout(() => ws.close(1008, 'Join handshake timed out'), 10_000);
    ws.on('pong', () => { alive = true; });
    const ping = setInterval(() => { if (!alive) ws.terminate(); else { alive = false; ws.ping(); } }, 30_000);
    ping.unref();
    ws.on('error', () => {});
    ws.on('message', (raw, binary) => {
      try {
        if (Date.now() - windowStart > 1000) { windowStart = Date.now(); messages = 0; }
        if (++messages > 100 || binary) { ws.close(1008, 'Invalid message rate or format'); return; }
        const msg = JSON.parse(raw.toString());
        if (!msg || typeof msg !== 'object' || Array.isArray(msg)) return fail(ws, room, 'Invalid message');
        if (!member) {
          room = rooms.get(msg.sessionId);
          if (msg.type !== 'client_hello' || msg.protocolVersion !== 1 || !room ||
              ![room.hostKey, room.guestKey].includes(msg.sessionKey) || typeof msg.clientId !== 'string' ||
              !msg.clientId || msg.clientId.length > 128 || typeof msg.displayName !== 'string' || msg.displayName.length > 100 ||
              room.banned.has(msg.clientId) || room.members.size >= maxParticipants) {
            fail(ws, room, 'Unable to join room', 'join_rejected'); ws.close(1008, 'Join rejected'); return;
          }
          const host = msg.sessionKey === room.hostKey;
          if ((host && room.host) || (!host && !room.host)) { ws.close(1008, 'Host not available'); return; }
          clearTimeout(helloTimeout);
          member = { id: randomUUID(), clientId: msg.clientId, name: msg.displayName.trim() || 'Listener', host,
            pending: !host && room.settings.requireHostApprovalToJoin, ws };
          room.members.set(member.id, member);
          if (host) room.host = member;
          send(ws, { type: 'server_welcome', protocolVersion: 1, sessionId: room.id, participantId: member.id,
            role: host ? 'HOST' : 'GUEST', isPending: member.pending, settings: room.settings });
          if (!host) {
            send(room.host.ws, { type: member.pending ? 'join_request' : 'participant_joined', sessionId: room.id, participant: participant(member) });
            broadcastState(room);
          }
          return;
        }
        if (!rooms.has(room.id) || room.members.get(member.id) !== member) return;
        if (msg.type !== 'room_state' && msg.sessionId !== room.id) return fail(ws, room, 'Wrong session');
        if (msg.type === 'heartbeat_ping') {
          if (!Number.isSafeInteger(msg.pingId) || !Number.isSafeInteger(msg.clientElapsedRealtimeMs)) return;
          send(ws, { type: 'heartbeat_pong', sessionId: room.id, pingId: msg.pingId,
            clientElapsedRealtimeMs: msg.clientElapsedRealtimeMs, serverElapsedRealtimeMs: clock() }); return;
        }
        if (msg.type === 'client_leave') { removeMember(room, member, 'Left room'); ws.close(1000, 'Left room'); return; }
        if (member.host) {
          if (msg.type === 'room_state') {
            const s = msg.state;
            if (!s || s.sessionId !== room.id || !Array.isArray(s.queue) || s.queue.length > 1000 ||
                !s.queue.every(safeTrack) || !Number.isSafeInteger(s.positionMs) || s.positionMs < 0 ||
                !Number.isSafeInteger(s.currentIndex) || s.currentIndex < 0 || (s.queue.length > 0 && s.currentIndex >= s.queue.length) ||
                ![0,1,2].includes(s.repeatMode) || typeof s.isPlaying !== 'boolean' || typeof s.shuffleEnabled !== 'boolean') {
              return fail(ws, room, 'Only shared online tracks and valid playback positions are supported.');
            }
            room.settings = settingsOf(s.settings);
            room.state = { ...s, sentAtElapsedRealtimeMs: clock() };
            broadcastState(room); return;
          }
          if (['join_decision', 'kick', 'ban'].includes(msg.type)) {
            const guest = room.members.get(msg.participantId);
            if (!guest || guest.host) return;
            if (msg.type === 'join_decision' && !guest.pending) return;
            if (msg.type === 'join_decision' && msg.approved === true) {
              guest.pending = false;
              send(guest.ws, { type: 'join_decision', sessionId: room.id, participantId: guest.id, approved: true });
              everyone(room, { type: 'participant_joined', sessionId: room.id, participant: participant(guest) });
              broadcastState(room);
            } else {
              if (msg.type === 'ban') room.banned.add(guest.clientId);
              send(guest.ws, { type: 'kick', sessionId: room.id, participantId: guest.id, reason: 'Removed by host' });
              removeMember(room, guest, 'Removed by host'); guest.ws.close(1000, 'Removed by host');
            }
          }
        } else if (!member.pending) {
          if (msg.type === 'control_request' && room.settings.allowGuestsToControlPlayback && validAction(msg.action)) {
            send(room.host.ws, { ...msg, participantId: member.id });
          } else if (msg.type === 'add_track_request' && room.settings.allowGuestsToAddTracks && safeTrack(msg.track) && ['PLAY_NEXT','ADD_TO_QUEUE'].includes(msg.mode)) {
            send(room.host.ws, { ...msg, participantId: member.id });
          } else fail(ws, room, 'This action is not allowed by the host.',
            msg.type === 'control_request' ? 'GUEST_CONTROL_DISABLED' : msg.type === 'add_track_request' ? 'GUEST_ADD_DISABLED' : 'forbidden');
        }
      } catch { fail(ws, room, 'Invalid message'); }
    });
    ws.on('close', () => { clearTimeout(helloTimeout); clearInterval(ping); sockets.delete(ws); if (member) removeMember(room, member, 'Disconnected'); });
  });
  const sweep = setInterval(() => {
    const now = Date.now();
    for (const room of rooms.values()) if (now - room.createdAt > roomTtlMs || (!room.host && now > room.hostDeadline)) destroyRoom(room, 'Room expired');
    for (const [ip, entry] of limits) if (now > entry.until) limits.delete(ip);
  }, 15_000);
  sweep.unref();
  return { server, rooms,
    async listen(port = 8787, host = '0.0.0.0') {
      await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, host, resolve); });
      return server.address();
    },
    async close() {
      clearInterval(sweep);
      for (const ws of sockets) ws.terminate();
      await new Promise(resolve => wss.close(resolve));
      await new Promise(resolve => server.close(resolve));
    },
  };
}

function validAction(a) {
  if (!a || typeof a !== 'object') return false;
  const nonnegative = v => Number.isSafeInteger(v) && v >= 0;
  if (['play','pause','skip_next','skip_previous'].includes(a.type)) return true;
  if (a.type === 'seek_to') return nonnegative(a.positionMs);
  if (a.type === 'seek_to_index') return nonnegative(a.index) && nonnegative(a.positionMs ?? 0);
  if (a.type === 'seek_to_track') return typeof a.trackId === 'string' && /^[\w-]{1,128}$/.test(a.trackId) && nonnegative(a.positionMs ?? 0);
  if (a.type === 'set_repeat_mode') return [0,1,2].includes(a.repeatMode);
  if (a.type === 'set_shuffle_enabled') return typeof a.shuffleEnabled === 'boolean';
  return false;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const relay = createRelay({ token: process.env.MUSIK_SERVER_TOKEN, publicUrl: process.env.MUSIK_PUBLIC_URL });
    const address = await relay.listen(Number(process.env.PORT || 8787), process.env.HOST || '0.0.0.0');
    console.log(`Musik Together listening on port ${address.port}. Set this server's network URL in the Together tab.`);
    for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, async () => { await relay.close(); process.exit(0); });
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
