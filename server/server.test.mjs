import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { WebSocket } from 'ws';
import { createRelay } from './server.mjs';

const token = 'test-token-at-least-16-characters';
async function fixture(t) {
  const relay = createRelay({ token });
  const address = await relay.listen(0, '127.0.0.1');
  const base = `http://127.0.0.1:${address.port}`;
  t.after(() => relay.close());
  async function post(path, body, auth = token) {
    const response = await fetch(`${base}/v1/together/${path}`, {
      method: 'POST', headers: { ...(auth ? { Authorization: `Bearer ${auth}` } : {}), 'Content-Type': 'application/json' }, body: JSON.stringify(body),
    });
    return { status: response.status, ...await response.json() };
  }
  async function client(room, host, name = host ? 'Host' : 'Guest') {
    const ws = new WebSocket(room.wsUrl, host ? { headers: { Authorization: `Bearer ${token}` } } : {});
    const inbox = [];
    const waiting = [];
    ws.on('message', data => {
      const msg = JSON.parse(data);
      const index = waiting.findIndex(w => w.match(msg));
      if (index >= 0) { const [w] = waiting.splice(index, 1); clearTimeout(w.timer); w.resolve(msg); }
      else inbox.push(msg);
    });
    function next(type, predicate = () => true) {
      const match = m => m.type === type && predicate(m);
      const index = inbox.findIndex(match);
      if (index >= 0) return Promise.resolve(inbox.splice(index, 1)[0]);
      return new Promise((resolve, reject) => {
        const w = { match, resolve, timer: setTimeout(() => { waiting.splice(waiting.indexOf(w), 1); reject(Error(`Timed out waiting for ${type}`)); }, 3000) };
        waiting.push(w);
      });
    }
    await once(ws, 'open');
    ws.send(JSON.stringify({ type: 'client_hello', protocolVersion: 1, sessionId: room.sessionId,
      sessionKey: host ? room.hostKey : room.guestKey, clientId: name, displayName: name }));
    const welcome = await next('server_welcome');
    return { ws, welcome, next, send: msg => ws.send(JSON.stringify({ sessionId: room.sessionId, ...msg })) };
  }
  return { relay, base, post, client };
}
const playback = (room, extras = {}) => ({
  type: 'room_state', state: { sessionId: room.sessionId, hostId: 'phone-host', participants: [],
    settings: { allowGuestsToAddTracks: true, allowGuestsToControlPlayback: false, requireHostApprovalToJoin: false },
    queue: [{ id: 'testVideo01', title: 'Test track', artists: ['Artist'], durationSec: 120, thumbnailUrl: null }],
    queueHash: 'testVideo01', currentIndex: 0, positionMs: 4000, isPlaying: true, repeatMode: 0, shuffleEnabled: false,
    sentAtElapsedRealtimeMs: 999999999, ...extras },
});

test('authenticated room creation, codes and invalid requests', async t => {
  const f = await fixture(t);
  assert.equal((await fetch(`${f.base}/health`)).status, 200);
  assert.equal((await f.post('sessions', { hostDisplayName: 'Host' }, 'wrong')).status, 401);
  assert.equal((await f.post('sessions', { hostDisplayName: 'Host' }, null)).status, 401);
  assert.equal((await f.post('sessions', { hostDisplayName: '' })).status, 400);
  const room = await f.post('sessions', { hostDisplayName: 'Host' });
  assert.equal(room.status, 201);
  assert.match(room.code, /^[A-Z2-9]{6}$/);
  assert.notEqual(room.hostKey, room.guestKey);
  const invite = await fetch(`${f.base}/?code=${room.code}`);
  assert.equal(invite.status, 200);
  const html = await invite.text();
  assert.ok(html.includes(`musik://jam?code=${room.code}`));
  assert.ok(!html.includes(token));
  assert.equal((await f.post('sessions/resolve', { code: room.code })).status, 404);
  await f.client(room, true);
  const resolved = await f.post('sessions/resolve', { code: room.code.toLowerCase() }, null);
  assert.equal(resolved.guestKey, room.guestKey);
  assert.equal(resolved.hostKey, undefined);
  assert.equal((await f.post('sessions/resolve', { code: 'missing' })).status, 404);
});

