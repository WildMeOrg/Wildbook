import { basicHeader } from "./useMintToken";

// Organization service key management calls. Every call sends NO cookie
// (credentials:"omit") and an explicit Basic header: the server verifies the
// password fresh and never trusts the session. auth = {username, password}.
async function call(auth, method, url, body) {
  const headers = { Authorization: basicHeader(auth.username, auth.password) };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const resp = await fetch(url, {
    method,
    credentials: "omit",
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let data = null;
  try {
    data = await resp.json();
  } catch (_e) {
    /* non-JSON body */
  }
  if (!resp.ok) {
    const err = new Error((data && data.error) || `request failed (${resp.status})`);
    err.status = resp.status;
    throw err;
  }
  return data || {};
}

const orgUrl = (orgId) =>
  `/api/v3/organizations/${encodeURIComponent(orgId)}/service-keys`;

export async function listManageableOrgs(auth) {
  const data = await call(auth, "GET", "/api/v3/service-keys/organizations");
  return data.organizations || [];
}

export async function listKeys(auth, orgId) {
  const data = await call(auth, "GET", orgUrl(orgId));
  return data.keys || [];
}

export function createKey(auth, orgId, { label, scopes, days }) {
  return call(auth, "POST", orgUrl(orgId), { label, scopes, days });
}

export function revokeKey(auth, orgId, keyId) {
  return call(auth, "DELETE", `${orgUrl(orgId)}/${encodeURIComponent(keyId)}`);
}

export function revokeAll(auth, orgId) {
  return call(auth, "DELETE", orgUrl(orgId));
}
