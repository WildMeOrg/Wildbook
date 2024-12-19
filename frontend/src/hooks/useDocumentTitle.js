import { useEffect } from "react";
import useGetSiteSettings from "../models/useGetSiteSettings";

export default function useDocumentTitle() {
  const { data: siteSettings } = useGetSiteSettings();

  console.log("siteSettings", siteSettings);
  document.title = siteSettings?.siteName || "wildbook";
  useEffect(() => {
    if (!siteSettings) return;
    let iconURL = siteSettings?.siteFavicon;
    let link = document.querySelector("link[rel*='icon']");
    const metaDescription = document.querySelector("meta[name='description']");
    const metaKeywords = document.querySelector("meta[name='keywords']");
    const metaAuthor = document.querySelector("meta[name='author']");

    if (!metaDescription) {
      const meta = document.createElement("meta");
      meta.name = "description";
      meta.content = siteSettings?.siteDescription;
      document.getElementsByTagName("head")[0].appendChild(meta);
    }

    if (!metaKeywords) {
      const meta = document.createElement("meta");
      meta.name = "keywords";
      meta.content = siteSettings?.siteKeywords;
      document.getElementsByTagName("head")[0].appendChild(meta);
    }

    if (!metaAuthor) {
      const meta = document.createElement("meta");
      meta.name = "author";
      meta.content = siteSettings?.siteAuthor;
      document.getElementsByTagName("head")[0].appendChild(meta);
    }

    console.log(
      "++++++++++++",
      siteSettings.siteDescription,
      siteSettings.siteKeywords,
      siteSettings.siteAuthor,
    );

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
