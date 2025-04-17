import React from "react";
import { observer } from "mobx-react-lite";
import EditableDataTable from "../../components/EditableDataTable";
import { useContext } from "react";
import ThemeContext from "../../ThemeColorProvider";
import { FormattedMessage } from "react-intl";
import MainButton from "../../components/MainButton";


export const BulkImportTableReview = observer(({ store }) => {
    const theme = useContext(ThemeContext);

    return (
        <div>
            <EditableDataTable store={store}/>
            <div>
                <MainButton
                    onClick={() => {
                        store.setActiveStep(1);
                    }}
                    backgroundColor={theme.wildMeColors.cyan700}
                    color={theme.defaultColors.white}
                    noArrow={true}
                    style={{ width: "auto", fontSize: "1rem", margin: "0 auto" }}
                >
                    <FormattedMessage id="PREVIOUS" />
                </MainButton>
                <MainButton
                    onClick={() => {
                        store.setActiveStep(3);
                    }}
                    backgroundColor={theme.wildMeColors.cyan700}
                    color={theme.defaultColors.white}
                    noArrow={true}
                    style={{ width: "auto", fontSize: "1rem", margin: "0 auto" }}
                >
                    <FormattedMessage id="NEXT" />
                </MainButton>
            </div>
        </div>
    )


})


