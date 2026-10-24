// An intentionally minimal, local-only protocol harness; NOT production SPA auth code.
// Transaction state is stored temporarily; OAuth tokens exist only in this page's memory.
const issuer = 'http://127.0.0.1:9000';
const clientId = 'vellumhub-spa-poc';
const redirectUri = 'http://127.0.0.1:9100/index.html';
const status = document.querySelector('#status');
const api = document.querySelector('#api');
const logout = document.querySelector('#logout');
let accessToken;

const urlSafe = bytes => btoa(String.fromCharCode(...bytes))
  .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const random = bytes => urlSafe(crypto.getRandomValues(new Uint8Array(bytes)));
const sha256 = async text => new Uint8Array(await crypto.subtle.digest(
  'SHA-256', new TextEncoder().encode(text)));

async function login() {
  const verifier = random(64);
  const state = random(32);
  const nonce = random(32);
  sessionStorage.setItem('poc-transaction', JSON.stringify({verifier, state, nonce}));
  const params = new URLSearchParams({
    response_type: 'code', client_id: clientId, scope: 'openid profile',
    redirect_uri: redirectUri, code_challenge_method: 'S256',
    code_challenge: urlSafe(await sha256(verifier)), state, nonce
  });
  window.location.assign(issuer + '/oauth2/authorize?' + params);
}
function inspectIdToken(idToken, expectedNonce) {
  // This check is illustrative ONLY. A production SPA must use a maintained
  // OIDC library that validates ID Token signature and the complete protocol.
  const payload = JSON.parse(atob(idToken.split('.')[1]
    .replace(/-/g, '+').replace(/_/g, '/')));
  if (payload.nonce !== expectedNonce || payload.iss !== issuer ||
      payload.aud !== clientId) throw Error('ID Token nonce/issuer/audience mismatch');
  return payload.sub;
}
async function completeCallback() {
  const params = new URLSearchParams(window.location.search);
  if (!params.has('code') && !params.has('error')) return;
  history.replaceState(null, '', redirectUri);
  const raw = sessionStorage.getItem('poc-transaction');
  sessionStorage.removeItem('poc-transaction');
  if (!raw) throw Error('Missing ephemeral PKCE transaction');
  const {verifier, state, nonce} = JSON.parse(raw);
  if (params.get('state') !== state || params.has('error')) {
    throw Error('OAuth state mismatch or authorization denied');
  }
  const body = new URLSearchParams({
    grant_type: 'authorization_code', client_id: clientId,
    code: params.get('code'), redirect_uri: redirectUri,
    code_verifier: verifier
  });
  const response = await fetch(issuer + '/oauth2/token', {
    method: 'POST', headers: {'Content-Type':'application/x-www-form-urlencoded'},
    body
  });
  if (!response.ok) throw Error('Code exchange rejected: HTTP ' + response.status);
  const tokens = await response.json();
  if (tokens.refresh_token) throw Error('Unexpected refresh token in public SPA');
  if (tokens.token_type?.toLowerCase() !== 'bearer') throw Error('Unexpected token type');
  const sub = inspectIdToken(tokens.id_token, nonce);
  accessToken = tokens.access_token;
  api.disabled = false;
  logout.disabled = false;
  status.textContent = 'Signed in. OIDC subject: ' + sub
    + '\nAccess token is held in memory only. TTL: ' + tokens.expires_in + 's';
}
document.querySelector('#login').addEventListener('click', () => {
  login().catch(e => status.textContent = e.message);
});
api.addEventListener('click', async () => {
  try {
    const response = await fetch(issuer + '/poc/api/me', {
      headers: {Authorization: 'Bearer ' + accessToken}
    });
    const result = await response.text();
    status.textContent = 'Protected API: HTTP ' + response.status + '\n' + result;
  } catch (e) {status.textContent = e.message;}
});
logout.addEventListener('click', () => {
  accessToken = undefined;
  api.disabled = true;
  logout.disabled = true;
  status.textContent = 'Local access token cleared. AS SSO session may remain active; logout policy belongs to #309.';
});
completeCallback().catch(e => status.textContent = 'OAuth failed: ' + e.message);
