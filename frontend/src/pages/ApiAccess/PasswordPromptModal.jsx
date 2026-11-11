import React from "react";
import { useIntl } from "react-intl";
import { Button, Modal, Form, Alert } from "react-bootstrap";

// The fresh-password prompt every service key call needs. Purely presentational:
// the caller owns the password, error and busy state.
export default function PasswordPromptModal({
  show,
  password,
  setPassword,
  error,
  busy,
  onCancel,
  onSubmit,
  inputId = "svc-pw",
}) {
  const intl = useIntl();
  const t = (id) => intl.formatMessage({ id });
  return (
    <Modal show={show} onHide={onCancel}>
      <Form onSubmit={onSubmit}>
        <Modal.Header closeButton>
          <Modal.Title>{t("API_SERVICE_KEY_PASSWORD_TITLE")}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <p>{t("API_SERVICE_KEY_PASSWORD_PROMPT")}</p>
          {error && <Alert variant="danger">{error}</Alert>}
          <Form.Group>
            <Form.Label htmlFor={inputId}>
              {t("API_SERVICE_KEY_PASSWORD")}
            </Form.Label>
            <Form.Control
              id={inputId}
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
          </Form.Group>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" onClick={onCancel}>
            {t("API_SERVICE_KEY_CANCEL")}
          </Button>
          <Button type="submit" disabled={busy || !password}>
            {t("API_SERVICE_KEY_CONFIRM")}
          </Button>
        </Modal.Footer>
      </Form>
    </Modal>
  );
}
