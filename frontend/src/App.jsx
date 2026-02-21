import React, { useState, useRef } from "react";
import { IntlProvider } from "react-intl";
import messagesEn from "./locale/en.json";
import messagesEs from "./locale/es.json";
import messagesFr from "./locale/fr.json";
import messagesIt from "./locale/it.json";
import messagesDe from "./locale/de.json";
import "bootstrap/dist/css/bootstrap.min.css";
import "bootstrap-icons/font/bootstrap-icons.css";
import { QueryClient, QueryClientProvider } from "react-query";
import FrontDesk from "./FrontDesk";
import { BrowserRouter } from "react-router-dom";
import LocaleContext from "./IntlProvider";
import FooterVisibilityContext from "./FooterVisibilityContext";
import Cookies from "js-cookie";
import FilterContext from "./FilterContextProvider";
import { SiteSettingsProvider } from "./SiteSettingsContext";
import { ToastContainer } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";

function App() {
  const messageMap = {
    en: messagesEn,
    es: messagesEs,
    fr: messagesFr,
    it: messagesIt,
    de: messagesDe,
  };

  const initialLocale = Cookies.get("wildbookLangCode") || "en";
  const [locale, setLocale] = useState(initialLocale);

  const [visible, setVisible] = useState(true);

  const containerStyle = {
    display: "flex",
    flexDirection: "column",
    minHeight: "100vh",
  };

  const queryClientRef = useRef(null);
  if (!queryClientRef.current) queryClientRef.current = new QueryClient();

  const handleLocaleChange = (newLocale) => {
    setLocale(newLocale);
    Cookies.set("wildbookLangCode", newLocale);
  };

  const [filters, setFilters] = useState({});
  const updateFilter = (filterName, value) => {
    setFilters((prevFilters) => ({
      ...prevFilters,
      [filterName]: value,
    }));
  };

  const resetFilters = () => {
    setFilters({});
  };

  const publicUrl = process.env.PUBLIC_URL
    ? process.env.PUBLIC_URL.startsWith("http")
      ? new URL(process.env.PUBLIC_URL).pathname
      : process.env.PUBLIC_URL
    : "/";

  return (
    <QueryClientProvider client={queryClientRef.current}>
      <LocaleContext.Provider
        value={{ locale, onLocaleChange: handleLocaleChange }}
      >
        <div
          className="App mx-auto w-100 position-relative"
          style={containerStyle}
        >
          <BrowserRouter basename={publicUrl}>
            <IntlProvider
              locale={locale}
              defaultLocale="en"
              messages={messageMap[locale]}
            >
              <SiteSettingsProvider>
                <FooterVisibilityContext.Provider
                  value={{ visible, setVisible }}
                >
                  <FilterContext.Provider
                    value={{ filters, updateFilter, resetFilters }}
                  >
                    <FrontDesk
                      adminUserInitialized={true}
                      setLocale={setLocale}
                    />

                    <ToastContainer
                      position="top-right"
                      autoClose={3000}
                      hideProgressBar={false}
                      newestOnTop={false}
                      closeOnClick
                      rtl={false}
                      pauseOnFocusLoss
                      draggable
                      pauseOnHover
                    />
                  </FilterContext.Provider>
                </FooterVisibilityContext.Provider>
              </SiteSettingsProvider>
            </IntlProvider>
          </BrowserRouter>
        </div>
      </LocaleContext.Provider>
    </QueryClientProvider>
  );
}

export default App;
