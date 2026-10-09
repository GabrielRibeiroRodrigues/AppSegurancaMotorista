# Central de Operações (dashboard React) — build estático + nginx.
# O nginx serve o site e encaminha /api e /signaling para o backend/sinalização,
# então o navegador fala tudo na MESMA origem (sem CORS, sem chave de API).

FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# Mesma origem: a API vira /api e a sinalização /signaling (resolvidos pelo nginx).
ENV VITE_SERVER_URL=""
ENV VITE_SIGNALING_URL="/signaling"
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
