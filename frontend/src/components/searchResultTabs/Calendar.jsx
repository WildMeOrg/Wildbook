import React, { useEffect, useState } from "react";
import { Calendar, Views, dateFnsLocalizer } from "react-big-calendar";
import { format, parse, startOfWeek, getDay } from "date-fns";
import "react-big-calendar/lib/css/react-big-calendar.css";
import "./calendar.css";

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

export default function EncounterCalendar({
  filteredData = [],
}) {
  const [events, setEvents] = useState([]);
  const [view, setView] = useState(Views.MONTH);

  useEffect(() => {
    const data = [
      { id: "testencounter1", created: "2025-07-01T10:00:00Z" },
      { id: "testencounter2", created: "2025-07-01T12:00:00Z" },
      { id: "testencounter3", created: "2025-07-05T14:00:00Z" },
      { id: "testencounter4", created: "2025-07-28T14:00:00Z" },
    ];
    const parsed = data.map((item) => ({
      title: `/encounter/jsp?id=${item.id}`,
      start: new Date(item.created),
      end: new Date(item.created),
      id: item.id,
    }));
    setEvents(parsed);
  }, []);

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
        popup
        toolbar={true}
        components={{
          event: ({ event }) => (
            <a
              href={`/encounter/jsp?id=${event.id}`}
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
