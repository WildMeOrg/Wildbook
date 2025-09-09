import React from "react";
import MailIcon from "../../components/icons/MailIcon";
import { observer } from "mobx-react-lite";

const ImageCard = observer(({
    store = {}
}) => {
    console.log("111", store.selectedImageIndex);
    return (
        <div className="d-flex flex-column justify-content-between mt-3 position-relative mb-3"
            style={{
                padding: "10px",
                borderRadius: "10px",
                boxShadow: `0px 0px 10px rgba(0, 0, 0, 0.2)`,
                width: "100%"
            }}>
            <div className="mb-3 ms-1 d-flex flex-col align-items-center">
                <MailIcon />
                <span style={{ marginLeft: "10px", fontSize: "1rem", fontWeight: "bold" }}>Images</span>
            </div>

            <img
                src={store.encounterData?.mediaAssets[store.selectedImageIndex]?.url || ""}
                alt="No image available"
                style={{ width: "100%", height: "auto", borderRadius: "5px" }}
            />



            <div className="d-flex flex-wrap align-items-center mt-2" style={{ gap: 8 }}>
                {store.encounterData?.mediaAssets.map((asset, index) => (
                    <img
                        key={index}
                        src={asset.url}
                        alt={`${asset.url} ${index}`}
                        style={{
                            width: 100,
                            height: "auto",
                            borderRadius: 5,
                            cursor: "pointer",
                            border: store.selectedImageIndex === index ? "2px solid blue" : "2px solid transparent",
                        }}
                        onClick={() => store.setSelectedImageIndex(index)}
                    />
                ))}
            </div>
        </div>
    );
})

export default ImageCard;