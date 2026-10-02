// Servidor de sinalização WebRTC da Central de Operações (DesafioMaker).
// Só faz o "aperto de mão" (troca de SDP/ICE) entre o app do motorista (driver)
// e o navegador da operadora (viewer); o vídeo viaja P2P, não passa por aqui.
// Uma "sala" = um alertId. Um driver por sala; vários viewers.
//
// Protocolo (mensagens JSON), idêntico ao do backend original:
//   driver -> { type:'register-driver', alertId }
//   viewer -> { type:'watch', alertId }
//   server -> driver: { type:'watch-request', alertId, viewerId }
//   driver -> { type:'offer', alertId, targetId, sdp }   -> viewer: { type:'offer', alertId, sdp }
//   viewer -> { type:'answer', alertId, sdp }             -> driver: { type:'answer', alertId, viewerId, sdp }
//   ambos  -> { type:'ice-candidate', alertId, [targetId], candidate }

const http = require('node:http');
const crypto = require('node:crypto');
const { WebSocketServer } = require('ws');

const PORT = Number(process.env.PORT || 4001);

// --- Authentication -------------------------------------------------------
// The signaling link carries a live camera feed, so every connection must prove
// who it is with the Django JWT (same HS256 secret). Only operators (is_staff)
// may watch; any authenticated user may stream as a driver.
//   ws://host:4001/signaling?token=<access-jwt>
// Set SIGNALING_JWT_SECRET to Django's DJANGO_SECRET_KEY. For local dev without
// tokens, set SIGNALING_AUTH=off (never in production).
const JWT_SECRET = process.env.SIGNALING_JWT_SECRET || '';
const AUTH_DISABLED = process.env.SIGNALING_AUTH === 'off';
if (!AUTH_DISABLED && !JWT_SECRET) {
  console.error('[signaling] SIGNALING_JWT_SECRET ausente — defina-o (= DJANGO_SECRET_KEY) ou use SIGNALING_AUTH=off em dev. Conexões serão recusadas.');
}

function base64urlToBuffer(str) {
  return Buffer.from(str.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
}

/** Verifies a Django SimpleJWT access token (HS256). Returns the payload or null. */
function verifyToken(token) {
  if (!token || !JWT_SECRET) return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  const [h, p, s] = parts;
  let header;
  try {
    header = JSON.parse(base64urlToBuffer(h).toString('utf8'));
  } catch {
    return null;
  }
  if (header.alg !== 'HS256') return null; // reject "none"/other algorithms
  const expected = crypto.createHmac('sha256', JWT_SECRET).update(`${h}.${p}`).digest();
  const given = base64urlToBuffer(s);
  if (expected.length !== given.length || !crypto.timingSafeEqual(expected, given)) return null;
  let payload;
  try {
    payload = JSON.parse(base64urlToBuffer(p).toString('utf8'));
  } catch {
    return null;
  }
  if (payload.token_type && payload.token_type !== 'access') return null;
  if (payload.exp && Date.now() / 1000 > payload.exp) return null;
  return payload;
}

function tokenFromRequest(req) {
  try {
    const url = new URL(req.url, 'http://localhost');
    return url.searchParams.get('token');
  } catch {
    return null;
  }
}

/** @type {Map<string, {driver: any, viewers: Map<string, any>}>} */
const rooms = new Map();

function getOrCreateRoom(alertId) {
  let room = rooms.get(alertId);
  if (!room) {
    room = { driver: null, viewers: new Map() };
    rooms.set(alertId, room);
  }
  return room;
}

function cleanupRoomIfEmpty(alertId) {
  const room = rooms.get(alertId);
  if (room && !room.driver && room.viewers.size === 0) rooms.delete(alertId);
}

function send(socket, message) {
  if (socket && socket.readyState === 1 /* OPEN */) socket.send(JSON.stringify(message));
}

const httpServer = http.createServer((req, res) => {
  // Healthcheck simples.
  if (req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok');
    return;
  }
  res.writeHead(404);
  res.end();
});

const wss = new WebSocketServer({ server: httpServer, path: '/signaling' });

wss.on('connection', (socket, req) => {
  socket.clientId = crypto.randomUUID();

  // Authenticate the connection up front.
  if (AUTH_DISABLED) {
    socket.user = { username: 'dev', is_staff: true };
  } else {
    const payload = verifyToken(tokenFromRequest(req));
    if (!payload) {
      send(socket, { type: 'error', reason: 'auth' });
      socket.close(4001, 'unauthorized');
      return;
    }
    socket.user = { username: payload.username, is_staff: !!payload.is_staff };
  }

  socket.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      return;
    }
    const alertId = typeof msg.alertId === 'string' ? msg.alertId : undefined;
    if (!alertId) return;

    switch (msg.type) {
      case 'register-driver': {
        const room = getOrCreateRoom(alertId);
        room.driver = socket;
        socket.role = 'driver';
        socket.alertId = alertId;
        for (const viewerId of room.viewers.keys()) {
          send(socket, { type: 'watch-request', alertId, viewerId });
        }
        break;
      }
      case 'watch': {
        // Only Central operators may watch a driver's live feed.
        if (!socket.user?.is_staff) {
          send(socket, { type: 'error', reason: 'forbidden' });
          return;
        }
        const room = getOrCreateRoom(alertId);
        room.viewers.set(socket.clientId, socket);
        socket.role = 'viewer';
        socket.alertId = alertId;
        if (room.driver) send(room.driver, { type: 'watch-request', alertId, viewerId: socket.clientId });
        break;
      }
      case 'offer': {
        const room = rooms.get(alertId);
        const viewer = typeof msg.targetId === 'string' ? room?.viewers.get(msg.targetId) : undefined;
        if (viewer) send(viewer, { type: 'offer', alertId, sdp: msg.sdp });
        break;
      }
      case 'answer': {
        const room = rooms.get(alertId);
        if (room?.driver) send(room.driver, { type: 'answer', alertId, viewerId: socket.clientId, sdp: msg.sdp });
        break;
      }
      case 'ice-candidate': {
        const room = rooms.get(alertId);
        if (!room) break;
        if (socket.role === 'driver') {
          const viewer = typeof msg.targetId === 'string' ? room.viewers.get(msg.targetId) : undefined;
          if (viewer) send(viewer, { type: 'ice-candidate', alertId, candidate: msg.candidate });
        } else if (room.driver) {
          send(room.driver, { type: 'ice-candidate', alertId, viewerId: socket.clientId, candidate: msg.candidate });
        }
        break;
      }
    }
  });

  socket.on('close', () => {
    const { alertId, role, clientId } = socket;
    if (!alertId || !role) return;
    const room = rooms.get(alertId);
    if (!room) return;
    if (role === 'driver') {
      room.driver = null;
      for (const viewer of room.viewers.values()) send(viewer, { type: 'driver-left', alertId });
    } else {
      room.viewers.delete(clientId);
      if (room.driver) send(room.driver, { type: 'viewer-left', alertId, viewerId: clientId });
    }
    cleanupRoomIfEmpty(alertId);
  });
});

httpServer.listen(PORT, () => {
  console.log(`[signaling] WebRTC em ws://0.0.0.0:${PORT}/signaling`);
});
