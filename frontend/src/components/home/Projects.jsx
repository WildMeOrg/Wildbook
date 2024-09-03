import React from "react";
import Progress from "./Progress";
import { FormattedMessage } from "react-intl";

export default function Projects({ data }) {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "center",
        width: "100%",
        height: "400px",
        padding: "20px",
      }}
    >
      <div
        style={{
          width: "45%",
          display: "flex",
          flexDirection: "column",
          alignItems: "flex-end",
        }}
      >
        <h1
          style={{
            fontSize: "4em",
          }}
        >
          <FormattedMessage id="HOME_VIEW_PROJECT_1" />
        </h1>
        <h1
          style={{
            fontSize: "4em",
          }}
        >
          <FormattedMessage id="HOME_VIEW_PROJECT_2" />
        </h1>
      </div>
      <div
        style={{
          width: "45%",
          display: "flex",
          flexDirection: "column",
          alignItems: "flex-start",
          padding: "20px",
        }}
      >
        {Array.isArray(data) && data.length ? (
          data?.map((item, index) => {
            return (
              <Progress
                key={index}
                name={item.name}
                encounters={item.numberEncounters}
                progress={item.percentComplete}
                href={`/projects/project.jsp?id=${item.id}`}
                noUnderline
                newTab
              />
            );
          })
        ) : (
          <h1>
            <FormattedMessage id="HOME_NO_PROJECT" />
          </h1>
        )}
        <a
          href="/projects/projectList.jsp"
          style={{
            color: "black",
            fontWeight: "600",
            textDecoration: "none",
            marginTop: "20px",
            fontSize: "1.1em",
            display: "flex",
            flexDirection: "row",
            justifyContent: "center",
            alignItems: "center",
          }}
          target="_blank"
        >
          <FormattedMessage id="SEE_ALL" />
          <i
            className="bi bi-arrow-right-short"
            style={{ fontSize: 22 }}
          ></i>{" "}
        </a>
      </div>
    </div>
  );
}
