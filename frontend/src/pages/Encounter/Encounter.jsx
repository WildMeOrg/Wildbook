import React from "react";
import MainButton from "../../components/MainButton";
import axios from "axios";
import { observer } from "mobx-react-lite";
import EncounterStore from "./EncounterStore";
import { Container } from "react-bootstrap";
import { Row, Col } from "react-bootstrap";
import ActivePill from "../../components/ActivePill";
import InactivePill from "../../components/InactivePill";
import CardWithSaveAndCancelButtons from "../../components/CardWithSaveAndCancelButtons";
import TextInput from "../../components/TextInput";
import DateIcon from "../../components/DateIcon";
import DateCardContent from "./DateCardContent";
import IdentifyIcon from "../../components/IdentifyIcon";
import MetadataIcon from "../../components/MetaDataIcon";
import LocationIcon from "../../components/LocationIcon";
import AttributesIcon from "../../components/AttributesIcon";
import ImageCard from "./ImageCard";
import CardWithEditButton from "../../components/CardWithEditButton";

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

      <div style={{ marginTop: "20px", display: "flex", flexDirection: "row" }}>

        <ActivePill
          text="Overview"
          style={{ marginRight: "10px" }}
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
      {store.overviewActive ? (

        <Row>
          <Col md={3}>
            {
              store.editDateCard ? (<CardWithSaveAndCancelButtons
                icon={<DateIcon />}
                title="Date"
                onSave={() => {
                  console.log("Save clicked");
                  store.setEditDateCard(false);
                }}
                onCancel={() => {
                  console.log("Cancel clicked");
                  store.setEditDateCard(false);
                }}
                content={<DateCardContent />}
              />) : (
                <CardWithEditButton
                  icon={<DateIcon />}
                  title="Date"
                  onClick={() => {
                    store.setEditDateCard(true);
                  }}
                  content={<p>it's time</p>}
                />
              )
            }
            {
              store.editIdentifyCard ? (
                <CardWithSaveAndCancelButtons
                  icon={<IdentifyIcon />}
                  onSave={() => {
                    console.log("Save clicked");
                    store.setEditIdentifyCard(false);
                  }}
                  title="Identify"
                  onCancel={() => {
                    console.log("Cancel clicked");
                    store.setEditIdentifyCard(false);
                  }}
                  saveButtonText="Save Changes"
                  cancelButtonText="Cancel"
                />
              ) : (
                <CardWithEditButton
                  icon={<IdentifyIcon />}
                  title="Identify"
                  onClick={() => {
                    store.setEditIdentifyCard(true);
                  }}
                />
              )
            }

            {
              store.editMetadataCard ? (
                <CardWithSaveAndCancelButtons
                  icon={<MetadataIcon />}
                  title="Metadata"
                  onSave={() => {
                    console.log("Save clicked");
                    store.setEditMetadataCard(false);
                  }}
                  onCancel={() => {
                    console.log("Cancel clicked");
                    store.setEditMetadataCard(false);
                  }}
                  saveButtonText="Save Changes"
                  cancelButtonText="Cancel"
                />
              ) : (
                <CardWithEditButton
                  icon={<MetadataIcon />}
                  title="Metadata"
                  onClick={() => {
                    store.setEditMetadataCard(true);
                  }}
                />
              )
            }

          </Col>
          <Col md={3}>

            {
              store.editLocationCard ? (
                <CardWithSaveAndCancelButtons
                  icon={<LocationIcon />}
                  title="Location"
                  onSave={() => {
                    console.log("Save clicked");
                    store.setEditLocationCard(false);
                  }}
                  onCancel={() => {
                    console.log("Cancel clicked");
                    store.setEditLocationCard(false);
                  }}
                />
              ) : (
                <CardWithEditButton
                  icon={<LocationIcon />}
                  title="Location"
                  onClick={() => {
                    store.setEditLocationCard(true);
                  }}
                />
              )
            }

            {store.editAttributesCard ? (
              <CardWithSaveAndCancelButtons
                icon={<LocationIcon />}
                title="Location"
                onSave={() => {
                  store.setEditAttributesCard(false);
                }
                }
                onCancel={() => {
                  console.log("Cancel clicked");
                  store.setEditAttributesCard(false);
                }}

              />) : (
              <CardWithEditButton
                icon={<AttributesIcon />}
                title="Attributes"
                onClick={() => {
                  store.setEditAttributesCard(true);
                }}
              />
            )
            }

          </Col>
          <Col md={6}>
            <ImageCard />
          </Col>
        </Row>) : (
        <p>TDB</p>
      )}
    </Container>
  );
});

export default Encounter;
