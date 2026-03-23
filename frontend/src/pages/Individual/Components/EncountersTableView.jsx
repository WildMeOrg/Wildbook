import React, { useContext, useMemo } from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import SimpleDataTable from "../../../components/SimpleDataTable";
import ThemeColorContext from "../../../ThemeColorProvider";

const EncountersTableView = observer(({ store }) => {
  const theme = useContext(ThemeColorContext);

  const columns = useMemo(
    () => [
      {
        name: <FormattedMessage id="DATE" />,
        selector: (row) => row.date || "-",
        sortable: true,
      },
      {
        name: <FormattedMessage id="LOCATION" />,
        selector: (row) =>
          row.locationName || row.verbatimLocality || "-",
        sortable: true,
      },
      {
        name: <FormattedMessage id="ENCOUNTER" />,
        selector: (row) => row.id,
        cell: (row) => (
          <a
            href={`/react/encounter?number=${row.id}`}
            target="_blank"
            rel="noopener noreferrer"
            style={{
              color: theme.primaryColors.primary500,
              textDecoration: "none",
            }}
          >
            {row.id}
          </a>
        ),
        sortable: true,
      },
      {
        name: <FormattedMessage id="SIGHTING_ID" />,
        selector: (row) => row.occurrenceId,
        cell: (row) =>
          row.occurrenceId ? (
            <a
              href={`/occurrence.jsp?number=${row.occurrenceId}`}
              target="_blank"
              rel="noopener noreferrer"
              style={{
                color: theme.primaryColors.primary500,
                textDecoration: "none",
              }}
            >
              {row.occurrenceId}
            </a>
          ) : (
            "-"
          ),
        sortable: true,
      },
      {
        name: <FormattedMessage id="ANNOTATIONS" />,
        selector: (row) =>
          row.mediaAssets?.reduce(
            (acc, asset) => acc + (asset.annotations?.length || 0),
            0,
          ) || 0,
        sortable: true,
      },
    ],
    [theme],
  );

  const data = useMemo(() => {
    return store.encounters.map((enc) => ({
      ...enc,
      tableID: enc.id,
    }));
  }, [store.encounters]);

  if (store.encountersLoading) {
    return (
      <div
        className="d-flex justify-content-center align-items-center"
        style={{ minHeight: "300px" }}
      >
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div
        className="d-flex justify-content-center align-items-center"
        style={{ minHeight: "300px" }}
      >
        <p className="text-muted">
          <FormattedMessage
            id="NO_ENCOUNTERS_FOUND"
            defaultMessage="No encounters found"
          />
        </p>
      </div>
    );
  }

  return (
    <div
      style={{
        borderRadius: "10px",
        overflow: "hidden",
        backgroundColor: theme.defaultColors.white,
      }}
    >
      <SimpleDataTable columns={columns} data={data} perPage={10} />
    </div>
  );
});

export default EncountersTableView;
