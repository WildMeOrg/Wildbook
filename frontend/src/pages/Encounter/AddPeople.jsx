import React from 'react';
import TextInput from '../../components/generalInputs/TextInput';
import SelectInput from '../../components/generalInputs/SelectInput';
import MainButton from '../../components/MainButton';
import { observer } from 'mobx-react-lite';
import ThemeColorContext from '../../ThemeColorProvider';


export const AddPeople = observer(({ store }) => {
    const theme = React.useContext(ThemeColorContext);
    return (
        <div>
            <h6>Add New People</h6>
            <TextInput
                value={store.newPersonEmail}
                onChange={(value) => store.setNewPersonEmail(value)}
                placeholder="Enter email"
                className="mb-3"
                label="Email"
            />
            <SelectInput
                value={store.newPersonRole}
                onChange={(value) => store.setNewPersonRole(value)}
                options={['submitters', 'photographers', 'informOthers']}
                placeholder="Select role"
                className="mb-3"
                label="Role"
            />
            <div className="d-flex justify-content-between align-items-center w-100 flex-wrap mt-3">
                <MainButton
                    onClick={() => {
                        store.addNewPerson();                        
                        store.refreshEncounterData();
                    }}
                    noArrow={true}
                    backgroundColor={theme.primaryColors.primary700}
                    color="white"
                    disabled={!store.newPersonEmail || !store.newPersonRole}
                >
                    {"Save"}
                </MainButton>
                <MainButton
                    onClick={() => {
                        store.setNewPersonName('');
                        store.setNewPersonEmail('');
                        store.setNewPersonRole('');
                        store.modals.setOpenAddPeopleModal(false);
                    }}
                    noArrow={true}
                    variant="secondary"
                    borderColor={theme.primaryColors.primary700}
                    color={theme.primaryColors.primary700}
                    shadowColor={theme.primaryColors.primary700}
                >
                    {"Cancel"}
                </MainButton>
            </div>
        </div>
    );
})

export default AddPeople;