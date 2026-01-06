import React from "react";
import { observer } from "mobx-react-lite";
import ContactInfoCard from "./ContactInfoCard";
import AddPeople from "./AddPeople";
import MainButton from "../../components/MainButton";
import ThemeColorContext from "../../ThemeColorProvider";
import { Modal } from "react-bootstrap";
import { FormattedMessage } from "react-intl";

export const ContactInfoModal = observer(({ isOpen, onClose, store = {} }) => {
  if (!isOpen) return null;
  const theme = React.useContext(ThemeColorContext);

  return (
    <Modal
      show={isOpen}
      onHide={onClose}
      size="lg"
      backdrop
      keyboard
      scrollable
      centered
    >
      <Modal.Header closeButton>
        <Modal.Title>
          <FormattedMessage id="CONTACT_INFORMATION" />
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <div
          style={{
            padding: "10px",
          }}
        ></div>
        {Object.keys(store.encounterData?.submitterInfo).length > 0 && (
          <ContactInfoCard
            title="MANAGING_RESEARCHER"
            type="submitterID"
            data={[store.encounterData?.submitterInfo]}
            store={store}
          />
        )}
        {store.encounterData?.submitters.length > 0 && (
          <ContactInfoCard
            title="SUBMITTER"
            type="submitters"
            data={store.encounterData?.submitters}
            store={store}
          />
        )}
        {store.encounterData?.photographers.length > 0 && (
          <ContactInfoCard
            title="PHOTOGRAPHER"
            type="photographers"
            store={store}
            data={store.encounterData?.photographers}
          />
        )}
        {store.encounterData?.informOthers.length > 0 && (
          <ContactInfoCard
            title="INFORM_OTHERS"
            type="informOthers"
            store={store}
            data={store.encounterData?.informOthers}
          />
        )}
        {store.access === "write" && (
          <MainButton
            onClick={() => store.modals.setOpenAddPeopleModal(true)}
            noArrow={true}
            backgroundColor={theme.primaryColors.primary700}
            color="white"
            style={{ marginLeft: 0, marginTop: "20px" }}
          >
            {<FormattedMessage id="ADD_PEOPLE" />}
          </MainButton>
        )}
        {store.access === "write" && store.modals.openAddPeopleModal && (
          <AddPeople store={store} />
        )}
      </Modal.Body>
    </Modal>
  );
});

export default ContactInfoModal;
