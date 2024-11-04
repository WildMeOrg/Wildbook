import React, { useContext } from "react";
import { Row } from "react-bootstrap";
import { useIntl } from "react-intl";
import ThemeColorContext from "../ThemeColorProvider";
import PassKey from "../components/svg/PassKey";
import GalleryThumbnail from "../components/svg/GalleryThumbnail";
import DeleteSweep from "../components/svg/DeleteSweep";
import StackedEmail from "../components/svg/StackedEmail";

export const AdminLogs = () => {
  const themeColor = useContext(ThemeColorContext);
  const intl = useIntl();

  const cards = [
    {
      title: intl.formatMessage({ id: "USER_ACCESS_LOG" }),
      icon: <PassKey />,
      url: "/wildbook_data_dir/logs/user-access.htm",
    },
    {
      title: intl.formatMessage({ id: "ENCOUNTER_SUBMISSION_LOG" }),
      icon: <GalleryThumbnail />,
      url: "/wildbook_data_dir/logs/encounter-submission.htm",
    },
    {
      title: intl.formatMessage({ id: "DELETED_ENCOUNTERS_LOG" }),
      icon: <DeleteSweep />,
      url: "/wildbook_data_dir/logs/encounter-delete.htm",
    },
    {
      title: intl.formatMessage({ id: "EMAIL_LOG" }),
      icon: <StackedEmail />,
      url: "/wildbook_data_dir/logs/email.htm",
    },
  ];
  return (
    <div
      style={{
        // eslint-disable-next-line no-undef
        backgroundImage: `url(${process.env.PUBLIC_URL}/images/List_of_Logs_Image.png)`,
        backgroundSize: "cover",
        backgroundPosition: "center",
        width: "100%",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
      }}
    >
      <div
        style={{
          backgroundColor: "rgba(255, 255, 255, 0.1)",
          padding: "20px",
          borderRadius: "10px",
          backdropFilter: "blur(3px)",
          WebkitBackdropFilter: "blur(3px)", // For Safari compatibility
        }}
      >
        <h1 className="display-1 text-white fw-bold">Logs</h1>
        <Row>
          {cards.map((card, index) => (
            <div
              key={index}
              className="bg-white d-flex flex-column align-items-center p-3 m-3 fw-semibold"
              onClick={() => (window.location.href = card.url)}
              style={{
                height: "170px",
                width: "170px",
                color: themeColor.primaryColors.primary800,
                borderRadius: "20px",
                cursor: "pointer",
              }}
            >
              <div className="d-flex align-items-center justify-content-center mt-2">
                {card.icon}
              </div>
              <span
                className="text-center"
                style={{
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  maxWidth: "100%",
                }}
                title={card.title}
              >
                {card.title}
              </span>
            </div>
          ))}
        </Row>
      </div>
    </div>
  );
};

export default AdminLogs;
