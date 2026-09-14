import React, { useState } from "react";
import { Button, Modal, Form, Alert } from "react-bootstrap";
import useGetMe from "../../models/auth/users/useGetMe";
import useMintToken from "../../models/auth/useMintToken";

const SKILL_URL = "/api/v3/agent-skill";

export default function ApiAccessPage() {
  const me = useGetMe();
  const username = me?.data?.username || "";
  const { mint, loading } = useMintToken();

  const [showModal, setShowModal] = useState(false);
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [token, setToken] = useState(null);
  const [expiresIn, setExpiresIn] = useState(null);

  const openModal = () => {
    setError(null);
    setPassword("");
    setShowModal(true);
  };

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      const res = await mint(username, password);
      setToken(res.token);
      setExpiresIn(res.expiresInSeconds);
      setPassword("");
      setShowModal(false);
    } catch (err) {
      if (err.status === 401)
        setError(
          "Incorrect password. If your account uses single sign-on, API tokens aren't available yet.",
        );
      else if (err.status === 503)
        setError("Token issuance isn't enabled on this server.");
      else setError("Couldn't generate a token. Please try again.");
    }
  };

  return (
    <div className="container" style={{ maxWidth: 720, padding: "2rem 1rem" }}>
      <h2>API Access</h2>
      <p>
        Generate a short-lived token so an AI agent or script can act with{" "}
        <strong>your</strong> Wildbook access. Treat it like a password.
      </p>
      <Alert variant="warning">
        Do <strong>not</strong> give your agent your username/password &mdash;
        paste only the token.
      </Alert>
      <p>
        Your agent can learn this API here: <code>{SKILL_URL}</code>{" "}
        <Button
          size="sm"
          variant="link"
          onClick={() =>
            navigator.clipboard?.writeText(
              window.location.origin + SKILL_URL,
            )
          }
        >
          Copy link
        </Button>
      </p>

      <Button onClick={openModal} disabled={!username}>Generate API token</Button>

      {token && (
        <div style={{ marginTop: "1.5rem" }}>
          <Alert variant="success">
            Copy this token now &mdash; it won&apos;t be shown again
            {expiresIn
              ? ` and expires in ~${Math.round(expiresIn / 60)} min`
              : ""}
            .
          </Alert>
          <div style={{ display: "flex", gap: 8 }}>
            <code style={{ wordBreak: "break-all", flex: 1 }}>{token}</code>
            <Button
              size="sm"
              onClick={() => navigator.clipboard?.writeText(token)}
            >
              Copy
            </Button>
          </div>
        </div>
      )}

      <Modal show={showModal} onHide={() => setShowModal(false)}>
        <Form onSubmit={submit}>
          <Modal.Header closeButton>
            <Modal.Title>Confirm your password</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <p>
              Re-enter your password to mint a token for{" "}
              <strong>{username}</strong>.
            </p>
            {error && <Alert variant="danger">{error}</Alert>}
            <Form.Group>
              <Form.Label htmlFor="apitoken-pw">Password</Form.Label>
              <Form.Control
                id="apitoken-pw"
                type="password"
                aria-label="Password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
            </Form.Group>
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" onClick={() => setShowModal(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={loading || !password || !username}>
              Confirm
            </Button>
          </Modal.Footer>
        </Form>
      </Modal>
    </div>
  );
}
