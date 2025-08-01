import React, { useEffect, useState } from "react";
import { Calendar, Views, dateFnsLocalizer } from "react-big-calendar";
import { format, parse, startOfWeek, getDay } from "date-fns";
import "react-big-calendar/lib/css/react-big-calendar.css";
import "./searchResultTabs.css";

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

export default function EncounterCalendar({ loadingAll }) {
  const [events, setEvents] = useState([]);
  const [view, setView] = useState(Views.MONTH);
  const filteredData = []; // This should be replaced with actual data fetching logic
  useEffect(() => {
    if (!filteredData || filteredData.length === 0) {
      setEvents([]);
      return;
    }

    const parsed = filteredData.map((item) => {
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
  }, [filteredData]);

  if (loadingAll) {
    return (
      <div
        className="spinner-border spinner-border-sm ms-1"
        role="status"
      >
        <span className="visually-hidden">
          Loading...
        </span>
      </div>
    );
  }

  return (
    <div
      className="container"
      style={{ padding: "1rem", backgroundColor: "#f8f9fa" }}
    >
      {/* <div style={{ marginBottom: "1rem", width: "100%", display: "flex", justifyContent: "flex-end" }}>
        <button onClick={() => setView(Views.MONTH)}>Grid</button>
        <button onClick={() => setView(Views.AGENDA)}>List</button>
      </div> */}

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
}
