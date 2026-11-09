import React, { useState } from "react";
import { useIntl } from "react-intl";
import { Button, Modal, Alert, Table } from "react-bootstrap";
import PasswordPromptModal from "./PasswordPromptModal";
import { listMyReadAsKeys, revokeKey } from "../../models/auth/serviceKeysApi";

const fmt = (ms) => (ms ? new Date(ms).toLocaleString() : "-");

// Keys that read with the signed-in user's access. Shown to every signed-in
// user: the list needs a fresh password, so the page cannot know beforehand
// whether any exist. The creator may revoke these without managing the
// organization.
export default function MyReadAsKeysPanel({ username }) {
  const intl = useIntl();
  const t = (id, values) => intl.formatMessage({ id }, values);

  const [auth, setAuth] = useState(null);
  const [keys, setKeys] = useState([]);
  const [error, setError] = useState(null);

  const [pending, setPending] = useState(null);
  const [password, setPassword] = useState("");
  const [promptError, setPromptError] = useState(null);
  const [busy, setBusy] = useState(false);

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
    try {
      await pending.run(creds);
      setPassword("");
      setPending(null);
    } catch (err) {
      setPromptError(
        err.status === 401
          ? t("API_SERVICE_KEY_BAD_PASSWORD")
          : t("API_SERVICE_KEY_ERROR"),
      );
      setPassword("");
    }
    setBusy(false);
  };

  const load = async (creds) => {
    const list = await listMyReadAsKeys(creds);
    setKeys(list);
    setAuth(creds);
    setError(null);
  };

  const close = () => {
    setAuth(null);
    setKeys([]);
    setError(null);
  };

  const doRevoke = (k) => {
    if (
      !window.confirm(t("API_SERVICE_KEY_CONFIRM_REVOKE", { label: k.label }))
    )
      return;
    askPassword(async (creds) => {
      await revokeKey(creds, k.organizationId, k.keyId);
      try {
        await load(creds);
      } catch (_err) {
        // the revoke succeeded; only the refresh failed
        setError(t("API_SERVICE_KEY_ERROR"));
      }
    });
  };

  return (
    <div style={{ marginTop: "1rem" }}>
      <Button
        variant="outline-secondary"
        onClick={() => askPassword(load)}
        disabled={!username}
      >
        {t("API_MY_READ_AS_KEYS")}
      </Button>

      <Modal show={!!auth} onHide={close} size="lg">
        <Modal.Header closeButton>
          <Modal.Title>{t("API_MY_READ_AS_KEYS")}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <p>{t("API_MY_READ_AS_KEYS_INTRO")}</p>
          {error && <Alert variant="danger">{error}</Alert>}
          {keys.length === 0 ? (
            <Alert variant="info">{t("API_MY_READ_AS_KEYS_NONE")}</Alert>
          ) : (
            <Table size="sm" responsive>
              <thead>
                <tr>
                  <th>{t("API_SERVICE_KEY_LABEL")}</th>
                  <th>{t("API_SERVICE_KEY_SCOPES")}</th>
                  <th>{t("API_SERVICE_KEY_EXPIRES")}</th>
                  <th>{t("API_SERVICE_KEY_LAST_USED")}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {keys.map((k) => (
                  <tr key={k.keyId}>
                    <td>{k.label}</td>
                    <td>{(k.scopes || []).join(", ")}</td>
                    <td>{fmt(k.expiresAt)}</td>
                    <td>
                      {k.lastUsedAt
                        ? fmt(k.lastUsedAt)
                        : t("API_SERVICE_KEY_NEVER_USED")}
                    </td>
                    <td>
                      <Button
                        size="sm"
                        variant="outline-danger"
                        onClick={() => doRevoke(k)}
                      >
                        {t("API_SERVICE_KEY_REVOKE")}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Modal.Body>
      </Modal>

      <PasswordPromptModal
        show={!!pending}
        password={password}
        setPassword={setPassword}
        error={promptError}
        busy={busy}
        onCancel={cancelPrompt}
        onSubmit={submitPassword}
        inputId="svc-mine-pw"
      />
    </div>
  );
}
