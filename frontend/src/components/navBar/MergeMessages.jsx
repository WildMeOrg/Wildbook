import React, { useState } from "react";
import BrutalismButton from "../BrutalismButton";
import { FormattedMessage } from "react-intl";

export default function MergeMessages({
  mergeData,
  getAllNotifications,
  setModalOpen,
}) {
  const handleClick = () => {
    // const result = changeIndividualMergeState(action, taskId);
    // setError('Error: ' + result);
    getAllNotifications();
    setModalOpen(false);
  };

  // eslint-disable-next-line no-unused-vars
  const [showError, setShowError] = useState(false);
  // eslint-disable-next-line no-unused-vars
  const [error, setError] = useState("");

  const content = mergeData?.map((data) => {
    const mergePending = data.notificationType === "mergePending";
    const mergeComplete = data.notificationType === "mergeComplete";
    const mergeDenied = data.notificationType === "mergeDenied";
    const ownedByMe = data.ownedByMe === "true";

    return (
      <div
        key={data?.taskId}
        style={{
          borderBottom: "1px solid #ccc",
          padding: "10px 0 0 10px",
        }}
      >
        {mergePending && (
          <p>
            <FormattedMessage
              id="MERGE_PENDING_MESSAGE"
              values={{
                indv1: data.primaryIndividualName,
                indv2: data.secondaryIndividualName,
                initiator: data.initiator,
                mergeDate: data.mergeExecutionDate,
                bold: (chunks) => <strong>{chunks}</strong>,
              }}
            />
          </p>
        )}

        {mergeComplete && (
          <p>
            <FormattedMessage
              id="MERGE_COMPLETE_MESSAGE"
              values={{
                indv1: data.primaryIndividualName,
                indv2: data.secondaryIndividualName,
                initiator: data.initiator,
                mergeDate: data.mergeExecutionDate,
                bold: (chunks) => <strong>{chunks}</strong>,
              }}
            />
          </p>
        )}

        {mergeDenied && (
          <p>
            <FormattedMessage
              id="MERGE_DENIED_MESSAGE"
              values={{
                indv1: data.primaryIndividualName,
                indv2: data.secondaryIndividualName,
                initiator: data.initiator,
                deniedBy: data.deniedBy,
                mergeDate: data.mergeExecutionDate,
                bold: (chunks) => <strong>{chunks}</strong>,
              }}
            />
          </p>
        )}

        {!ownedByMe && mergePending && (
          <div
            style={{
              display: "flex",
              flexDirection: "row",
              marginTop: "10px",
              marginBottom: "10px",
            }}
          >
            <BrutalismButton onClick={() => handleClick("ignore", data.taskId)}>
              <FormattedMessage id="IGNORE" />
            </BrutalismButton>
            <BrutalismButton onClick={() => handleClick("deny", data.taskId)}>
              <FormattedMessage id="DENY" />
            </BrutalismButton>
          </div>
        )}

        {mergeComplete && (
          <div
            style={{
              display: "flex",
              marginTop: "10px",
              marginBottom: "10px",
              // width: 105
            }}
          >
            <BrutalismButton onClick={() => handleClick("ignore", data.taskId)}>
              <FormattedMessage id="DISMISS" />
            </BrutalismButton>
          </div>
        )}

        {mergeDenied && (
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              marginTop: "10px",
              marginBottom: "10px",
              width: "112px",
            }}
          >
            <BrutalismButton onClick={() => handleClick("ignore", data.taskId)}>
              <FormattedMessage id="DISMISS" />
            </BrutalismButton>
          </div>
        )}

        {ownedByMe ? (
          <p>
            <FormattedMessage
              id="INITIATED_BY_USER"
              values={{
                user: "current user",
                bold: (chunks) => <strong>{chunks}</strong>,
              }}
            />
          </p>
        ) : (
          <p>
            <FormattedMessage
              id="INITIATED_BY_USER"
              values={{
                user: data.initiator,
                bold: (chunks) => <strong>{chunks}</strong>,
              }}
            />
          </p>
        )}
      </div>
    );
  });

  return (
    <div
      style={{
        maxHeight: "500px",
        overflow: "auto",
        marginTop: "20px",
      }}
    >
      {mergeData.length > 0 && (
        <h4>
          <FormattedMessage id="INDIVIDUAL_MERGE_NOTIFICATIONS" />
        </h4>
      )}
      {content}
      {showError && <h6>{error}</h6>}
    </div>
  );
}
