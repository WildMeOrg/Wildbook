
import React from "react";
import BrutalismButton from "../BrutalismButton";

export default function LatestActivity({name, files, date, onViewClick}) {

    return (
        <div className="activity-item" style={{ marginBottom: '0.5em'}}>
          <h5>{name}</h5>
          <span>
            {files} files uploaded | {date}
          </span>
          <BrutalismButton>
            View
          </BrutalismButton>
        </div>
      );
}