import React from "react";
import { observer } from "mobx-react-lite";
import "./BulkImport.css";
import {
    CircularProgressbarWithChildren,
    buildStyles,
} from "react-circular-progressbar";
import "react-circular-progressbar/dist/styles.css";
import { FaImage } from "react-icons/fa";
import { MdTableChart } from "react-icons/md";
import ThemeColorContext from "../../ThemeColorProvider";

export const BulkImportUploadProgress = observer(({ store }) => {
    const theme = React.useContext(ThemeColorContext);
    return (
        <div className="d-flex flex-row mt-4 me-5">

            <div style={{ width: 40, height: 40, marginRight: "30px" }}
                onClick={() => {
                    console.log("Image Upload Progress: ", store.imageUploadProgress);
                    store.setActiveStep(0);
                }
                }
            >
                <CircularProgressbarWithChildren
                    value={store.imageUploadProgress}
                    strokeWidth={6}
                    background
                    backgroundPadding={4}
                    styles={buildStyles({
                        trailColor: theme.primaryColors.primary50,
                        backgroundColor: store.activeStep === 0 ? theme.primaryColors.primary500 : theme.primaryColors.primary50,
                    })}

                >
                    <FaImage
                        size={20}
                        color={store.activeStep === 0 ? "#fff" : theme.primaryColors.primary500}
                    />
                </CircularProgressbarWithChildren>
            </div>
            <div style={{ width: 40, height: 40, marginRight: "30px" }}
                onClick={() => {
                    console.log("Spreadsheet Upload Progress: ", store.spreadsheetUploadProgress);
                    store.setActiveStep(1);
                }}
            >
                <CircularProgressbarWithChildren
                    value={store.spreadsheetUploadProgress}
                    strokeWidth={6}
                    background
                    backgroundPadding={4}
                    styles={buildStyles({
                        trailColor: theme.primaryColors.primary50,
                        backgroundColor: store.activeStep === 1 ? theme.primaryColors.primary500 : theme.primaryColors.primary50,
                    })}
                    onClick={() => {
                        store.setActiveStep(1);
                    }
                    }
                >
                    <MdTableChart size={20} color={store.activeStep === 1 ? "#fff" : theme.primaryColors.primary500} />
                </CircularProgressbarWithChildren>
            </div>
            <div style={{
                width: 38,
                height: 38,
                marginRight: "30px",
                backgroundColor: store.activeStep === 2 ? theme.primaryColors.primary500 : theme.primaryColors.primary50,
                borderRadius: "50%",
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
                color: store.activeStep === 2 ? "#fff" : theme.primaryColors.primary500,
                fontSize: "20px",
            }}
                onClick={() => {
                    if (store.spreadsheetUploadProgress === 100) {
                        store.setActiveStep(2);
                    }
                }
                }
            >
                <i class="bi bi-eye"></i>
            </div>
            <div style={{
                width: 38,
                height: 38,
                marginRight: "30px",
                backgroundColor: store.activeStep === 3 ? theme.primaryColors.primary500 : theme.primaryColors.primary50,
                borderRadius: "50%",
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
                color: store.activeStep === 3 ? "#fff" : theme.primaryColors.primary500,
                fontSize: "20px",
            }}
                onClick={() => {
                    console.log("Review Progress: ", store.spreadsheetUploadProgress);
                    store.setActiveStep(3);
                }}
            >
                <i class="bi bi-crosshair"></i>
            </div>
        </div>
    );
});

