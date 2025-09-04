import React from "react";

export default function ImageCard() {
    return (
         <div className="d-flex flex-column justify-content-between mt-3"
            style={{
                padding: "10px",
                borderRadius: "10px",
                boxShadow: `0px 0px 10px rgba(0, 0, 0, 0.2)`,
                width: "100%",
                height: "600px",
                marginBottom: "30px",
            }}>
            <img src="https://via.placeholder.com/150" alt="Encounter" />
            <div className="image-card-content">
                <h5>Encounter Image</h5>
                <p>Description of the encounter image.</p>
            </div>
        </div>
    );
}