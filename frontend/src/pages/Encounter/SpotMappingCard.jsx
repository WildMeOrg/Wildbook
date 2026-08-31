import React from "react";
import { observer } from "mobx-react-lite";
import { Modal, Button } from "react-bootstrap";
import SpotMappingIcon from "../../components/icons/SpotMappingIcon";
import MainButton from "../../components/MainButton";
import ThemeColorContext from "../../ThemeColorProvider";
import RemoveIcon from "../../components/icons/RemoveIcon";
import { FormattedMessage, useIntl } from "react-intl";
import ContainerWithSpinner from "../../components/ContainerWithSpinner";

export const SpotMappingCard = observer(({ store = {} }) => {
  const intl = useIntl();
  const themeColor = React.useContext(ThemeColorContext);

  const [pendingRemoveSide, setPendingRemoveSide] = React.useState(null);

  const isWrite = store?.access === "write";
  const loading = Boolean(store?.spotMappingLoading);

  const spotMapping = store?.encounterData?.spotMapping || {};
  const enabled = Boolean(spotMapping?.enabled);
  const encounterNumber = store?.encounterData?.id || "";

  const hasLeftSpots = Boolean(spotMapping?.hasLeftSpots);
  const hasRightSpots = Boolean(spotMapping?.hasRightSpots);
  const numberLeftSpots = spotMapping?.numberLeftSpots ?? 0;
  const numberRightSpots = spotMapping?.numberRightSpots ?? 0;
  const hasSpots = Boolean(spotMapping?.hasSpots);
  const resultsGrothLeft = Boolean(spotMapping?.resultsGrothLeft);
  const resultsGrothRight = Boolean(spotMapping?.resultsGrothRight);
  const resultsI3SLeft = Boolean(spotMapping?.resultsI3SLeft);
  const resultsI3SRight = Boolean(spotMapping?.resultsI3SRight);

  const availableSides = [
    hasLeftSpots ? "left" : null,
    hasRightSpots ? "right" : null,
  ].filter(Boolean);

  const selectedSide = availableSides.includes(store?.selectedSpotMappingSide)
    ? store?.selectedSpotMappingSide
    : availableSides[0] || "";

  const algorithmTitle = "Modified Groth and I3S";
  const cyan700 = themeColor?.wildMeColors?.cyan700 || "#00b7e3";

  const handleConfirmRemove = async () => {
    if (loading) {
      return;
    }
    try {
      await store?.removeExtractedSpots?.(pendingRemoveSide);
      setPendingRemoveSide(null);
    } catch (error) {
      // Error already handled by removeExtractedSpots (toast shown)
      // Modal stays open so user can retry
      console.error("Failed to remove spots:", error);
    }
  };

  const renderExtractedSpotRow = (side, count) => {
    const sideLabel = intl.formatMessage({
      id: side === "left" ? "LEFT_SIDE" : "RIGHT_SIDE",
      defaultMessage: side === "left" ? "Left-side" : "Right-side",
    });
    return (
      <div
        key={side}
        className="d-flex align-items-center justify-content-between mb-2"
      >
        <p className="mb-0">
          <FormattedMessage
            id="SPOTS_SIDE_ADDED"
            defaultMessage="{count} {side}-side spots added"
            values={{ count, side: sideLabel }}
          />
        </p>

        {isWrite && (
          <button
            type="button"
            className="btn p-1"
            aria-label={intl.formatMessage({
              id: "REMOVE_EXTRACTED_SPOTS",
              defaultMessage: "Remove extracted spots",
            })}
            title={intl.formatMessage({
              id: "REMOVE_EXTRACTED_SPOTS",
              defaultMessage: "Remove extracted spots",
            })}
            style={{
              background: "transparent",
              border: "none",
              cursor: loading ? "not-allowed" : "pointer",
              opacity: loading ? 0.5 : 1,
            }}
            disabled={loading}
            onClick={(e) => {
              e.stopPropagation();
              if (!loading) {
                setPendingRemoveSide(side);
              }
            }}
          >
            <RemoveIcon />
          </button>
        )}
      </div>
    );
  };

  if (!enabled) {
    return null;
  }

  return (
    <>
      <Modal
        show={pendingRemoveSide !== null}
        onHide={() => setPendingRemoveSide(null)}
        centered
      >
        <Modal.Header closeButton>
          <Modal.Title>
            <FormattedMessage
              id="REMOVE_EXTRACTED_SPOTS"
              defaultMessage="Remove extracted spots"
            />
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <FormattedMessage
            id="REMOVE_EXTRACTED_SPOTS_CONFIRM"
            defaultMessage="Are you sure you want to remove all {side}-side extracted spots? This cannot be undone."
            values={{ side: pendingRemoveSide }}
          />
        </Modal.Body>
        <Modal.Footer>
          <Button
            variant="secondary"
            onClick={() => setPendingRemoveSide(null)}
          >
            <FormattedMessage id="CANCEL" defaultMessage="Cancel" />
          </Button>
          <Button
            onClick={handleConfirmRemove}
            disabled={loading}
            style={{
              color: "white",
              backgroundColor: cyan700,
              borderColor: cyan700,
            }}
          >
            <FormattedMessage id="REMOVE" defaultMessage="Remove" />
          </Button>
        </Modal.Footer>
      </Modal>

      <div
        className="d-flex flex-column justify-content-between mt-3 mb-3"
        style={{
          padding: "20px",
          borderRadius: "10px",
          boxShadow: `0px 0px 10px rgba(0, 0, 0, 0.2)`,
          width: "100%",
        }}
      >
        <div className="mb-3 d-flex align-items-center justify-content-between">
          <div className="d-flex flex-row align-items-center mb-3">
            <SpotMappingIcon style={{ marginRight: "10px" }} />
            <h6 className="mb-0">
              <FormattedMessage
                id="SPOT_MAPPING_ALGORITHMS"
                defaultMessage="Spot Mapping Algorithms ({algorithms})"
                values={{ algorithms: algorithmTitle }}
              />
            </h6>
          </div>
        </div>

        <ContainerWithSpinner loading={loading}>
          <div>
            <div className="mb-4">
              <div className="mb-2" style={{ fontWeight: "bold" }}>
                <FormattedMessage
                  id="EXTRACTED_SPOTS"
                  defaultMessage="Extracted Spots"
                />
              </div>

              {!hasSpots && (
                <p className="mb-0">
                  <FormattedMessage
                    id="NO_SPOTS_EXTRACTED_YET"
                    defaultMessage="No spots extracted yet."
                  />
                </p>
              )}

              {hasLeftSpots && renderExtractedSpotRow("left", numberLeftSpots)}
              {hasRightSpots &&
                renderExtractedSpotRow("right", numberRightSpots)}
            </div>

            <div
              style={{
                width: "100%",
                borderBottom: "1px solid #ccc",
                marginBottom: "20px",
              }}
            />

            <div className="mb-4">
              <div className="mb-2" style={{ fontWeight: "bold" }}>
                <FormattedMessage
                  id="PATTERN_MATCHING_RESULTS"
                  defaultMessage="Pattern Matching Results"
                />
              </div>

              {!resultsGrothLeft &&
                !resultsGrothRight &&
                !resultsI3SLeft &&
                !resultsI3SRight && (
                  <p className="mb-0">
                    <FormattedMessage
                      id="NO_SCAN_RESULTS_YET"
                      defaultMessage="No scan results yet."
                    />
                  </p>
                )}

              {resultsGrothLeft && (
                <div className="mb-1">
                  <a
                    href={`/encounters/scanEndApplet.jsp?writeThis=true&number=${encodeURIComponent(encounterNumber)}&taskID=scanL${encodeURIComponent(encounterNumber)}`}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <FormattedMessage
                      id="GROTH_LEFT_SCAN_RESULTS"
                      defaultMessage="Groth: Left-side scan results"
                    />
                  </a>
                </div>
              )}

              {resultsGrothRight && (
                <div className="mb-1">
                  <a
                    href={`/encounters/scanEndApplet.jsp?writeThis=true&number=${encodeURIComponent(encounterNumber)}&taskID=scanR${encodeURIComponent(encounterNumber)}&rightSide=true`}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <FormattedMessage
                      id="GROTH_RIGHT_SCAN_RESULTS"
                      defaultMessage="Groth: Right-side scan results"
                    />
                  </a>
                </div>
              )}

              {resultsI3SLeft && (
                <div className="mb-1">
                  <a
                    href={`/encounters/i3sScanEndApplet.jsp?writeThis=true&number=${encodeURIComponent(encounterNumber)}&I3S=true`}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <FormattedMessage
                      id="I3S_LEFT_SCAN_RESULTS"
                      defaultMessage="I3S: Left-side scan results"
                    />
                  </a>
                </div>
              )}

              {resultsI3SRight && (
                <div className="mb-1">
                  <a
                    href={`/encounters/i3sScanEndApplet.jsp?writeThis=true&number=${encodeURIComponent(encounterNumber)}&rightSide=true&I3S=true`}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <FormattedMessage
                      id="I3S_RIGHT_SCAN_RESULTS"
                      defaultMessage="I3S: Right-side scan results"
                    />
                  </a>
                </div>
              )}
            </div>

            {isWrite && availableSides.length > 0 && (
              <>
                <div
                  style={{
                    width: "100%",
                    borderBottom: "1px solid #ccc",
                    marginBottom: "20px",
                  }}
                />

                <div>
                  <div className="mb-2" style={{ fontWeight: "bold" }}>
                    <FormattedMessage
                      id="SCAN_FOR_MATCHES"
                      defaultMessage="Scan for Matches"
                    />
                  </div>

                  <p className="mb-2">
                    <FormattedMessage
                      id="SCAN_ENTIRE_DATABASE_USING_ALGORITHMS"
                      defaultMessage="Scan entire database using the {groth} and {i3s} algorithms."
                      values={{
                        groth: (
                          <a
                            href="https://besjournals.onlinelibrary.wiley.com/doi/pdf/10.1111/j.1365-2664.2005.01117.x"
                            target="_blank"
                            rel="noopener noreferrer"
                            style={{
                              color: cyan700,
                              textDecoration: "underline",
                            }}
                          >
                            Modified Groth
                          </a>
                        ),
                        i3s: (
                          <a
                            href="https://besjournals.onlinelibrary.wiley.com/doi/full/10.1111/j.1365-2664.2006.01273.x?journalCode=jpe"
                            target="_blank"
                            rel="noopener noreferrer"
                            style={{
                              color: cyan700,
                              textDecoration: "underline",
                            }}
                          >
                            I3S
                          </a>
                        ),
                      }}
                    />
                  </p>

                  <div className="mb-3 d-flex flex-column" style={{ gap: 8 }}>
                    {availableSides.includes("left") && (
                      <label
                        className="d-inline-flex align-items-center"
                        style={{ gap: 8, cursor: "pointer" }}
                      >
                        <input
                          type="radio"
                          name="spot-mapping-side"
                          checked={selectedSide === "left"}
                          onChange={() =>
                            store?.setSelectedSpotMappingSide?.("left")
                          }
                          style={{
                            width: 18,
                            height: 18,
                            accentColor: cyan700,
                          }}
                        />
                        <span>
                          <FormattedMessage
                            id="LEFT_SIDE"
                            defaultMessage="Left-side"
                          />
                        </span>
                      </label>
                    )}

                    {availableSides.includes("right") && (
                      <label
                        className="d-inline-flex align-items-center"
                        style={{ gap: 8, cursor: "pointer" }}
                      >
                        <input
                          type="radio"
                          name="spot-mapping-side"
                          checked={selectedSide === "right"}
                          onChange={() => {
                            store?.setSelectedSpotMappingSide?.("right");
                          }}
                          style={{
                            width: 18,
                            height: 18,
                            accentColor: cyan700,
                          }}
                        />
                        <span>
                          <FormattedMessage
                            id="RIGHT_SIDE"
                            defaultMessage="Right-side"
                          />
                        </span>
                      </label>
                    )}
                  </div>

                  <MainButton
                    onClick={() => {
                      if (loading) {
                        return;
                      }
                      store?.startSpotMappingScan?.(selectedSide);
                    }}
                    noArrow={true}
                    color="white"
                    backgroundColor={cyan700}
                    borderColor={cyan700}
                    disabled={!selectedSide || loading}
                  >
                    <FormattedMessage
                      id="START_SCAN"
                      defaultMessage="Start Scan"
                    />
                  </MainButton>
                </div>
              </>
            )}
          </div>
        </ContainerWithSpinner>
      </div>
    </>
  );
});
