import React, { useState } from "react";
import BrutalismButton from "../BrutalismButton";

export default function CollaborationMessages({
  collaborationTitle,
  collaborationData,
  getAllNotifications,
  setModalOpen,
}) {
  // eslint-disable-next-line no-unused-vars
  const [loading, setLoading] = useState(false);
  const [showError, setShowError] = useState(false);
  const [error, setError] = useState("");

  const content = collaborationData?.map((data) => {
    const username = data.getAttribute("data-username");
    const access = data.textContent.includes("view-only")
      ? "View-Only"
      : "Edit";
    const email = data.textContent.match(/\S+@\S+\.\S+/);
    const buttons = [...data.querySelectorAll('input[type="button"]')].map(
      (button) => {
        return {
          class: button.getAttribute("class"),
          value: button.getAttribute("value"),
        };
      },
    );
    const id = data.getAttribute("id");
    const collabString = id ? `&collabId=${id.replace("edit-", "")} :` : "";

    return (
      <div
        key={username}
        style={{
          display: "flex",
          flexDirection: "column",
          padding: "10px",
          borderBottom: "1px solid #ccc",
        }}
      >
        <h6>
          {username} ({access}) {email}
        </h6>
        <div
          style={{
            marginTop: "10px",
            display: "flex",
            flexDirection: "row",
          }}
        >
          {buttons.map((button) => (
            <BrutalismButton
              key={button?.class}
              style={{
                margin: "0 5px 10px 0",
              }}
              onClick={async () => {
                setLoading(true);
                const response = await fetch(
                  `/Collaborate?json=1&username=${username}&approve=${button.class}&actionForExisting=${button.class}${collabString}`,
                );
                const data = await response.json();
                if (data.error) {
                  setShowError(true);
                  setError(data.error);
                } else {
                  setShowError(false);
                  getAllNotifications();
                  setModalOpen(false);
                }
                setLoading(false);
              }}
            >
              {button.value}
            </BrutalismButton>
          ))}
        </div>
      </div>
    );
  });

  return (
    <div
      style={{
        maxHeight: "500px",
        overflow: "auto",
      }}
    >
      {<h4>{collaborationTitle}</h4>}
      {content}
      {showError && <h6>{error}</h6>}
    </div>
  );
}
