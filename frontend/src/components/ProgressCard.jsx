import React from "react";
import { Card } from "react-bootstrap";
import {
  CircularProgressbarWithChildren,
  buildStyles,
} from "react-circular-progressbar";
import { FaCheck } from "react-icons/fa";
import "react-circular-progressbar/dist/styles.css";
import { FormattedMessage } from "react-intl";

export const ProgressCard = ({
  title,
  progress = 0,
  status = "not_started",
}) => {
  const isComplete = progress === 1;

  return (
    <Card
      className="text-center shadow-sm"
      style={{ width: 130, height: 150, border: "none", borderRadius: 12 }}
    >
      <Card.Body className="d-flex flex-column align-items-center justify-content-center gap-2 p-3">
        <div style={{ width: 56, height: 56 }}>
          {isComplete ? (
            <div
              style={{
                width: "100%",
                height: "100%",
                borderRadius: "50%",
                border: "2px solid #B6EEFF",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <FaCheck size={22} color="#15B28C" />
            </div>
          ) : (
            <CircularProgressbarWithChildren
              value={progress * 100}
              strokeWidth={6}
              background
              backgroundPadding={4}
              styles={buildStyles({
                pathColor: "#38BDF8",
                trailColor: "#E6F7FF",
                backgroundColor: "#FFFFFF",
              })}
            >
              <span style={{ fontSize: 11 }}>
                {`${(progress * 100).toFixed(0)}%`}
              </span>
            </CircularProgressbarWithChildren>
          )}
        </div>

        <div>
          <div style={{ fontSize: 14, fontWeight: 500 }}>{title}</div>
          <div style={{ fontSize: 12, color: "#6B7280" }}>
            <FormattedMessage
              id={`BULK_IMPORT_TASK_STATUS_${status.split(" ").join("_").toLowerCase()}`}
              defaultMessage={status}
            />
          </div>
        </div>
      </Card.Body>
    </Card>
  );
};
