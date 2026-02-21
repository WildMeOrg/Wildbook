import React, { createContext, useContext } from "react";
import useGetSiteSettings from "./models/useGetSiteSettings";

const SiteSettingsContext = createContext(null);

export const SiteSettingsProvider = ({ children }) => {
  const { data, isLoading, error } = useGetSiteSettings();
  return (
    <SiteSettingsContext.Provider
      value={{ data: data ?? {}, isLoading, error }}
    >
      {children}
    </SiteSettingsContext.Provider>
  );
};

export const useSiteSettings = () => {
  const context = useContext(SiteSettingsContext);
  if (context === null) {
    throw new Error("useSiteSettings must be used within SiteSettingsProvider");
  }
  return context;
};
