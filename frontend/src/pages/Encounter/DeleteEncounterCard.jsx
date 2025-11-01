import React from "react";
import MainButton from "../../components/MainButton";
import axios from "axios";
import { toast } from "react-toastify";

export default function DeleteEncounterCard({
  id,
  setEncounterDeleted = () => {},
}) {
  const handleDelete = async () => {
    const params = new URLSearchParams();
    params.append("number", id);
    params.append("approve", "Delete Encounter?");

    const result = await axios.post("/EncounterDelete", params, {
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
    });

    if (result.status === 200) {
      setEncounterDeleted("success");
      toast.success("Encounter deleted successfully");
    } else {
      setEncounterDeleted("error");
      toast.error("Failed to delete encounter");
    }
  };

  return (
    <div className="delete-encounter-card mb-3">
      <div className="card-header d-flex flex-row align-items-center mb-3">
        <div
          className
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
          Danger Zone
        </div>
      </div>
      <p>Delete Encounter?</p>
      <p>Encounter will be permanently deleted after 30 days</p>
      <MainButton
        onClick={handleDelete}
        backgroundColor="#e80d23ff"
        color="#ffffff"
        shadowColor="#69050fff"
        style={{ marginLeft: 0 }}
        noArrow={true}
      >
        Delete Encounter
      </MainButton>
    </div>
  );
}
