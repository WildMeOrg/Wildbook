import React, { useState, createContext } from "react";
import { IntlProvider } from "react-intl";
import messagesEn from "./locale/en.json";
import messagesEs from "./locale/es.json";
import messagesFr from "./locale/fr.json";
import messagesIt from "./locale/it.json";
import "bootstrap/dist/css/bootstrap.min.css";
import "bootstrap-icons/font/bootstrap-icons.css";
import { QueryClient, QueryClientProvider } from "react-query";
import FrontDesk from "./FrontDesk";
import { BrowserRouter, useLocation, useRoutes } from "react-router-dom";
import LocaleContext from "./IntlProvider";
import FooterVisibilityContext from "./FooterVisibilityContext";

function App() {
  const messageMap = {
    en: messagesEn,
    es: messagesEs,
    fr: messagesFr,
    it: messagesIt,
  };
  const [locale, setLocale] = useState("en");
  const [visible, setVisible] = useState(true);
  const containerStyle = {
    maxWidth: "1440px",
  };

  const queryClient = new QueryClient();

  const handleLocaleChange = (newLocale) => {
    console.log("handleLocaleChange", newLocale);
    setLocale(newLocale);
  };

  return (
    <QueryClientProvider client={queryClient}>
      <LocaleContext.Provider
        value={{ locale, onLocaleChange: handleLocaleChange }}
      >
        <div
          className="App mx-auto w-100 position-relative"
          style={containerStyle}
        >
          <BrowserRouter basename="/react">
            <IntlProvider
              locale={locale}
              defaultLocale="en"
              messages={messageMap[locale]}
            >
              <FooterVisibilityContext.Provider value={{ visible, setVisible }}>
                <FrontDesk adminUserInitialized={true} setLocale={setLocale} />
              </FooterVisibilityContext.Provider>
            </IntlProvider>
          </BrowserRouter>
        </div>
      </LocaleContext.Provider>
    </QueryClientProvider>
  );
}

export default App;
