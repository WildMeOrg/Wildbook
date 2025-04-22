// ProgressStepper.jsx
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

            <div style={{ width: 48, height: 48, marginRight: "30px" }}
                onClick={() => {
                    console.log("Image Upload Progress: ", store.imageUploadProgress);
                    store.setActiveStep(0);
                }
                }

            >
                <CircularProgressbarWithChildren
                    value={store.imageUploadProgress}
                    strokeWidth={6}
                    styles={buildStyles({
                        pathColor: "#00b3d9",
                        trailColor: "#e6f7ff",
                    })}

                >
                    <FaImage size={20} color="#00b3d9" />
                </CircularProgressbarWithChildren>
            </div>
            <div style={{ width: 48, height: 48, marginRight: "30px" }}
                onClick={() => {
                    console.log("Spreadsheet Upload Progress: ", store.spreadsheetUploadProgress);
                    store.setActiveStep(1);
                }
                }
            >
                <CircularProgressbarWithChildren
                    value={store.spreadsheetUploadProgress}
                    strokeWidth={6}
                    styles={buildStyles({
                        pathColor: "#00b3d9",
                        trailColor: "#e6f7ff",
                    })}
                    onClick={() => {
                        store.setActiveStep(1);
                    }
                    }
                >
                    <MdTableChart size={20} color="#00b3d9" />
                </CircularProgressbarWithChildren>
            </div>
            <div style={{ width: 48, height: 48, marginLeft: "10px" }}
            onClick={() => {
                console.log("Review Progress: ", store.spreadsheetUploadProgress);
                store.setActiveStep(2);
            }
            }
            >

                VIEW

            </div>
        </div>
    );
});

