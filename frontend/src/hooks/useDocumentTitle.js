import { useEffect } from "react";
import useGetSiteSettings from "../models/useGetSiteSettings";

export default function useDocumentTitle() {
  const { data: siteSettings } = useGetSiteSettings();
  document.title = siteSettings?.siteName || "wildbook";
  useEffect(() => {
    let iconURL = siteSettings?.siteFavicon;
    let link = document.querySelector("link[rel*='icon']");

    if (link) {
      link.href = iconURL;
    } else {
      link = document.createElement("link");
      link.type = "image/png";
      link.rel = "shortcut icon";
      link.href = iconURL;
      document.getElementsByTagName("head")[0].appendChild(link);
    }
  }, [siteSettings]);
}
