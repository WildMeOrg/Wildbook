import React, { useContext } from "react";
import { Row } from "react-bootstrap";
import { useIntl } from "react-intl";
import ThemeColorContext from "../ThemeColorProvider";
import PassKey from "../components/svg/PassKey";
import GalleryThumbnail from "../components/svg/GalleryThumbnail";
import DeleteSweep from "../components/svg/DeleteSweep";
import StackedEmail from "../components/svg/StackedEmail";
import Card from "../components/Card";

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
            <Card
              key={index}
              icon={card.icon}
              title={card.title}
              link={card.url}
              color={themeColor.primaryColors.primary800}
            />
          ))}
        </Row>
      </div>
    </div>
  );
};

export default AdminLogs;
