import React from 'react';
import { observer } from 'mobx-react-lite';
import ContactInfoCard from './ContactInfoCard';
import AddPeople from './AddPeople';
import MainButton from '../../components/MainButton';
import ThemeColorContext from '../../ThemeColorProvider';

export const ContactInfoModal = observer(({
    isOpen,
    onClose,
    store = {}
}) => {
    if (!isOpen) return null;
    const theme = React.useContext(ThemeColorContext);
    console.log("openAddPeopleModal:", JSON.stringify(store.openAddPeopleModal));
    return (
        <div className="modal show d-block" style={{
            // backgroundColor: 'rgba(172, 59, 59, 0.5)',
            padding: '20px',
            height: '100vh',
            overflowY: 'auto',
        }}>
            <div className="modal-dialog modal-lg">
                <div className="modal-content">
                    <div className="modal-header">
                        <h5 className="modal-title">Contact Information</h5>
                        <button type="button" className="btn-close" onClick={onClose}></button>
                    </div>

                    <div className="modal-body">
                        <div
                            style={{
                                padding: "10px",
                            }}
                        >
                            <p>Adding someone here won't send them emails by default. "Send Updates"
                                only applied to this encounter, and they can opt out anytime.
                            </p>
                        </div>
                        <ContactInfoCard
                            title="submitter"
                            data={store.encounterData?.submitters}
                            onDelete={() =>
                                // store.removeContact(data?.id)
                                console.log(`Delete submitter:`) // Placeholder for delete function
                            }
                        />

                        {
                            <ContactInfoCard title="photographer" data={store.encounterData?.photographers} />
                        }
                        {
                            <ContactInfoCard title="other users to inform" data={store.encounterData?.informOthers} />
                        }

                        <MainButton
                            onClick={() => store.setOpenAddPeopleModal(true)}
                            noArrow={true}
                            backgroundColor={theme.primaryColors.primary700}
                            color="white"
                            style={{ marginLeft: 0, marginTop: '20px' }}
                        >
                            {"Add People"}
                        </MainButton>
                        {store.openAddPeopleModal &&
                            <AddPeople
                                store={store}
                            />}
                    </div>
                    <div className="modal-footer">
                        <button type="button" className="btn btn-secondary" onClick={onClose}>
                            Close
                        </button>
                    </div>
                </div>
            </div>
        </div >
    );
})

export default ContactInfoModal;