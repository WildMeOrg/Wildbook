import React, { useEffect, useState } from "react";
import { Calendar, Views, dateFnsLocalizer } from "react-big-calendar";
import { format, parse, startOfWeek, getDay } from "date-fns";
import "react-big-calendar/lib/css/react-big-calendar.css";
import "./searchResultTabs.css";
import { observer } from "mobx-react-lite";
import FullScreenLoader from "../../../components/FullScreenLoader";

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

const CalendarTab = observer(({ store }) => {
  const [events, setEvents] = useState([]);
  const [view, setView] = useState(Views.MONTH);
  useEffect(() => {
    if (!store.searchResultsAll || store.searchResultsAll.length === 0) {
      setEvents([]);
      return;
    }

    console.log("searchResultsAll", JSON.stringify(store.searchResultsAll));

    const parsed = store?.searchResultsAll?.map((item) => {
      const dateStrLocal = item?.date?.replace(/Z$/, "");
      const d = new Date(dateStrLocal);
      return {
        title: item.id,
        start: d,
        end: d,
        id: item.id,
        url: `/encounters/encounter.jsp?number=${item.id}`,
      };
    });
    setEvents(parsed);
  }, [store.searchResultsAll]);

  return (
    <div
      className="container mt-1"
      style={{ backgroundColor: "#f8f9fa", position: "relative" }}
    >
      {/* <div style={{ marginBottom: "1rem", width: "100%", display: "flex", justifyContent: "flex-end" }}>
        <button onClick={() => setView(Views.MONTH)}>Grid</button>
        <button onClick={() => setView(Views.AGENDA)}>List</button>
      </div> */}
      {store.loadingAll && <FullScreenLoader />}
      <Calendar
        localizer={localizer}
        events={events}
        view={view}
        onView={setView}
        startAccessor="start"
        endAccessor="end"
        style={{ height: 600 }}
        // popup
        toolbar={true}
        components={{
          event: ({ event }) => (
            <a
              href={event.url}
              target="_blank"
              rel="noreferrer"
              style={{
                background: "transparent",
                color: "#007bff !important",
                padding: 0,
                textDecoration: "underline",
              }}
            >
              {event.id}
            </a>
          ),
        }}
      />
    </div>
  );
})

export default CalendarTab;
