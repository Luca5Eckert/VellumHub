import { UserManager, WebStorageStateStore } from 'oidc-client-ts'

export const session = new UserManager({
  authority: import.meta.env.VITE_OIDC_ISSUER ?? 'http://127.0.0.1:9000',
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'vellumhub-web',
  redirect_uri: `${location.origin}/callback`,
  post_logout_redirect_uri: location.origin + '/',
  response_type: 'code',
  scope: 'openid profile user:read user:write catalog:read catalog:personal:read catalog:personal:write engagement:write recommendation:read',
  userStore: new WebStorageStateStore({ store: sessionStorage }),
  automaticSilentRenew: false,
  monitorSession: false,
})
