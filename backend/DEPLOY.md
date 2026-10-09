# Deploy na VPS (HTTP em `IP:8000`)

Guia para subir o backend numa VPS Ubuntu e acessar do celular por `http://SEU_IP:8000`.
O app (`usesCleartextTraffic=true`) já aceita HTTP, então não precisa de domínio nem HTTPS
para testar. **Atenção:** sem HTTPS, senha e tokens trafegam sem criptografia — ok para
teste, **não** use com dados reais de usuários.

## 1. Pré-requisitos na VPS
```bash
# Ubuntu 22.04/24.04 — instala Docker + plugin Compose
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER      # faça logout/login depois deste comando
```

## 2. Copiar o projeto
```bash
git clone https://github.com/GabrielRibeiroRodrigues/AppSegurancaMotorista.git
cd AppSegurancaMotorista/backend
```

## 3. Configurar o ambiente
```bash
cp .env.prod.example .env
nano .env   # edite:
#   DJANGO_SECRET_KEY  -> python3 -c "import secrets; print(secrets.token_urlsafe(50))"
#   ALLOWED_HOSTS      -> o IP público da VPS
#   POSTGRES_PASSWORD  -> uma senha forte
```

## 4. Subir
Use um nome de projeto próprio (`-p copiloto`) para isolar os containers/rede/volume
dos outros serviços que já rodam na VPS:
```bash
docker compose -p copiloto -f docker-compose.prod.yml up -d --build
```
O `entrypoint.sh` roda `migrate` + `collectstatic` automaticamente. A API fica em
`http://SEU_IP:<WEB_PORT>/`. Crie um admin:
```bash
docker compose -p copiloto -f docker-compose.prod.yml exec web python manage.py createsuperuser
```

> **VPS com outros serviços:** o Postgres **não** publica porta (fica só na rede interna
> do compose), então não conflita. Só a porta do web importa. Se a 8000 já estiver em uso,
> defina outra no `.env` (ex.: `WEB_PORT=8080`) — veja se está livre com
> `sudo ss -tlnp | grep :8000`.

## 5. Abrir a porta no firewall (a mesma do `WEB_PORT`)
- **UFW:** `sudo ufw allow 8000/tcp` (troque 8000 pelo seu `WEB_PORT`)
- **Cloud (AWS/Oracle/GCP/DigitalOcean):** libere essa porta TCP no *security group* / firewall do painel.

## 6. Testar
No navegador do PC: `http://SEU_IP:8000/api/profile/` deve responder **401** (sem token = correto).
No app, o `API_BASE_URL` precisa apontar para `http://SEU_IP:8000/` — peça o rebuild do APK
com esse IP.

## Operação
- Logs: `docker compose -f docker-compose.prod.yml logs -f web`
- Atualizar após mudanças: `git pull && docker compose -f docker-compose.prod.yml up -d --build`
- Parar: `docker compose -f docker-compose.prod.yml down` (os dados ficam no volume `postgres_data`)

## Segurança mínima (recomendado)
- O Postgres **não** está exposto à internet (sem porta publicada) — mantenha assim.
- Troque `POSTGRES_PASSWORD` e `DJANGO_SECRET_KEY` por valores fortes.

## Central de Operações (painel web)

A Central agora sobe **junto** no `docker compose up` como o serviço `dashboard`
(nginx servindo o site e encaminhando `/api` e `/signaling` para os serviços
internos — mesma origem, **sem CORS e sem chave**). Depois do deploy:

- Acesse **`http://SEU_IP:8080/`** (troque a porta com `DASHBOARD_PORT` no `.env`).
- Faça login com uma conta de **operador** (`is_staff`). Crie uma:
  ```bash
  docker compose -p copiloto -f docker-compose.prod.yml exec web python manage.py createsuperuser
  ```
- Abra `8080/tcp` no firewall (igual ao `8000`).

## Produção endurecida (HTTPS + TURN + backup)

O fluxo `IP:8000` por HTTP acima serve para **testes**. Para usuários reais, use a
camada `docker-compose.tls.yml`, que adiciona:

- **Caddy** — proxy reverso com **HTTPS automático** (Let's Encrypt). Exige um
  **domínio** apontando para a VPS (certificado não é emitido para IP puro) e as
  portas **80/443** abertas no firewall.
- **coturn** — servidor **TURN**, para o vídeo ao vivo conectar mesmo em 4G/NAT simétrico.
- **backup** — `pg_dump` diário do Postgres (mantém os últimos 7).

### 1. Operadores da Central
A lista de alertas e as ações agora são **restritas a operadores** (conta `is_staff`).
Crie um operador (ou promova um usuário no admin):
```bash
docker compose -p copiloto -f docker-compose.prod.yml exec web \
  python manage.py createsuperuser   # superuser já é is_staff (operador)
```
O dashboard web (Central) agora pede login dessa conta.

### 2. `.env` adicional
```bash
CADDY_DOMAIN=copiloto.seudominio.com   # domínio -> IP da VPS
SECURE_SSL=True                        # cookies seguros + HSTS (Django atrás do TLS)
CORS_ALLOWED_ORIGINS=https://copiloto.seudominio.com   # origem do dashboard
PUBLIC_IP=191.252.100.161              # IP público (para o TURN anunciar o relay)
TURN_USERNAME=copiloto
TURN_PASSWORD=uma-senha-forte-do-turn
```

### 3. Subir com a camada de TLS
```bash
docker compose -p copiloto \
  -f docker-compose.prod.yml -f docker-compose.tls.yml up -d --build
```
Depois **feche** 8000/4001 no firewall (tudo passa pelo 443) e abra 80/443 +
`3478/tcp,udp` e `49160-49200/udp` (TURN).

### 4. Rebuild do APK para HTTPS/WSS + TURN
```bash
cd android
./gradlew assembleDebug \
  -PapiBaseUrl=https://copiloto.seudominio.com/ \
  -PsignalingUrl=wss://copiloto.seudominio.com/signaling \
  -PturnUrl=turn:191.252.100.161:3478 \
  -PturnUsername=copiloto -PturnCredential=uma-senha-forte-do-turn
```
Com HTTPS, dá para **remover** o `usesCleartextTraffic="true"` do `AndroidManifest.xml`.
O dashboard usa as mesmas infos via `VITE_SERVER_URL`, `VITE_TURN_*` (veja `dashboard/.env.example`).
