import React from "react";
import { IntlProvider } from "react-intl";
import messages from "../locale/en.json";

export const wrapper = ({ children }) => (
  <IntlProvider locale="en" messages={messages}>
    {children}
  </IntlProvider>
);
