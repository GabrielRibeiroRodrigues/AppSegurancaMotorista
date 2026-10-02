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

wss.on('connection', (socket) => {
  socket.clientId = crypto.randomUUID();

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
