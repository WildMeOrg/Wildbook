import React, { useEffect, useState } from "react";
import { observer } from "mobx-react-lite";
import { Container, Row, Col, Modal } from "react-bootstrap";
import { FormattedMessage, useIntl } from "react-intl";
import Pill from "../../components/Pill";
import HistoryIcon from "../../components/icons/HistoryIcon";
import IndividualStore from "./stores/IndividualStore";
import ViewSwitcher from "./Components/ViewSwitcher";
import DetailsCard from "./Components/DetailsCard";
import AdditionalFilesCard from "./Components/AdditionalFilesCard";
import EncountersTableView from "./Components/EncountersTableView";
import EncountersGalleryView from "./Components/EncountersGalleryView";
import EncountersMapView from "./Components/EncountersMapView";
import LoadingScreen from "../../components/LoadingScreen";
import ThemeColorContext from "../../ThemeColorProvider";
import { useSearchParams } from "react-router-dom";

const Individual = observer(() => {
  const [store] = useState(() => new IndividualStore());
  const intl = useIntl();
  const [individualValid, setIndividualValid] = useState(true);

  const [params] = useSearchParams();
  const individualId = params.get("id");
  const theme = React.useContext(ThemeColorContext);

  useEffect(() => {
    store.setIntl(intl);
  }, [store, intl]);

  useEffect(() => {
    let cancelled = false;

    const fetchData = async () => {
      try {
        await store.fetchIndividual(individualId);
      } catch (_err) {
        if (!cancelled) {
          setIndividualValid(false);
        }
      }
    };

    if (individualId) {
      fetchData();
    } else {
      setIndividualValid(false);
    }

    return () => {
      cancelled = true;
    };
  }, [individualId, store]);

  if (store.loading) {
    return <LoadingScreen />;
  }

  if (!individualValid) {
    return (
      <Container style={{ padding: "20px" }}>
        <Modal
          show
          onHide={() => {
            window.location.href = "/react";
          }}
        >
          <Modal.Header closeButton>
            <Modal.Title>
              <FormattedMessage id="INDIVIDUAL_NOT_FOUND" />
            </Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <p>
              <FormattedMessage id="INDIVIDUAL_NOT_FOUND_DESC" />
            </p>
          </Modal.Body>
        </Modal>
      </Container>
    );
  }

  const renderContent = () => {
    switch (store.contentView) {
      case "gallery":
        return <EncountersGalleryView store={store} />;
      case "map":
        return <EncountersMapView store={store} />;
      case "table":
      default:
        return <EncountersTableView store={store} />;
    }
  };

  return (
    <Container fluid style={{ padding: "20px", maxWidth: "1400px" }}>
      <Row className="align-items-center mb-4">
        <Col md={8}>
          <div className="d-flex align-items-center gap-3">
            <div
              style={{
                width: "80px",
                height: "80px",
                borderRadius: "50%",
                overflow: "hidden",
                border: "3px solid #00ACCE",
                flexShrink: 0,
              }}
            >
              <img
                src={store.avatarUrl}
                alt={store.displayName}
                style={{
                  width: "100%",
                  height: "100%",
                  objectFit: "cover",
                }}
              />
            </div>

            <div>
              <h2 className="mb-1">{store.displayName}</h2>
              {store.alternateIds.length > 0 && (
                <p className="mb-0 text-muted">
                  <FormattedMessage id="ALTERNATE_ID" />:{" "}
                  {store.alternateIds.join(", ")}
                </p>
              )}
            </div>
          </div>
        </Col>

        <Col md={4} className="text-end">
          <div
            style={{
              cursor: "pointer",
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
              width: "40px",
              height: "40px",
              borderRadius: "50%",
              backgroundColor: theme.primaryColors.primary50,
            }}
            onClick={() => store.modals.setOpenHistoryModal(true)}
            title={intl.formatMessage({ id: "INDIVIDUAL_HISTORY" })}
          >
            <HistoryIcon />
          </div>
        </Col>
      </Row>

      <Row className="mb-3">
        <Col>
          <div className="d-flex gap-2">
            <Pill
              text="ENCOUNTERS"
              active={store.activeTab === "encounters"}
              onClick={() => store.setActiveTab("encounters")}
            />
            <Pill
              text="SOCIAL"
              active={store.activeTab === "social"}
              onClick={() => store.setActiveTab("social")}
            />
          </div>
        </Col>
      </Row>

      <Row>
        <Col md={3}>
          <DetailsCard
            store={store}
            onEdit={() => store.setEditDetailsCard(true)}
          />
          <AdditionalFilesCard store={store} />
        </Col>

        <Col md={9}>
          {store.activeTab === "encounters" && (
            <>
              <div className="d-flex justify-content-end mb-3">
                <ViewSwitcher
                  activeView={store.contentView}
                  onViewChange={(view) => store.setContentView(view)}
                />
              </div>
              {renderContent()}
            </>
          )}
          <Pill
            text="SOCIAL"
            active={store.activeTab === "social"}
            onClick={() => {
              window.location.href = `/individuals.jsp?id=${individualId}#social`;
            }}
          />
        </Col>
      </Row>

      {store.modals.openHistoryModal && (
        <Modal
          show
          onHide={() => store.modals.setOpenHistoryModal(false)}
          size="lg"
        >
          <Modal.Header closeButton>
            <Modal.Title>
              <FormattedMessage id="INDIVIDUAL_HISTORY" />
            </Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <p>
              <FormattedMessage
                id="INDIVIDUAL_HISTORY_DESC"
                defaultMessage="Individual history and timeline will be displayed here."
              />
            </p>
          </Modal.Body>
        </Modal>
      )}
    </Container>
  );
});

export default Individual;
