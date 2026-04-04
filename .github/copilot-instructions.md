# EasySell Workspace Instructions

## Project Scope
- This workspace contains three connected parts:
- Android seller app in `app/`
- Web storefront in `EasySell-WEB/easysell-webapp/`
- Node notification/analytics backend in `EasySell-WEB/easysell-backend/`

## Architecture
- Treat Android as seller operations (catalogues, orders, seller-facing notifications).
- Treat web app as buyer storefront and checkout flow.
- Treat backend as API bridge for notifications and analytics (`/api/notify-*`, `/api/analytics/*`).
- Firebase is the shared data/auth backbone across Android and web.

See `analysis.txt` for the full ecosystem narrative.

## Build And Test
- Android (Windows): `./gradlew.bat :app:assembleDebug`
- Android tests: `./gradlew.bat :app:testDebugUnitTest`
- Web app: from `EasySell-WEB/easysell-webapp/`
- Install: `npm install`
- Run dev server: `npm start`
- Build: `npm run build`
- Backend: from `EasySell-WEB/easysell-backend/`
- Install: `npm install`
- Run server: `node server.js`

## Conventions
- Prefer existing stack patterns over introducing new frameworks.
- Android app uses Java + ViewBinding + Firebase libraries.
- Web app uses React function components + Chakra UI.
- Keep backend changes minimal and consistent with current Express style.
- For custom-domain and store-resolution work, check `impl plans/implementation_plan.md` first.

## Repository Guardrails
- Do not edit generated build outputs:
- `app/build/`
- `build/`
- Do not commit machine-local secrets/paths:
- `local.properties`
- `serviceAccountKey.json` (if created locally for backend runtime)
- There are two backend folders (`EasySell-Backend/` and `EasySell-WEB/easysell-backend/`).
- Default to `EasySell-WEB/easysell-backend/` for web flow/API work unless explicitly asked to change the other one.

## Documentation Links
- Root context: `analysis.txt`
- In-progress feature plan: `impl plans/implementation_plan.md`
- Backend deploy notes:
- `EasySell-WEB/easysell-backend/DEPLOY_RENDER.md`
- `EasySell-Backend/DEPLOY_RENDER.md`