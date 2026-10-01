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
```bash
docker compose -f docker-compose.prod.yml up -d --build
```
O `entrypoint.sh` roda `migrate` + `collectstatic` automaticamente. A API fica em
`http://SEU_IP:8000/`. Crie um admin:
```bash
docker compose -f docker-compose.prod.yml exec web python manage.py createsuperuser
```

## 5. Abrir a porta 8000
- **UFW:** `sudo ufw allow 8000/tcp`
- **Cloud (AWS/Oracle/GCP/DigitalOcean):** libere a porta 8000 TCP no *security group* / firewall do painel.

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
- Quando quiser levar a sério, coloque um domínio + HTTPS (Caddy/Nginx + Let's Encrypt) e
  troque o app para `https://`.
