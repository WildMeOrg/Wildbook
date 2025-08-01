import React from "react";
import LandingImage from "../components/home/LandingImage";
import LatestData from "../components/home/LatestData";
import PickUp from "../components/home/PickUpWhereYouLeft";
import Report from "../components/home/Report";
import Projects from "../components/home/Projects";
import useDocumentTitle from "../hooks/useDocumentTitle";
import useGetHomePageInfo from "../models/useGetHomePageInfo";
import NotFound from "./errorPages/NotFound";
import ServerError from "./errorPages/ServerError";
import BadRequest from "./errorPages/BadRequest";
import Unauthorized from "./errorPages/Unauthorized";
import Forbidden from "./errorPages/Forbidden";

export default function Home() {
  useDocumentTitle("HOME");
  const { data, loading, statusCode } = useGetHomePageInfo();

  const errorComponents = {
    404: NotFound,
    500: ServerError,
    400: BadRequest,
    401: Unauthorized,
    403: Forbidden,
  };

  const ErrorComponent = errorComponents[statusCode];

  if (ErrorComponent) {
    return <ErrorComponent />;
  }

  return (
    <div className="col-12">
      <LandingImage />
      <LatestData
        data={data?.latestEncounters || []}
        username={data?.user?.username}
        loading={loading}
      />
      <PickUp data={data} />
      <Report />
      <Projects data={data?.projects} />
    </div>
  );
}
