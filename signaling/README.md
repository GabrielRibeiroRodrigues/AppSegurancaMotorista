# Sinalização WebRTC

Servidor WebSocket que faz o aperto de mão (SDP/ICE) do vídeo ao vivo entre o
app do motorista (driver) e a Central de Operações (viewer). O vídeo em si é
P2P — não passa por aqui.

- Porta: `4001` (env `PORT`), caminho `/signaling`.
- Rodar local: `npm install && npm start`
- Docker: incluído no `backend/docker-compose.prod.yml` (serviço `signaling`).
