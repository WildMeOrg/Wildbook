import React from "react";
import DiamondCard from "../DiamondCard";
import More from "../CircledMoreButton";
import { formatDate } from "../../utils/formatters";
import { FormattedMessage } from "react-intl";

export default function LatestData({ data, username, loading = true }) {
  return (
    <div className="d-flex flex-column align-items-center justify-content-center p-3 mt-5">
      <h1 style={{ fontSize: 48 }}>
        <FormattedMessage id="HOME_LATEST_DATA" />
      </h1>
      <div className="d-flex flex-row justify-content align-items-center">
        {data.map((sighting) => {
          const formattedDate =
            formatDate(sighting.date, true) || sighting.dateTime;
          return (
            <DiamondCard
              key={sighting?.id}
              date={formattedDate}
              title={sighting.taxonomy}
              annotations={sighting.numberAnnotations}
            />
          );
        })}

        <More
          href={`/react/encounter-search?username=${username}`}
          loading={loading}
        />
      </div>
    </div>
  );
}
