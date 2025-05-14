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

export const BulkImportUploadProgress = observer(({ store }) => {
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
                        pathColor: "#yellow",
                        trailColor: "#e6f7ff",
                        backgroundColor: store.activeStep === 0 ? "#00b3d9" : "#e6f7ff",
                    })}

                >
                    <FaImage
                        size={20}
                        // color="#00b3d9" 
                        color={store.activeStep === 0 ? "#fff" : "#00b3d9"}
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
                        pathColor: "#yellow",
                        trailColor: "#e6f7ff",
                        backgroundColor: store.activeStep === 1 ? "#00b3d9" : "#e6f7ff",
                    })}
                    onClick={() => {
                        store.setActiveStep(1);
                    }
                    }
                >
                    <MdTableChart size={20} color={store.activeStep === 1 ? "#fff" : "#00b3d9"} />
                </CircularProgressbarWithChildren>
            </div>
            <div style={{
                width: 38,
                height: 38,
                marginRight: "30px",
                backgroundColor: store.activeStep === 2 ? "#00b3d9" : "#e6f7ff",
                borderRadius: "50%",
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
                color: store.activeStep === 2 ? "#fff" : "#00b3d9",
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
                backgroundColor: store.activeStep === 3 ? "#00b3d9" : "#e6f7ff",
                borderRadius: "50%",
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
                color: store.activeStep === 3 ? "#fff" : "#00b3d9",
                fontSize: "20px",
            }}
                onClick={() => {
                    console.log("Review Progress: ", store.spreadsheetUploadProgress);

                }}
            >
                <i class="bi bi-geo"></i>
                {/* <i class="bi bi-crosshair2"></i>             */}
            </div>
        </div>
    );
});

