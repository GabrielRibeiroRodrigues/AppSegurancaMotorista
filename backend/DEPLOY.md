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
- Quando quiser levar a sério, coloque um domínio + HTTPS (Caddy/Nginx + Let's Encrypt) e
  troque o app para `https://`.
