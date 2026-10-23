import React, { useState } from "react";
import { useIntl } from "react-intl";
import { Button, Modal, Form, Alert, Table } from "react-bootstrap";
import {
  listManageableOrgs,
  listKeys,
  createKey,
  revokeKey,
  revokeAll,
} from "../../models/auth/serviceKeysApi";

export const SCOPES = ["data:read", "submissions:read", "submissions:write"];
const DEFAULT_DAYS = 90;

// The ONE place that decides who sees the entry button. This is only a UI
// hint; the server makes the real decision on every call.
export function canManageServiceKeys(me) {
  const roles = me?.data?.roles || me?.roles;
  return Array.isArray(roles) && roles.includes("admin");
}

const fmt = (ms) => (ms ? new Date(ms).toLocaleString() : "-");

export default function ServiceKeysPanel({ username }) {
  const intl = useIntl();
  const t = (id, values) => intl.formatMessage({ id }, values);

  // auth lives in component state only while the panel is open.
  const [auth, setAuth] = useState(null);
  const [orgs, setOrgs] = useState([]);
  const [orgId, setOrgId] = useState("");
  const [keys, setKeys] = useState([]);
  const [created, setCreated] = useState(null);
  const [error, setError] = useState(null);

  const [label, setLabel] = useState("");
  const [scopes, setScopes] = useState(["data:read"]);
  const [days, setDays] = useState(String(DEFAULT_DAYS));

  // pending = {run(authObj)} while the password prompt is open
  const [pending, setPending] = useState(null);
  const [password, setPassword] = useState("");
  const [promptError, setPromptError] = useState(null);
  const [busy, setBusy] = useState(false);

  const daysNum = Number(days);
  const daysValid =
    days !== "" && Number.isInteger(daysNum) && daysNum >= 1 && daysNum <= 365;

  const errorText = (err) =>
    err.status === 409
      ? t("API_SERVICE_KEY_LIMIT")
      : err.status === 403
        ? t("API_SERVICE_KEY_FORBIDDEN")
        : t("API_SERVICE_KEY_ERROR");

  const closePanel = () => {
    setAuth(null);
    setOrgs([]);
    setOrgId("");
    setKeys([]);
    setCreated(null);
    setError(null);
    setLabel("");
    setScopes(["data:read"]);
    setDays(String(DEFAULT_DAYS));
  };

  const askPassword = (run) => {
    setPassword("");
    setPromptError(null);
    setPending({ run });
  };

  const cancelPrompt = () => {
    setPassword("");
    setPromptError(null);
    setPending(null);
  };

  const submitPassword = async (e) => {
    e.preventDefault();
    const creds = { username, password };
    setBusy(true);
    setPromptError(null);
    let after = null;
    try {
      // run() performs the mutation and returns an optional follow-up.
      after = await pending.run(creds);
      setPassword("");
      setPending(null);
    } catch (err) {
      setPromptError(
        err.status === 401 ? t("API_SERVICE_KEY_BAD_PASSWORD") : errorText(err),
      );
      setPassword("");
      setBusy(false);
      return;
    }
    setBusy(false);
    // The mutation succeeded and the prompt is closed; a failing refresh is a
    // panel error and must never bring the prompt back (no duplicate create).
    if (after) {
      try {
        await after();
      } catch (err) {
        setError(errorText(err));
      }
    }
  };

  const refresh = async (creds, id) => {
    setKeys(await listKeys(creds, id));
  };

  const discover = async (creds) => {
    const list = await listManageableOrgs(creds);
    setOrgs(list);
    setAuth(creds);
    if (list.length > 0) {
      setOrgId(list[0].id);
      return () => refresh(creds, list[0].id);
    }
  };

  const onOrgChange = async (id) => {
    setOrgId(id);
    setCreated(null);
    setError(null);
    setKeys([]);
    try {
      await refresh(auth, id);
    } catch (err) {
      setError(errorText(err));
    }
  };

  const doCreate = (e) => {
    e.preventDefault();
    if (!daysValid || !label.trim() || scopes.length === 0) return;
    askPassword(async (creds) => {
      const res = await createKey(creds, orgId, {
        label: label.trim(),
        scopes,
        days: daysNum,
      });
      setAuth(creds);
      setCreated(res);
      setError(null);
      setLabel("");
      return () => refresh(creds, orgId);
    });
  };

  const doRevoke = (k) => {
    if (!window.confirm(t("API_SERVICE_KEY_CONFIRM_REVOKE", { label: k.label })))
      return;
    askPassword(async (creds) => {
      await revokeKey(creds, orgId, k.keyId);
      setAuth(creds);
      setError(null);
      return () => refresh(creds, orgId);
    });
  };

  const doRevokeAll = () => {
    if (!window.confirm(t("API_SERVICE_KEY_CONFIRM_REVOKE_ALL"))) return;
    askPassword(async (creds) => {
      await revokeAll(creds, orgId);
      setAuth(creds);
      setError(null);
      return () => refresh(creds, orgId);
    });
  };

  const toggleScope = (s) =>
    setScopes((cur) =>
      cur.includes(s) ? cur.filter((x) => x !== s) : [...cur, s],
    );

  const status = (k) =>
    k.revokedAt
      ? t("API_SERVICE_KEY_STATUS_REVOKED")
      : k.expiresAt && k.expiresAt <= Date.now()
        ? t("API_SERVICE_KEY_STATUS_EXPIRED")
        : t("API_SERVICE_KEY_STATUS_ACTIVE");

  const isLive = (k) =>
    !k.revokedAt && !(k.expiresAt && k.expiresAt <= Date.now());

  return (
    <div style={{ marginTop: "2rem" }}>
      <Button
        variant="outline-primary"
        onClick={() => askPassword(discover)}
        disabled={!username}
      >
        {t("API_SERVICE_KEYS_MANAGE")}
      </Button>

      <Modal show={!!auth} onHide={closePanel} size="lg">
        <Modal.Header closeButton>
          <Modal.Title>{t("API_SERVICE_KEYS_TITLE")}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <p>{t("API_SERVICE_KEYS_INTRO")}</p>
          {error && <Alert variant="danger">{error}</Alert>}
          {orgs.length === 0 ? (
            <Alert variant="info">{t("API_SERVICE_KEYS_NONE")}</Alert>
          ) : (
            <>
              <Form.Group className="mb-3" controlId="svc-org">
                <Form.Label>{t("API_SERVICE_KEYS_ORG")}</Form.Label>
                <Form.Select
                  value={orgId}
                  onChange={(e) => onOrgChange(e.target.value)}
                >
                  {orgs.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.name}
                    </option>
                  ))}
                </Form.Select>
              </Form.Group>

              {created && (
                <Alert variant="success">
                  <p>{t("API_SERVICE_KEY_SHOWN_ONCE")}</p>
                  <div style={{ display: "flex", gap: 8 }}>
                    <code
                      data-testid="created-key"
                      style={{ wordBreak: "break-all", flex: 1 }}
                    >
                      {created.key}
                    </code>
                    <Button
                      size="sm"
                      onClick={() => navigator.clipboard?.writeText(created.key)}
                    >
                      {t("API_SERVICE_KEY_COPY")}
                    </Button>
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => setCreated(null)}
                    >
                      {t("API_SERVICE_KEY_DONE")}
                    </Button>
                  </div>
                </Alert>
              )}

              <Table size="sm" responsive>
                <thead>
                  <tr>
                    <th>{t("API_SERVICE_KEY_LABEL")}</th>
                    <th>{t("API_SERVICE_KEY_SCOPES")}</th>
                    <th>{t("API_SERVICE_KEY_EXPIRES")}</th>
                    <th>{t("API_SERVICE_KEY_STATUS")}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {keys.map((k) => (
                    <tr key={k.keyId}>
                      <td>{k.label}</td>
                      <td>{(k.scopes || []).join(", ")}</td>
                      <td>{fmt(k.expiresAt)}</td>
                      <td>{status(k)}</td>
                      <td>
                        {isLive(k) && (
                          <Button
                            size="sm"
                            variant="outline-danger"
                            onClick={() => doRevoke(k)}
                          >
                            {t("API_SERVICE_KEY_REVOKE")}
                          </Button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </Table>
              {keys.some(isLive) && (
                <Button
                  size="sm"
                  variant="danger"
                  className="mb-3"
                  onClick={doRevokeAll}
                >
                  {t("API_SERVICE_KEY_REVOKE_ALL")}
                </Button>
              )}

              <h5>{t("API_SERVICE_KEY_CREATE")}</h5>
              <Form onSubmit={doCreate}>
                <Form.Group className="mb-2" controlId="svc-label">
                  <Form.Label>{t("API_SERVICE_KEY_LABEL")}</Form.Label>
                  <Form.Control
                    value={label}
                    maxLength={100}
                    onChange={(e) => setLabel(e.target.value)}
                  />
                </Form.Group>
                <Form.Group className="mb-2">
                  <Form.Label>{t("API_SERVICE_KEY_SCOPES")}</Form.Label>
                  {SCOPES.map((s) => (
                    <Form.Check
                      key={s}
                      type="checkbox"
                      id={`svc-scope-${s}`}
                      label={s}
                      checked={scopes.includes(s)}
                      onChange={() => toggleScope(s)}
                    />
                  ))}
                  {scopes.includes("submissions:write") && (
                    <Form.Text>{t("API_SERVICE_KEY_WRITE_NEEDS_ADMIN")}</Form.Text>
                  )}
                </Form.Group>
                <Form.Group className="mb-3" controlId="svc-days">
                  <Form.Label>{t("API_SERVICE_KEY_DAYS")}</Form.Label>
                  <Form.Control
                    type="number"
                    min={1}
                    max={365}
                    value={days}
                    isInvalid={!daysValid}
                    onChange={(e) => setDays(e.target.value)}
                  />
                  <Form.Control.Feedback type="invalid">
                    {t("API_SERVICE_KEY_DAYS_INVALID")}
                  </Form.Control.Feedback>
                </Form.Group>
                <Button
                  type="submit"
                  disabled={!daysValid || !label.trim() || scopes.length === 0}
                >
                  {t("API_SERVICE_KEY_CREATE")}
                </Button>
              </Form>
            </>
          )}
        </Modal.Body>
      </Modal>

      <Modal show={!!pending} onHide={cancelPrompt}>
        <Form onSubmit={submitPassword}>
          <Modal.Header closeButton>
            <Modal.Title>{t("API_SERVICE_KEY_PASSWORD_TITLE")}</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <p>{t("API_SERVICE_KEY_PASSWORD_PROMPT")}</p>
            {promptError && <Alert variant="danger">{promptError}</Alert>}
            <Form.Group>
              <Form.Label htmlFor="svc-pw">
                {t("API_SERVICE_KEY_PASSWORD")}
              </Form.Label>
              <Form.Control
                id="svc-pw"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
            </Form.Group>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" onClick={cancelPrompt}>
              {t("API_SERVICE_KEY_CANCEL")}
            </Button>
            <Button type="submit" disabled={busy || !password}>
              {t("API_SERVICE_KEY_CONFIRM")}
            </Button>
          </Modal.Footer>
        </Form>
      </Modal>
    </div>
  );
}
