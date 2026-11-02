import React from "react";
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import MobilePackExport from "../../pages/SearchPages/components/MobilePackExport";
import messages from "../../locale/en.json";

const renderExport = () => render(<IntlProvider locale="en" messages={messages}>
  <MobilePackExport searchQueryId="saved-search" />
</IntlProvider>);
const response = (body, ok = true) => Promise.resolve({ ok, json: async () => body });
const configuration = { enabled: true, profiles: [{ taxonomy: "Equus caballus", featureClass: "horse+face", label: "Equus caballus — horse+face" }] };

afterEach(() => { cleanup(); jest.restoreAllMocks(); });

it("does not expose publishing when the server disables it", async () => {
  global.fetch = jest.fn(() => response({ enabled: false }));
  renderExport();
  await waitFor(() => expect(global.fetch).toHaveBeenCalled());
  expect(screen.queryByText("MOBILE_PACK_TITLE")).toBeNull();
});

it("requires an explicit public-release acknowledgement and sends the saved search", async () => {
  global.fetch = jest.fn().mockImplementationOnce(() => response(configuration))
    .mockImplementationOnce(() => response({ jobId: "job-one", packId: "pk-one" }));
  renderExport();
  const button = await screen.findByRole("button", { name: "MOBILE_PACK_PUBLISH" });
  expect(button.disabled).toBe(true);
  const inputs = screen.getAllByRole("textbox");
  fireEvent.change(inputs[0], { target: { value: "Example horses" } });
  fireEvent.change(screen.getByRole("combobox"), { target: { value: "0" } });
  expect(button.disabled).toBe(true);
  fireEvent.click(screen.getByRole("checkbox"));
  fireEvent.click(button);
  await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(2));
  const [, options] = global.fetch.mock.calls[1];
  expect(options.headers["X-Requested-With"]).toBe("XMLHttpRequest");
  expect(JSON.parse(options.body)).toEqual({ searchQueryId: "saved-search", displayName: "Example horses",
    taxonomy: "Equus caballus", featureClass: "horse+face", confirmPublic: true });
});

it("contains a rejected configuration request", async () => {
  global.fetch = jest.fn().mockRejectedValue(new Error("offline"));
  renderExport();
  await waitFor(() => expect(global.fetch).toHaveBeenCalled());
  expect(screen.queryByRole("button")).toBeNull();
});