test('two phones share track, pause, seek, permissions and relay clock', async t => {
  const f = await fixture(t);
  const room = await f.post('sessions', { hostDisplayName: 'Host' });
  const host = await f.client(room, true);
  const guest = await f.client(room, false);
  await host.next('participant_joined');
  host.send(playback(room));
  const first = (await guest.next('room_state')).state;
  assert.equal(first.queue[0].id, 'testVideo01');
  assert.equal(first.participants.length, 2);
  assert.equal(first.hostId, host.welcome.participantId);
  assert.ok(first.sentAtElapsedRealtimeMs < 999999999);
  guest.send({ type: 'control_request', participantId: 'forged-host', action: { type: 'pause' } });
  assert.equal((await guest.next('server_error')).code, 'GUEST_CONTROL_DISABLED');
  host.send(playback(room, { settings: { allowGuestsToControlPlayback: true }, positionMs: 12000, isPlaying: false }));
  const paused = (await guest.next('room_state', m => !m.state.isPlaying)).state;
  assert.equal(paused.positionMs, 12000);
  guest.send({ type: 'control_request', participantId: 'forged-host', action: { type: 'seek_to', positionMs: 15000 } });
  const requested = await host.next('control_request');
  assert.equal(requested.participantId, guest.welcome.participantId);
  assert.equal(requested.action.positionMs, 15000);
  guest.send({ type: 'heartbeat_ping', pingId: 1, clientElapsedRealtimeMs: 400 });
  const pong = await guest.next('heartbeat_pong');
  assert.ok(pong.serverElapsedRealtimeMs >= paused.sentAtElapsedRealtimeMs);
  assert.equal(pong.clientElapsedRealtimeMs, 400);
  // Guests cannot forge playback state or act in another room.
  guest.send(playback(room, { positionMs: 999 }));
  guest.send({ type: 'control_request', sessionId: 'other-room', action: { type: 'pause' } });
  assert.equal((await guest.next('server_error', m => m.message === 'Wrong session')).message, 'Wrong session');
  host.send(playback(room, { positionMs: 16000, isPlaying: false }));
  assert.equal((await guest.next('room_state', m => m.state.positionMs === 16000)).state.queue[0].title, 'Test track');
});

test('host approval, late joining, banning and host disconnect cleanup', async t => {
  const f = await fixture(t);
  const room = await f.post('sessions', { hostDisplayName: 'Host', settings: { requireHostApprovalToJoin: true } });
  const host = await f.client(room, true);
  host.send(playback(room, { settings: { requireHostApprovalToJoin: true }, isPlaying: false }));
  await host.next('room_state');
  const guest = await f.client(room, false);
  assert.equal(guest.welcome.isPending, true);
  const join = await host.next('join_request');
  host.send({ type: 'join_decision', participantId: join.participant.id, approved: true });
  await guest.next('participant_joined');
  assert.equal((await guest.next('room_state')).state.queue[0].title, 'Test track');
  host.send({ type: 'ban', participantId: guest.welcome.participantId });
  await guest.next('kick');
  await once(guest.ws, 'close');
  const listener = await f.client(room, false, 'Another guest');
  const closed = once(listener.ws, 'close');
  host.ws.close();
  await closed;
  assert.equal((await f.post('sessions/resolve', { code: room.code })).status, 404);
  assert.equal(f.relay.rooms.size, 0);
});

test('rejects forged session keys and malformed websocket input', async t => {
  const f = await fixture(t);
  const room = await f.post('sessions', { hostDisplayName: 'Host' });
  const host = await f.client(room, true);
  host.ws.send('{broken-json');
  assert.equal((await host.next('server_error')).message, 'Invalid message');
  const ws = new WebSocket(room.wsUrl, { headers: { Authorization: `Bearer ${token}` } });
  await once(ws, 'open');
  const closed = once(ws, 'close');
  ws.send(JSON.stringify({ type: 'client_hello', protocolVersion: 1, sessionId: room.sessionId,
    sessionKey: 'wrong-key', clientId: 'fake', displayName: 'Fake' }));
  assert.equal((await closed)[0], 1008);
  host.send(playback(room, { queue: [{ id: 'file:///private/song.mp3', title: 'Private local file' }] }));
  assert.match((await host.next('server_error')).message, /online tracks/);
  assert.equal(f.relay.rooms.get(room.sessionId).state, null);
});
