import React from "react";
import { observer } from "mobx-react-lite";
import ContactInfoCard from "./ContactInfoCard";
import AddPeople from "./AddPeople";
import MainButton from "../../components/MainButton";
import ThemeColorContext from "../../ThemeColorProvider";
import { Modal } from "react-bootstrap";
import { FormattedMessage } from "react-intl";

export const ContactInfoModal = observer(({ isOpen, onClose, store = {} }) => {
  const theme = React.useContext(ThemeColorContext);

  const submitterInfo = store?.encounterData?.submitterInfo;
  const submitters = store?.encounterData?.submitters;
  const photographers = store?.encounterData?.photographers;
  const informOthers = store?.encounterData?.informOthers;

  if (!isOpen) return null;

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

        {submitterInfo && Object.keys(submitterInfo).length > 0 && (
          <ContactInfoCard
            title="MANAGING_RESEARCHER"
            type="submitterID"
            data={[submitterInfo]}
            store={store}
          />
        )}

        {Array.isArray(submitters) && submitters.length > 0 && (
          <ContactInfoCard
            title="SUBMITTER"
            type="submitters"
            data={submitters}
            store={store}
          />
        )}

        {Array.isArray(photographers) && photographers.length > 0 && (
          <ContactInfoCard
            title="PHOTOGRAPHER"
            type="photographers"
            store={store}
            data={photographers}
          />
        )}

        {Array.isArray(informOthers) && informOthers.length > 0 && (
          <ContactInfoCard
            title="INFORM_OTHERS"
            type="informOthers"
            store={store}
            data={informOthers}
          />
        )}

        {store?.access === "write" && (
          <MainButton
            onClick={() => store?.modals?.setOpenAddPeopleModal?.(true)}
            noArrow={true}
            backgroundColor={theme.primaryColors.primary700}
            color="white"
            style={{ marginLeft: 0, marginTop: "20px" }}
          >
            <FormattedMessage id="ADD_PEOPLE" />
          </MainButton>
        )}

        {store?.access === "write" && store?.modals?.openAddPeopleModal && (
          <AddPeople store={store} />
        )}
      </Modal.Body>
    </Modal>
  );
});

export default ContactInfoModal;
