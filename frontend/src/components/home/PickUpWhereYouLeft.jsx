import React, { useContext } from "react";
import "bootstrap/dist/css/bootstrap.min.css";
import LatestActivityItem from "./LatestActivityItem";
import { formatDate } from "../../utils/formatters";
import { FormattedMessage } from "react-intl";
import ThemeColorContext from "../../ThemeColorProvider";

const PickUp = ({ data }) => {
  const theme = useContext(ThemeColorContext);
  const matchActionDate = data?.latestMatchTask?.dateTimeCreated || new Date();
  const date = new Date(matchActionDate);
  const now = new Date();
  const twoWeeksAgo = new Date(now.getTime() - 14 * 24 * 60 * 60 * 1000);
  const matchActionButtonUrl =
    date > twoWeeksAgo
      ? `/iaResults.jsp?taskId=${data?.latestMatchTask?.id}`
      : `/encounters/encounter.jsp?number=${data?.latestMatchTask?.encounterId}`;

  return (
    <div className="position-relative mt-5" style={{ height: "500px" }}>
      <div
        className="col-8 position-absolute p-2"
        style={{
          left: "100px",
          width: "500px",
          zIndex: 1,
        }}
      >
        <h1 style={{ fontSize: "4em" }}>
          <FormattedMessage id="HOME_PICK_UP_1" />
        </h1>
        <h1 style={{ fontSize: "4em" }}>
          <FormattedMessage id="HOME_PICK_UP_2" />
        </h1>
        <LatestActivityItem
          name="HOME_LATEST_BULK_REPORT"
          num={data?.latestBulkImportTask?.numberMediaAssets || "0"}
          date={formatDate(data?.latestBulkImportTask?.dateTimeCreated, true)}
          text={data?.latestBulkImportTask}
          disabled={!data?.latestBulkImportTask}
          latestId={`/import.jsp?taskId=${data?.latestBulkImportTask?.id}`}
        />
        <LatestActivityItem
          name="HOME_LATEST_INDIVIDUAL"
          date={formatDate(data?.latestIndividual?.dateTimeCreated, true)}
          text={data?.latestIndividual}
          disabled={!data?.latestIndividual}
          latestId={`/individuals.jsp?id=${data?.latestIndividual?.id}`}
        />
        <LatestActivityItem
          name="HOME_LATEST_MATCHING_ACTION"
          date={formatDate(data?.latestMatchTask?.dateTimeCreated, true)}
          text={data?.latestMatchTask}
          disabled={!data?.latestMatchTask}
          latestId={matchActionButtonUrl}
        />
      </div>
      <div
        style={{
          backgroundColor: theme.statusColors.blue100,
          position: "absolute",
          top: 0,
          left: "40%",
          bottom: "10%",
          borderRadius: "10px 0 0 10px",
          width: "60%",
        }}
      ></div>
      <div
        className="col-4 position-absolute"
        style={{
          top: "10%",
          left: "55%",
          width: "300px",
          borderRadius: "10px",
          height: "450px",
          zIndex: 1,
          backgroundImage: "url(/react/images/pick.png)",
        }}
      ></div>
    </div>
  );
};

export default PickUp;
