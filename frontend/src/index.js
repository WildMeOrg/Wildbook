import React from "react";
import ReactDOM from "react-dom/client";
import "./index.css";
import App from "./App";
import * as serviceWorkerRegistration from "./serviceWorkerRegistration";
import {
  handleUpdateFound,
  watchForControllerChange,
} from "./utils/serviceWorkerUpdates";
import reportWebVitals from "./reportWebVitals";

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);

// See utils/serviceWorkerUpdates.js for why a new version is never taken live under a page
// that is already open (issue #1759).
if ("serviceWorker" in navigator) {
  watchForControllerChange(navigator.serviceWorker, () =>
    window.location.reload(),
  );
}

serviceWorkerRegistration.register({
  onUpdate: handleUpdateFound,
  onSuccess: () => {
    console.log("Wildbook is ready for offline use.");
  },
});

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
