import React, { useEffect, useState, useMemo } from "react";
import { Calendar, Views, dateFnsLocalizer } from "react-big-calendar";
import { format, parse, startOfWeek, getDay } from "date-fns";
import "react-big-calendar/lib/css/react-big-calendar.css";
import { observer } from "mobx-react-lite";
import FullScreenLoader from "../../../components/FullScreenLoader";
import useFilterEncounters from "../../../models/encounters/useFilterEncounters";
import { FormattedMessage } from "react-intl";

const locales = {
  "en-US": require("date-fns/locale/en-US"),
};

const localizer = dateFnsLocalizer({
  format,
  parse,
  startOfWeek,
  getDay,
  locales,
});

function startOfDayISO(d) {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x.toISOString();
}
function endOfDayISO(d) {
  const x = new Date(d);
  x.setHours(23, 59, 59, 999);
  return x.toISOString();
}

const CalendarTab = observer(({ store }) => {
  const [events, setEvents] = useState([]);
  const [view, setView] = useState(Views.MONTH);

  const toDefault = useMemo(() => new Date(), []);
  const fromDefault = useMemo(() => {
    const d = new Date();
    d.setMonth(d.getMonth() - 1);
    return d;
  }, []);

  const [from, setFrom] = useState(fromDefault);
  const [to, setTo] = useState(toDefault);

  const handleRangeChange = (range) => {
    const dates = Array.isArray(range) ? range : [range.start, range.end];
    const start = new Date(Math.min(...dates.map((d) => +new Date(d))));
    const end = new Date(Math.max(...dates.map((d) => +new Date(d))));
    setFrom(start);
    setTo(end);
  };

  const queries = useMemo(() => {
    const base = store.formFilters || [];
    const hasDate = base.some((f) => f.filterId === "date");
    if (hasDate) return base;

    return [
      ...base,
      {
        filterId: "date",
        clause: "filter",
        query: {
          range: {
            date: {
              gte: startOfDayISO(from),
              lte: endOfDayISO(to),
            },
          },
        },
        filterKey: "Sighting Date",
        path: "",
      },
    ];
  }, [store.formFilters, from, to]);

  const {
    data: encounterData,
    loading,
    refetch,
  } = useFilterEncounters({
    queries,
    params: { sort: "date", sortOrder: "desc" },
  });

  useEffect(() => {
    refetch?.();
  }, [queries, refetch]);

  useEffect(() => {
    const results = encounterData?.results || [];
    if (!encounterData || results.length === 0) {
      setEvents([]);
      return;
    }
    const parsed = results.map((item) => {
      const dateStrLocal = (item?.date || "").replace(/Z$/, "");
      const d = new Date(dateStrLocal);
      return {
        title: item.id,
        start: d,
        end: d,
        allDay: true,
        id: item.id,
        url: `/react/encounter?number=${item.id}`,
      };
    });
    setEvents(parsed);
  }, [encounterData]);

  return (
    <div>
      <div
        className="d-flex flex-row align-items-center gap-2"
        style={{ color: "white", height: "50px" }}
      >
        <FormattedMessage id="CALENDAR_VIEW_DESC" />
      </div>
      <div
        className="container"
        style={{
          backgroundColor: "#f8f9fa",
          position: "relative",
          borderRadius: "8px",
          padding: "20px",
        }}
      >
        {(loading || store.loadingAll) && <FullScreenLoader />}

        <Calendar
          localizer={localizer}
          events={events}
          view={view}
          onView={setView}
          startAccessor="start"
          endAccessor="end"
          onRangeChange={handleRangeChange}
          style={{ height: 600 }}
          onSelectEvent={(event) => {
            if (event?.url) {
              window.location.href = event.url;
            }
          }}
          eventPropGetter={() => ({
            style: {
              backgroundColor: "transparent",
              color: "#007bff",
              cursor: "pointer",
              border: "none",
              borderRadius: "4px",
            },
          })}
        />
      </div>
    </div>
  );
});

export default CalendarTab;
