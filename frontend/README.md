# CORE operations workspace

Next.js application for inventory CRUD, order lifecycle management, and document-based operational assistance. Data comes from the CORE gateway; empty and unavailable states are shown explicitly.

```bash
npm ci
cp .env.example .env.local
npm run dev
```

Open http://localhost:3000 and sign in with the gateway credentials (local defaults: `admin` / `admin`). Credentials stay in React memory and are cleared by a refresh or sign-out. API requests use a same-origin server proxy, with `GATEWAY_URL` configured at runtime. No gateway credentials are bundled into browser code.

```bash
npm run typecheck
npm run lint
npm run build
npm run test:proxy
npm start
```

The Podman image, built from `Containerfile`, uses the standalone Next.js server and runs as a non-root user. Inventory prices are displayed in USD. Low stock means 10 units or fewer. Orders do not reserve or decrement inventory. The assistant retrieves only explicitly submitted documents, not live inventory or order records.
