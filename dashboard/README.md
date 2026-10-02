# Central de Operações

Painel web (React + Vite + Leaflet) que recebe os **alertas de pânico** dos
motoristas em tempo real, mostra a localização no mapa, a transcrição capturada,
e permite ao operador agir (acionar polícia, contato ativo, falso positivo).

Portado do projeto *DesafioMaker* e reapontado para o backend **Django** do
Copiloto (`/api/alerts/`), em vez do servidor Express/PWA original.

## Rodar
```bash
cd dashboard
npm install
cp .env.example .env     # ajuste VITE_SERVER_URL para o backend
npm run dev              # abre em http://localhost:5173
```
- **Dev** (backend local): `VITE_SERVER_URL=http://localhost:8000`
- **Produção** (VPS): `VITE_SERVER_URL=http://SEU_IP:8000`

O painel faz *polling* de `GET /api/alerts/` e envia as ações do operador via
`PATCH /api/alerts/<id>/`. Os alertas são criados pelo app do motorista
(botão "Enviar alerta de teste" ou pela frase-gatilho do modo proteção).

## Build de produção
```bash
npm run build     # gera dist/ (pode servir com qualquer estático, ex.: nginx)
```

> **Vídeo ao vivo (WebRTC):** o componente `VideoFeed` se conecta a um servidor
> de sinalização em `${VITE_SERVER_URL}/signaling`. Enquanto o serviço de
> sinalização não estiver no ar, o restante do painel (lista, mapa, ações)
> funciona normalmente.
