# Sinalização WebRTC

Servidor WebSocket que faz o aperto de mão (SDP/ICE) do vídeo ao vivo entre o
app do motorista (driver) e a Central de Operações (viewer). O vídeo em si é
P2P — não passa por aqui.

- Porta: `4001` (env `PORT`), caminho `/signaling`.
- Rodar local: `npm install && npm start`
- Docker: incluído no `backend/docker-compose.prod.yml` (serviço `signaling`).

## Autenticação

Toda conexão precisa do JWT do Django na query: `ws://host:4001/signaling?token=<access>`.
O servidor valida a assinatura (HS256) e só deixa **operadores** (`is_staff`) assistirem;
qualquer usuário autenticado pode transmitir como motorista.

- `SIGNALING_JWT_SECRET` — **obrigatório**; igual ao `DJANGO_SECRET_KEY`.
- `SIGNALING_AUTH=off` — desliga a autenticação (apenas para dev local, nunca em produção).
