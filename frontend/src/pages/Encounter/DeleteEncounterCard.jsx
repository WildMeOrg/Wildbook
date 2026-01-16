import React from "react";
import MainButton from "../../components/MainButton";
import axios from "axios";
import { toast } from "react-toastify";
import Modal from "react-bootstrap/Modal";
import { FormattedMessage } from "react-intl";
import ThemeColorContext from "../../ThemeColorProvider";

export default function DeleteEncounterCard({
  id,
  individualId = null,
  setEncounterDeleted = () => {},
}) {
  const theme = React.useContext(ThemeColorContext);
  const [unassignModalShow, setUnassignModalShow] = React.useState(false);
  const [deleteModalShow, setDeleteModalShow] = React.useState(false);
  const [loading, setLoading] = React.useState(false);

  const handleClick = () => {
    if (individualId) {
      setUnassignModalShow(true);
    } else {
      setDeleteModalShow(true);
    }
  };

  const handleUnassignAndDelete = async () => {
    setLoading(true);
    const ops = [
      {
        op: "remove",
        path: "individualId",
        value: individualId,
      },
    ];
    try {
      const result = await axios.patch(`/api/v3/encounters/${id}`, ops);
      if (result.status === 200) {
        toast.success("Individual unassigned from encounter successfully");
        await handleDelete();
      }
    } catch (error) {
      toast.error("Failed to remove individual from encounter");
      throw error;
    } finally {
      setUnassignModalShow(false);
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    setLoading(true);
    const params = new URLSearchParams();
    params.append("number", id);
    params.append("approve", "Delete Encounter?");
    try {
      const result = await axios.post("/EncounterDelete", params, {
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
        },
      });

      if (result.status === 200) {
        setEncounterDeleted("success");
        toast.success("Encounter deleted successfully");
      }
    } catch (error) {
      setEncounterDeleted("error");
      toast.error("An error occurred while deleting the encounter");
      throw error;
    } finally {
      setDeleteModalShow(false);
      setUnassignModalShow(false);
      setLoading(false);
    }
  };

  return (
    <div className="delete-encounter-card mb-3">
      <div className="card-header d-flex flex-row align-items-center mb-3">
        <div
          style={{
            backgroundColor: "#efd9dbff",
            color: "#e80d23ff",
            padding: "10px",
            borderRadius: "50%",
            textAlign: "center",
            width: "30px",
            height: "30px",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            marginRight: "10px",
          }}
        >
          <i className="bi bi-exclamation-triangle-fill"></i>
        </div>
        <div className="d-flex align-items-center text-align-center">
          <FormattedMessage id="DANGER_ZONE" />
        </div>
      </div>
      <p>
        <FormattedMessage id="DELETE_ENCOUNTER" />?
      </p>
      <p>
        <FormattedMessage id="DELETE_ENCOUNTER_DESC" />
      </p>
      <MainButton
        onClick={handleClick}
        backgroundColor="#e80d23ff"
        color="#ffffff"
        shadowColor="#69050fff"
        style={{ marginLeft: 0 }}
        noArrow={true}
        disabled={loading}
      >
        <FormattedMessage id="DELETE_ENCOUNTER" />
      </MainButton>
      <Modal
        centered
        show={deleteModalShow}
        onHide={() => setDeleteModalShow(false)}
      >
        <Modal.Header closeButton>
          <Modal.Title>
            <FormattedMessage id="DELETE_ENCOUNTER" />
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <p className="mb-0">
            <FormattedMessage id="DELETE_ENCOUNTER_CONFIRM" />
          </p>
        </Modal.Body>
        <Modal.Footer className="d-flex gap-3">
          <MainButton
            type="button"
            className="btn btn-outline-secondary"
            onClick={() => setDeleteModalShow(false)}
            noArrow={true}
            backgroundColor={theme.primaryColors.primary700}
            color="white"
          >
            <FormattedMessage id="CANCEL" />
          </MainButton>
          <MainButton
            onClick={handleDelete}
            backgroundColor="white"
            color="#f91010ff"
            noArrow
            borderColor={"#f91010ff"}
            disabled={loading}
          >
            <FormattedMessage id="DELETE" />
          </MainButton>
        </Modal.Footer>
      </Modal>

      <Modal
        centered
        show={unassignModalShow}
        onHide={() => setUnassignModalShow(false)}
      >
        <Modal.Header closeButton>
          <Modal.Title>
            <FormattedMessage id="UNASSIGN_AND_DELETE_ENCOUNTER" />
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <p className="mb-0">
            <FormattedMessage id="UNASSIGN_AND_DELETE_ENCOUNTER_DESC" />
          </p>
        </Modal.Body>
        <Modal.Footer className="d-flex gap-3">
          <MainButton
            type="button"
            className="btn btn-outline-secondary"
            onClick={() => setUnassignModalShow(false)}
            noArrow={true}
            backgroundColor={theme.primaryColors.primary700}
            color="white"
          >
            <FormattedMessage id="CANCEL" />
          </MainButton>
          <MainButton
            onClick={handleUnassignAndDelete}
            backgroundColor="white"
            color="#f91010ff"
            noArrow
            borderColor={"#f91010ff"}
            disabled={loading}
          >
            <FormattedMessage id="UNASSIGN_AND_DELETE_ENCOUNTER" />
          </MainButton>
        </Modal.Footer>
      </Modal>
    </div>
  );
}
