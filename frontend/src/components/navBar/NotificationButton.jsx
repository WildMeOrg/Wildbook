import React from "react";
import { Button, Badge } from "react-bootstrap";
import { Bell } from "react-bootstrap-icons";
import Modal from "react-bootstrap/Modal";
import CollaborationMessages from "./CollaborationMessages";
import MergeMessages from "./MergeMessages";
import { FormattedMessage } from "react-intl";

const NotificationButton = ({
  count,
  collaborationTitle,
  collaborationData,
  mergeData,
  getAllNotifications,
}) => {
  const [modalOpen, setModalOpen] = React.useState(false);

  return (
    <div
      style={{
        width: "35px",
        height: "35px",
        marginLeft: "20px",
        position: "relative",
      }}
      tabIndex={0}
    >
      <Modal.Dialog
        style={{
          position: "absolute",
          top: "50px",
          right: "-50px",
          zIndex: "100",
          width: "800px",
          display: modalOpen ? "block" : "none",
          backgroundColor: "#E5F6FF",
          boxShadow: "4px 4px 0px #CCF0FF",
          padding: "20px",
          borderRadius: "10px",
        }}
      >
        <Modal.Body
          style={{
            display: "flex",
            flexDirection: "column",
            maxHeight: "1000px",
            contentOverflow: "scroll",
          }}
        >
          {count > 0 ? (
            <>
              <CollaborationMessages
                collaborationTitle={collaborationTitle}
                collaborationData={collaborationData}
                mergeData={mergeData}
                getAllNotifications={getAllNotifications}
                setModalOpen={setModalOpen}
              />
              <MergeMessages
                mergeData={mergeData}
                getAllNotifications={getAllNotifications}
                setModalOpen={setModalOpen}
              />
            </>
          ) : (
            <h5>
              <FormattedMessage id="NO_NEW_MESSAGE" />
            </h5>
          )}
        </Modal.Body>
      </Modal.Dialog>
      <Button
        style={{
          backgroundColor: "rgba(255, 255, 255, 0.25)",
          border: "none",
          borderRadius: "50%",
          minWidth: "35px",
          height: "35px",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          position: "relative",
          padding: 0,
        }}
        onClick={() => {
          setModalOpen(!modalOpen);
        }}
      >
        <Bell color="white" />
        {count > 0 && (
          <Badge
            pill
            bg="danger"
            style={{
              width: "12px",
              height: "12px",
              position: "absolute",
              top: "22px",
              left: "22px",
              padding: "0 4px",
              fontSize: "0.5rem",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            {count}
          </Badge>
        )}
      </Button>
    </div>
  );
};

export default NotificationButton;
