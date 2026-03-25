import React, { useContext, useMemo } from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import SimpleDataTable from "../../../components/SimpleDataTable";
import ThemeColorContext from "../../../ThemeColorProvider";

const EncountersTableView = observer(({ store }) => {
  const theme = useContext(ThemeColorContext);

  const linkStyle = {
    color: theme.primaryColors.primary500,
    textDecoration: "underline",
  };

  const columns = useMemo(
    () => [
      {
        name: <FormattedMessage id="DATE" defaultMessage="Date" />,
        selector: (row) => row.date || "-",
        sortable: true,
      },
      {
        name: <FormattedMessage id="LOCATION" defaultMessage="Location" />,
        selector: (row) => row.locationName || row.verbatimLocality || "-",
        sortable: true,
      },
      {
        name: <FormattedMessage id="ENCOUNTER" defaultMessage="Encounter" />,
        selector: (row) => row.id || "",
        cell: (row) =>
          row.id ? (
            <a
              href={`/react/encounter?number=${row.id}`}
              target="_blank"
              rel="noopener noreferrer"
              style={linkStyle}
            >
              {row.id}
            </a>
          ) : (
            "-"
          ),
        sortable: true,
      },
      {
        name: <FormattedMessage id="SIGHTING" defaultMessage="Sighting" />,
        selector: (row) => row.occurrenceId || "",
        cell: (row) =>
          row.occurrenceId ? (
            <a
              href={`/occurrence.jsp?number=${row.occurrenceId}`}
              target="_blank"
              rel="noopener noreferrer"
              style={linkStyle}
            >
              {row.occurrenceId}
            </a>
          ) : (
            "-"
          ),
        sortable: true,
      },
      {
        name: (
          <FormattedMessage id="COOCCURRENCE" defaultMessage="Cooccurrence" />
        ),
        selector: (row) =>
          (row.cooccurrenceIndividuals || [])
            .map((item) => item.displayName || item.name || item.id)
            .join(", "),
        cell: (row) => {
          const individuals = row.cooccurrenceIndividuals || [];

          if (!individuals.length) {
            return "-";
          }

          return (
            <div>
              {individuals.map((item, index) => {
                const label = item.displayName || item.name || item.id || "-";
                const href = item.id ? `/react/individual?id=${item.id}` : null;

                return (
                  <React.Fragment key={item.id || `${label}-${index}`}>
                    {href ? (
                      <a
                        href={href}
                        target="_blank"
                        rel="noopener noreferrer"
                        style={linkStyle}
                      >
                        {label}
                      </a>
                    ) : (
                      <span>{label}</span>
                    )}
                    {index < individuals.length - 1 ? ", " : ""}
                  </React.Fragment>
                );
              })}
            </div>
          );
        },
        sortable: true,
        grow: 1.5,
      },
      {
        name: <FormattedMessage id="BEHAVIOR" defaultMessage="Behavior" />,
        selector: (row) =>
          Array.isArray(row.behavior)
            ? row.behavior.join(", ")
            : row.behavior || "-",
        cell: (row) => (
          <span>
            {Array.isArray(row.behavior)
              ? row.behavior.join(", ")
              : row.behavior || "-"}
          </span>
        ),
        sortable: true,
      },
    ],
    [theme],
  );

  const data = useMemo(() => {
    return store.encounters.map((encounter) => ({
      ...encounter,
      tableID: encounter.id,
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
