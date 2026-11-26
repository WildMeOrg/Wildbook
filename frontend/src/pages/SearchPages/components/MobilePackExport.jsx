import React, { useEffect, useState } from "react";
import { Alert, Button, Card, Form, Spinner } from "react-bootstrap";
import { FormattedMessage, useIntl } from "react-intl";

export default function MobilePackExport({ searchQueryId }) {
  const intl = useIntl();
  const [configuration, setConfiguration] = useState(null);
  const [displayName, setDisplayName] = useState("");
  const [profileIndex, setProfileIndex] = useState("");
  const [packId, setPackId] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [job, setJob] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    fetch("/api/v3/mobile-packs", { signal: controller.signal })
      .then((response) => response.ok ? response.json() : null)
      .then(setConfiguration).catch(() => {});
    return () => controller.abort();
  }, []);
  useEffect(() => {
    if (!job?.jobId || ["ready", "failed"].includes(job.status)) return;
    let stopped = false;
    let timer;
    const controller = new AbortController();
    const poll = async () => {
      try {
        const response = await fetch(`/api/v3/mobile-packs?jobId=${encodeURIComponent(job.jobId)}`, { signal: controller.signal });
        if (!response.ok) throw new Error("status unavailable");
        const status = await response.json();
        if (!stopped) setJob(status);
      } catch {
        if (!stopped) setError(true);
      }
      if (!stopped) timer = setTimeout(poll, 3000);
    };
    timer = setTimeout(poll, 1000);
    return () => { stopped = true; clearTimeout(timer); controller.abort(); };
  }, [job?.jobId, job?.status]);
  if (!configuration?.enabled || !configuration.profiles?.length) return null;
  const publishing = job && !["ready", "failed"].includes(job.status);
  // The selected profile is sent as taxonomy + feature class so the worker can refuse a mismatched search.
  const profile = profileIndex === "" ? null : configuration.profiles[Number(profileIndex)];
  const publish = async () => {
    setBusy(true); setError(false);
    try {
      const response = await fetch("/api/v3/mobile-packs", {
        method: "POST", headers: { "Content-Type": "application/json", "X-Requested-With": "XMLHttpRequest" },
        body: JSON.stringify({ searchQueryId, displayName, taxonomy: profile.taxonomy, featureClass: profile.featureClass, confirmPublic: confirmed,
          ...(packId.trim() ? { packId: packId.trim() } : {}) }),
      });
      if (!response.ok) throw new Error("publication failed");
      const result = await response.json();
      setPackId(result.packId);
      setJob({ ...result, status: "queued" });
    } catch { setError(true); }
    finally { setBusy(false); }
  };
  return <Card className="shadow-sm mb-3"><Card.Body>
    <Card.Title><FormattedMessage id="MOBILE_PACK_TITLE" /></Card.Title>
    <Form.Group className="mb-2"><Form.Label><FormattedMessage id="MOBILE_PACK_NAME" /></Form.Label>
      <Form.Control value={displayName} maxLength={200} onChange={(event) => setDisplayName(event.target.value)} disabled={busy || publishing} />
    </Form.Group>
    <Form.Group className="mb-2"><Form.Label><FormattedMessage id="MOBILE_PACK_PIPELINE" /></Form.Label>
      <Form.Select value={profileIndex} onChange={(event) => setProfileIndex(event.target.value)} disabled={busy || publishing}>
        <option value="">{intl.formatMessage({ id: "MOBILE_PACK_SELECT" })}</option>
        {configuration.profiles.map((choice, index) => <option key={choice.label} value={index}>{choice.label}</option>)}
      </Form.Select>
    </Form.Group>
    <Form.Group className="mb-2"><Form.Label><FormattedMessage id="MOBILE_PACK_ID" /></Form.Label>
      <Form.Control value={packId} maxLength={40} onChange={(event) => setPackId(event.target.value)} disabled={busy || publishing} />
    </Form.Group>
    <Form.Check className="mb-3" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)}
      label={intl.formatMessage({ id: "MOBILE_PACK_PUBLIC_CONFIRM" })} disabled={busy || publishing} />
    <Button onClick={publish} disabled={busy || publishing || !confirmed || !displayName.trim() || !profile}>
      {(busy || publishing) && <Spinner size="sm" className="me-2" />}
      <FormattedMessage id="MOBILE_PACK_PUBLISH" />
    </Button>
    {error && <Alert variant="danger" className="mt-2"><FormattedMessage id="MOBILE_PACK_ERROR" /></Alert>}
    {job?.status === "failed" && <Alert variant="danger" className="mt-2">{job.message}</Alert>}
    {job?.status === "ready" && <Alert variant="success" className="mt-2">
      <FormattedMessage id="MOBILE_PACK_READY" /> {job.publication.code}
      <p><a href={job.publication.url} target="_blank" rel="noreferrer">{job.publication.url}</a></p>
      <img src={job.publication.qrUrl} width="180" height="180" alt={intl.formatMessage({ id: "MOBILE_PACK_QR" })} />
      <p><FormattedMessage id="MOBILE_PACK_SKIPPED" values={{ count: job.skippedCount }} /></p>
    </Alert>}
  </Card.Body></Card>;
}
