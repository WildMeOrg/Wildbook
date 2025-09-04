import React from "react";
import MainButton from "../../components/MainButton";
import axios from "axios";
import { observer } from "mobx-react-lite";
import EncounterStore from "./EncounterStore";
import { Container } from "react-bootstrap";
import { Row, Col } from "react-bootstrap";
import ActivePill from "../../components/ActivePill";
import InactivePill from "../../components/InactivePill";

const Encounter = observer(() => {
  const store = React.useMemo(() => new EncounterStore(), []);

  const params = new URLSearchParams(window.location.search);
  const encounterNumber =
    params.get("number") || "4770c075-a4c7-48c3-9616-8fa87aa2d65a";

  console.log("Encounter number:", encounterNumber);



  axios
    .get("/api/v3/encounters/" + encounterNumber)
    .then((response) => {
      store.setEncounterData(response.data);
    })
    .catch((error) => {
      console.error("There was an error fetching the encounter data:", error);
    });

  const handleSubmit = () => {
    axios
      .patch("/api/v3/encounters/" + encounterNumber, store.data)
      //  [{op: "add", path: field, value: value}])
      .then((response) => {
        alert("Data submitted successfully:", response.data);
      })
      .catch((error) => {
        alert("There was an error submitting the data:", error);
      });
  };

  const options = [
    "decimalLatitude",
    "decimalLongitude",
    "alternateID",
    "behavior",
    "country",
  ];

  const [value, setValue] = React.useState("");
  const [field, setField] = React.useState(options[0]);
  return (
    <Container>
      <h2>Encounter {store.encounterData?.individualNames ?
        store.encounterData?.individualNames.map(
          (name) => !!name)[0] : "Unassigned"}
      </h2>
      <p>Encounter ID: {encounterNumber}</p>

      {/* <select
        style={{ padding: "10px", width: "300px" }}
        onChange={(e) => setField(e.target.value)}
      >
        {options.map((option, index) => (
          <option key={index} value={option}>
            {option}
          </option>
        ))}
      </select>

      <div>You are updating {field}</div>

      <input
        type="text"
        placeholder="Type something here..."
        style={{ padding: "10px", width: "300px", marginTop: "20px" }}
        onChange={(e) => setValue(e.target.value)}
      />

      <MainButton onClick={() => handleSubmit(value)} noArrow={true}>
        Click Me
      </MainButton> */}

      <div style={{ marginTop: "20px", display: "flex", flexDirection: "row" }}>

        <ActivePill
          text="Overview"
          style={{marginRight: "10px"}}
          onClick={() => {
            console.log("Add Individual clicked");
            store.setOverviewActive(true);
          }}
        />
        <InactivePill
          text="More Details"
          onClick={() => {
            console.log("Add Individual clicked");
            store.setOverviewActive(false);
          }}
        />


      </div>
    </Container>
  );
});

export default Encounter;
