import { observer } from "mobx-react-lite";
import React from "react";
import { Modal } from "react-bootstrap";
import SelectInput from "../../components/generalInputs/SelectInput";
import MainButton from "../../components/MainButton";
import ThemeColorContext from "../../ThemeColorProvider";

export const MatchCriteriaModal = observer(function MatchCriteriaModal({
  store = {},
  isOpen = false,
  onClose = () => {},
}) {
  const theme = React.useContext(ThemeColorContext);

  return (
    <Modal
      show={isOpen}
      onHide={onClose}
      centered
      scrollable
      keyboard
      style={{ zIndex: 2000 }}
    >
      <Modal.Header closeButton>
        <Modal.Title>Match Criteria</Modal.Title>
      </Modal.Header>

      <Modal.Body>
        <div className="match-criteria">
          <p>
            Choose the algorithm and set filters to refine your search. You can
            also save your preferences to make future matching hassle-free
          </p>

          <h6>Filter Match Results</h6>
          <p>
            Set filters like location ID, sex, life stage, and owner. Use
            advanced filters to refine results even further.
          </p>

          <SelectInput
            label="Location ID"
            options={store.locationIdOptions}
            value={store.selectedLocation}
            onChange={(value) => store.setSelectedLocation(value)}
          />

          <SelectInput
            label="Owner"
            options={[{ label: "My Data", value: "mydata" }]}
            value={store.selectedSex}
            onChange={(value) => store.setOwner(value)}
          />

          <SelectInput
            label="Choose Algorithm"
            options={store.algorithmOptions}
            value={store.selectedAlgorithm}
            onChange={(value) => store.setSelectedAlgorithm(value)}
          />

          <div className="d-flex justify-content-between align-items-center w-100 flex-wrap mt-3">
            <MainButton
              noArrow={true}
              backgroundColor={theme.primaryColors.primary700}
              color="white"
            >
              {"Match"}
            </MainButton>

            <MainButton
              noArrow={true}
              variant="secondary"
              borderColor={theme.primaryColors.primary700}
              color={theme.primaryColors.primary700}
              shadowColor={theme.primaryColors.primary700}
              onClick={onClose}
            >
              {"Cancel"}
            </MainButton>
          </div>
        </div>
      </Modal.Body>
    </Modal>
  );
});

export default MatchCriteriaModal;
