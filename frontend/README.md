# VellumHub Web

SPA React/Vite que usa o Gateway para gêneros, preferências, recomendações, livros,
avaliações e progresso. O login usa o Auth diretamente, com Authorization Code e
PKCE por meio de `oidc-client-ts`. O token fica na sessão da aba.

## Executar localmente

Use Node 24. Na raiz do repositório, ajuste estes valores em `.env` antes de subir
a stack Compose. Se já houver outras origens no Gateway, acrescente a origem da SPA
à lista existente.

```dotenv
VELLUM_AUTH_CLIENT_ENABLED=true
VELLUM_AUTH_CLIENT_ID=vellumhub-web
VELLUM_AUTH_CLIENT_REDIRECT_URI=http://127.0.0.1:9100/callback
VELLUM_AUTH_CLIENT_POST_LOGOUT_URI=http://127.0.0.1:9100/
VELLUM_AUTH_CLIENT_SCOPES=user:read,user:write,catalog:read,catalog:personal:read,catalog:personal:write,engagement:write,recommendation:read
VELLUM_AUTH_ALLOWED_ORIGIN=http://127.0.0.1:9100
CORS_ALLOWED_ORIGINS=http://127.0.0.1:9100
```

Use a origem exata `http://127.0.0.1:9100` no navegador. A SPA usa por padrão
o Auth em `http://127.0.0.1:9000` e o Gateway em `http://127.0.0.1:8080`.
Sobrescreva com `VITE_OIDC_ISSUER`, `VITE_OIDC_CLIENT_ID` e `VITE_GATEWAY_URL`
quando necessário. O cliente OIDC é público e dispensa segredo de cliente.
O escopo `catalog:personal:read` carrega o estado de leitura e habilita a Estante.
Se o Auth já tiver um cliente persistido com os escopos antigos, configure um
novo ID em `VELLUM_AUTH_CLIENT_ID` e `VITE_OIDC_CLIENT_ID` ou atualize o registro
existente antes de reiniciar o Auth.

```sh
cd frontend
npm ci
npm run dev
```

## Desenvolvimento

`tokens.json` usa o formato DTCG. `npm run tokens` gera as variáveis CSS dos
dois temas via Style Dictionary. O Tailwind lê as variáveis no bloco
`@theme inline`. `npm run lint` verifica se o CSS gerado está atualizado.

```sh
npm run lint
npm run build
npm run build-storybook
npx playwright install chromium
npm test
```

Os testes de navegador exercitam teclado, tema, acessibilidade e o fluxo integrado
com respostas HTTP simuladas. Rodar contra a stack local com dados de catálogo
continua sendo necessário para validar login e recomendação com serviços reais.
