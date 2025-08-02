import { render, waitFor } from "@testing-library/react";
import AnalyticsAndTagManager from "../GoogleTagManager";
import React from "react";

// Mock global objects
beforeEach(() => {
  document.head.innerHTML = ""; // Clean head between tests
  global.console.error = jest.fn();
  window.wildbookGlobals = {
    gtmKey: "GTM-TEST",
    gaId: "GA-TEST",
  };
});

afterEach(() => {
  jest.restoreAllMocks();
});

test("loads JavascriptGlobals.js and inserts it with the correct ID", async () => {
  render(<AnalyticsAndTagManager />);
  await waitFor(() => {
    expect(document.getElementById("javascriptglobals")).toBeTruthy();
  });
});

test("handles script load failure", async () => {
  const originalCreateElement = document.createElement;

  document.createElement = (tagName) => {
    const el = originalCreateElement.call(document, tagName);
    if (tagName === "script") {
      setTimeout(() => el.onerror && el.onerror(new Error("load error")), 0);
    }
    return el;
  };

  render(<AnalyticsAndTagManager />);
  await waitFor(() => {
    expect(console.error).toHaveBeenCalledWith(
      "Failed to load script:",
      expect.any(Error),
    );
  });

  document.createElement = originalCreateElement;
});
