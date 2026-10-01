# Copiloto para Motoristas

Assistente de rentabilidade para motoristas de aplicativo (Uber, 99, inDrive). Lê a oferta de corrida na tela, calcula **R$/km**, **R$/hora** e **lucro líquido** com base nos custos do motorista, e mostra uma recomendação em **cartão flutuante** (verde / amarelo / vermelho) com **aviso por voz** — tudo sem o motorista tirar os olhos da rua.

## Estrutura

| Pasta | Stack | Papel |
|-------|-------|-------|
| `android/` | Kotlin · Jetpack Compose · MVVM · Room · Coroutines | App nativo Android |
| `backend/` | Django REST · PostgreSQL · Docker | API de sincronização e histórico |

## Módulos implementados

- **A — Scraping Engine:** `AccessibilityService` lê a árvore de views dos apps de corrida e extrai preço/distância/tempo via regex.
- **B — Calculation Engine:** cálculo puro de custo, R$/km, R$/h, lucro e classificação (verde/amarelo/vermelho).
- **C — Overlay flutuante:** cartão `WindowManager` leve, arrastável e que não bloqueia toques por baixo.
- **D — Voz (TTS):** anúncio em português ("Corrida verde. 15 reais, 5 quilômetros. Lucro de 10 reais.").
- **F — Sincronização + Admin:** Room (offline-first) → WorkManager → Django REST + PostgreSQL, com Django Admin.
- **G — Simulador de corrida:** botão que injeta uma oferta fictícia no mesmo pipeline (overlay + voz), sem esperar uma corrida real.

> **Módulo E (câmera secreta) não foi implementado** por decisão do dono do projeto. A gravação oculta de passageiros (e o bypass da exigência do Android de mostrar o preview da câmera) é um problema legal e de privacidade. Caso a função de segurança seja retomada, deve ser uma **dashcam transparente** (notificação visível, sem bypass).

## Como rodar

### App Android
Pré-requisitos: Android Studio (ou JDK 17 + Android SDK com a plataforma `android-36`).

```bash
cd android
./gradlew :app:assembleDebug          # gera o APK de debug
./gradlew :app:testDebugUnitTest      # roda os testes do motor de cálculo e parser
./gradlew :app:installDebug           # instala em um device/emulador conectado
```

No primeiro uso, conceda na tela inicial as permissões de **cartão flutuante** (sobreposição) e **acessibilidade**. Use **Simular corrida** para testar sem uma corrida real.

> O `API_BASE_URL` padrão é `http://10.0.2.2:8000/` (host local visto de dentro do emulador). Ajuste em `android/app/build.gradle.kts` para um backend real.

### Backend
```bash
cd backend
cp .env.example .env     # ajuste as credenciais
docker compose up --build
```
A API sobe em `http://localhost:8000/` (migrations e `collectstatic` rodam automaticamente). Admin em `/admin/` (crie um superusuário com `docker compose run --rm web python manage.py createsuperuser`).

Para rodar localmente sem Docker/Postgres:
```bash
cd backend
python -m venv .venv && .venv/Scripts/pip install -r requirements.txt   # Windows
USE_SQLITE=True DJANGO_SECRET_KEY=dev python manage.py test
```

### Endpoints
Todos exigem o header `X-Device-Id`:

| Método | Rota | Descrição |
|--------|------|-----------|
| `GET` / `POST` | `/api/rides/` | Lista / cria o histórico de corridas do dispositivo |
| `GET` / `PUT` | `/api/profile/` | Lê / atualiza a configuração do motorista |

## Testes
- Android: `cd android && ./gradlew :app:testDebugUnitTest` (motor de cálculo + parser).
- Backend: `cd backend && USE_SQLITE=True python manage.py test`.
