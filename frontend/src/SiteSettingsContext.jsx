import React, { createContext, useContext } from "react";
import useGetSiteSettings from "./models/useGetSiteSettings";
import LoadingScreen from "./components/LoadingScreen";

const SiteSettingsContext = createContext(null);

export const SiteSettingsProvider = ({ children }) => {
  const { data, isLoading, error } = useGetSiteSettings();

  if (isLoading) return <LoadingScreen />;

  if (error) {
    console.error("Error fetching site settings:", error);
  }

  return (
    <SiteSettingsContext.Provider value={data ?? {}}>
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
