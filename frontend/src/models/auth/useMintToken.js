import { useState, useCallback } from "react";

// Mint a short-lived API token via step-up Basic auth.
// IMPORTANT: uses a raw fetch with credentials:"omit" so NO session cookie is sent — the server
// must verify the supplied password fresh (a session alone cannot mint).
export async function mintToken(username, password) {
  const resp = await fetch("/api/v3/auth/token", {
    method: "POST",
    credentials: "omit",
    headers: {
      Authorization: "Basic " + btoa(`${username}:${password}`),
    },
  });
  let data = null;
  try { data = await resp.json(); } catch (_e) { /* non-JSON body */ }
  if (resp.status !== 200 || !data || !data.token) {
    const err = new Error((data && data.error) || `mint failed (${resp.status})`);
    err.status = resp.status;
    throw err;
  }
  return data; // { token, tokenType, expiresInSeconds }
}

// Thin hook wrapper for components (keeps call sites declarative).
export default function useMintToken() {
  const [loading, setLoading] = useState(false);
  const mint = useCallback(async (username, password) => {
    setLoading(true);
    try { return await mintToken(username, password); }
    finally { setLoading(false); }
  }, []);
  return { mint, loading };
}
