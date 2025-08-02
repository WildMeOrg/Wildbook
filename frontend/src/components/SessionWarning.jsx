import React, { useState, useEffect } from "react";
import { Modal, Button } from "react-bootstrap";
import { useIntl } from "react-intl";

const SessionWarningModal = ({
  sessionWarningTime = 20,
  sessionCountdownTime = 10,
}) => {
  const intl = useIntl();

  const title = intl.formatMessage({ id: "SESSION_WARNING_TITLE" });
  const originalContent = intl.formatMessage({ id: "SESSION_WARNING_CONTENT" });
  const countdownContent = intl.formatMessage({
    id: "SESSION_LOGIN_MODAL_CONTENT",
  });
  const extendButton = intl.formatMessage({ id: "SESSION_EXTEND" });
  const loginButton = intl.formatMessage({ id: "SESSION_LOGIN" });
  const closeButtonText = intl.formatMessage({ id: "SESSION_CLOSE" });

  const [showModal, setShowModal] = useState(false);
  const [countdown, setCountdown] = useState(sessionCountdownTime * 60);
  const [countdownInterval, setCountdownInterval] = useState(null);
  const [extendButtonText, setExtendButtonText] = useState(extendButton);
  const [modalText, setModalText] = useState(originalContent);
  const [action, setAction] = useState("extendSession");

  const activityTimeout = sessionWarningTime * 60 * 1000;

  const showWarning = () => {
    const now = Date.now();
    const lastActivityTimestamp = localStorage.getItem("lastActivity");
    const timeSinceLastActivity = now - lastActivityTimestamp;

    if (timeSinceLastActivity < activityTimeout) {
      setShowModal(false);
      startSessionTimer();
      return;
    }

    startCountdown();
    setShowModal(true);
  };

  const handleSessionButtonClick = () => {
    if (action === "login") {
      window.open(`${process.env.PUBLIC_URL}/login/`, "_blank");
    } else {
      fetch(`${window.wildbookGlobals?.baseUrl}../ExtendSession`)
        .then((res) => res.json())
        .then(() => {
          localStorage.setItem("sessionExtended", Date.now().toString());
          setShowModal(false);
          clearInterval(countdownInterval);
          resetActivity();
        })
        .catch((error) => console.warn("Error extending session:", error));
    }
  };

  const startSessionTimer = () => {
    setExtendButtonText(extendButton);
    setAction("extendSession");
    setModalText(originalContent);
    setShowModal(false);
    clearTimeout(countdownInterval);
    setCountdownInterval(setTimeout(showWarning, activityTimeout));
  };

  const resetActivity = () => {
    setCountdown(0);
    const now = Date.now();
    localStorage.setItem("lastActivity", now.toString());
    startSessionTimer();
  };

  const startCountdown = () => {
    const warningCountdownTime = sessionCountdownTime * 60 * 1000;

    const interval = setInterval(() => {
      const now = Date.now();
      const lastActivityTimestamp = parseInt(
        localStorage.getItem("lastActivity"),
        10,
      );
      const countdownTime =
        lastActivityTimestamp + activityTimeout + warningCountdownTime - now;
      const secondsRemaining = Math.floor(countdownTime / 1000);

      if (countdownTime < 0) {
        clearInterval(interval);
        setExtendButtonText(loginButton);
        setAction("login");
        setModalText(countdownContent);
        setCountdown(0);
      } else {
        setCountdown(secondsRemaining);
      }
    }, 1000);

    setCountdownInterval(interval);
  };

  const formatCountdown = () => {
    if (countdown === 0) {
      return "";
    }
    const minutes = Math.floor(countdown / 60);
    const secondsLeft = countdown % 60;
    return `${minutes}:${secondsLeft < 10 ? "0" : ""}${secondsLeft}`;
  };

  useEffect(() => {
    const handleStorage = (e) => {
      if (e.key === "lastActivity") {
        startSessionTimer();
      } else if (e.key === "sessionExtended") {
        resetActivity();
      }
    };

    window.addEventListener("storage", handleStorage);

    resetActivity();

    return () => {
      window.removeEventListener("storage", handleStorage);
    };
  }, []);

  return (
    <>
      <Modal show={showModal} onHide={() => setShowModal(false)}>
        <Modal.Header closeButton>
          <Modal.Title>{title}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <p>
            {modalText}
            {formatCountdown()}
          </p>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="primary" onClick={handleSessionButtonClick}>
            {extendButtonText}
          </Button>
          <Button
            variant="secondary"
            onClick={() => {
              setShowModal(false);
              // setCountdown(0);
              // clearInterval(countdownInterval);
              // resetActivity();
            }}
          >
            {closeButtonText}
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
};

export default SessionWarningModal;
