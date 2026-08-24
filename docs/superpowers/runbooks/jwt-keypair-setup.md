# JWT Keypair Setup — Wildbook Scoped-Access Tokens

This runbook covers generating the RSA-2048 keypair for `POST /api/v3/auth/token`,
configuring Wildbook, and maintaining key hygiene + rotation.

---

## 1. Generate an RSA-2048 Keypair

Run these commands on a secure workstation (not inside the container):

```bash
# 1. Generate the RSA-2048 private key in PEM form
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt_private.pem

# 2. Produce the Base64-encoded PKCS8 DER form → value for jwtPrivateKeyBase64
openssl pkcs8 -topk8 -nocrypt -in jwt_private.pem -outform DER | base64 -w0

# 3. Produce the Base64-encoded X.509 DER public key → value for jwtPublicKeyBase64
openssl rsa -in jwt_private.pem -pubout -outform DER | base64 -w0
```

Copy the output of steps 2 and 3 into `apiAccessKeys.properties` (see below).
**Immediately restrict the PEM file permissions** (`chmod 600 jwt_private.pem`).

---

## 2. `apiAccessKeys.properties` Keys

JWT keys live in their own file, `apiAccessKeys.properties` — a peer of
`commonConfiguration.properties` — so the signing key stays private and out of git.
Place a copy with real values in the data-dir override location (NOT the in-git template):

```
webapps/<dataDir>/WEB-INF/classes/bundles/apiAccessKeys.properties
```

where `<dataDir>` defaults to `wildbook_data_dir` (overridable per context via
`<context>DataDir`). Only the keys you set here are needed; the in-git template ships
with every key unset (token service disabled). Add:

```properties
# Required — Base64(PKCS8 DER) of the RSA-2048 private key (Wildbook signs tokens)
jwtPrivateKeyBase64=<output of step 2 above>

# Recommended — Base64(X.509 DER) of the RSA-2048 public key (optional Wildbook-side verify)
jwtPublicKeyBase64=<output of step 3 above>

# JWT issuer claim — default: wildbook
jwtIssuer=wildbook

# JWT audience claim — default: wildbook-scoped-api
jwtAudience=wildbook-scoped-api

# Token lifetime in seconds — default: 1800 (30 min), clamped to [60, 86400]
jwtTtlSeconds=1800

# Optional: sets the JWT 'kid' header for key rotation (see section 4)
# jwtKeyId=v1

# Optional: the context tokens are pinned to — default: context0
# Do NOT derive this from the HTTP request; it is a server-side configuration value.
# jwtContext=context0
```

All keys are read via `CommonConfiguration.getApiAccessProperty(key, context)`, which reads
only `apiAccessKeys.properties` — never `commonConfiguration.properties`.

---

## 3. Signing Split: Wildbook vs. Kernel

| Component | Holds | Purpose |
|---|---|---|
| Wildbook | **Private key** (`jwtPrivateKeyBase64`) | Signs / mints tokens |
| External scoped-query kernel | **Public key** (`jwtPublicKeyBase64` output) | Verifies token signature |

- Wildbook signs (`RS256`); the kernel verifies. The public key is distributed to the kernel
  **out of band** (config, not an endpoint — a JWKS endpoint is a planned follow-up).
- If `jwtPrivateKeyBase64` is **unset**, `POST /api/v3/auth/token` returns **503
  Service Unavailable** (`{"success":false,"error":"token issuance not configured"}`).
- The public key (`jwtPublicKeyBase64`) is optional from Wildbook's perspective; its absence
  only disables the Wildbook-side `JwtService.verify()` path (used in tests and future auditing).

---

## 4. Key Rotation

1. **Generate a new keypair** (steps 1–3 above). Assign a distinct `jwtKeyId`, e.g. `v2`.
2. **Distribute the new public key** to the kernel. Configure the kernel to accept BOTH
   the old (`v1`) and new (`v2`) public keys, selected by the `kid` JWT header claim.
   The kernel must keep the old public key for **at least one full TTL** after the new key
   is deployed so that in-flight tokens minted with the old key continue to verify.
3. **Update Wildbook** `apiAccessKeys.properties`: set `jwtPrivateKeyBase64` to the
   new private key and set `jwtKeyId=v2`. Reload/restart Wildbook.
4. After the old TTL window has elapsed (all tokens signed with `v1` have expired), remove
   the old public key from the kernel configuration.

---

## 5. Secret Hygiene

- **NEVER commit** the private key (PEM or Base64) to git. It belongs only in the
  data-dir override `apiAccessKeys.properties`, which is excluded from version control.
- **chmod 600** any `.pem` or key file on disk immediately after creation.
- **Never log** the `jwtPrivateKeyBase64` value, the raw PEM, or any minted token string.
  Wildbook's logging does not emit these, but verify any custom log lines before adding them.
- Rotate immediately if there is any suspicion the private key was exposed.
- The Base64 config value should be treated with the same care as a database password.
