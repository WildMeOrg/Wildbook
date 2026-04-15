/* eslint-disable react/display-name */
import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../components/generalInputs/SelectInput", () => (props) => (
  <select
    data-testid={`select-${props.label}`}
    value={props.value || ""}
    onChange={(e) => props.onChange && props.onChange(e.target.value)}
  >
    <option value="" />
    {(props.options || []).map((opt) => (
      <option key={String(opt)} value={opt}>
        {String(opt)}
      </option>
    ))}
  </select>
));

jest.mock("../../../components/generalInputs/TextInput", () => (props) => (
  <input
    data-testid={`text-${props.label}`}
    value={props.value || ""}
    onChange={(e) => props.onChange && props.onChange(e.target.value)}
  />
));

global.__SEARCH_PROPS__ = {};
jest.mock(
  "../../../components/generalInputs/SearchAndSelectInput",
  () => (props) => {
    global.__SEARCH_PROPS__[props.label] = props;
    return <div data-testid={`search-${props.label}`} />;
  },
);

jest.mock("../../../components/MainButton", () => (props) => (
  <button
    data-testid={`btn-${props.children?.props?.id || "button"}`}
    onClick={props.onClick}
  >
    {props.children}
  </button>
));

jest.mock("react-bootstrap", () => ({
  Alert: (p) => <div role="alert">{p.children}</div>,
}));

import { IdentifySectionEdit } from "../../../pages/Encounter/IdentifySectionEdit";

const makeStore = (overrides = {}) => ({
  identificationRemarksOptions: ["auto", "manual"],
  individualOptions: [],
  setIndividualOptions: jest.fn(),
  searchIndividualsByNameAndId: jest.fn(),
  searchSightingsById: jest.fn(),
  setFieldValue: jest.fn(),
  setEditIdentifyCard: jest.fn(),
  removeIndividualFromEncounter: jest.fn(),
  removeOccurrenceIdFromEncounter: jest.fn(),
  getFieldValue: jest.fn(() => ""),
  encounterData: {},
  errors: {
    hasSectionError: jest.fn(() => false),
    getSectionErrors: jest.fn(() => []),
  },
  ...overrides,
});

describe("IdentifySectionEdit", () => {
  beforeEach(() => {
    global.__SEARCH_PROPS__ = {};
  });

  test("effect pre-populates individualOptions when current id not in options", () => {
    const store = makeStore({
      getFieldValue: jest.fn((section, key) =>
        key === "individualId"
          ? 42
          : key === "individualDisplayName"
            ? "Alpha"
            : "",
      ),
      individualOptions: [],
    });

    render(<IdentifySectionEdit store={store} />);

    expect(store.setIndividualOptions).toHaveBeenCalledTimes(1);
    const arg = store.setIndividualOptions.mock.calls[0][0];
    expect(arg[0]).toEqual({ value: "42", label: "Alpha" });
  });

  test("effect does not duplicate when id already exists in options", () => {
    const store = makeStore({
      getFieldValue: jest.fn((section, key) =>
        key === "individualId"
          ? 42
          : key === "individualDisplayName"
            ? "Alpha"
            : "",
      ),
      individualOptions: [{ value: "42", label: "Z" }],
    });

    render(<IdentifySectionEdit store={store} />);

    expect(store.setIndividualOptions).not.toHaveBeenCalled();
  });

  test("changing MATCHED_BY select updates identificationRemarks", async () => {
    const user = userEvent.setup();
    const store = makeStore({
      getFieldValue: jest.fn((s, k) =>
        k === "identificationRemarks" ? "" : "",
      ),
    });

    render(<IdentifySectionEdit store={store} />);

    await user.selectOptions(screen.getByTestId("select-MATCHED_BY"), "manual");
    expect(store.setFieldValue).toHaveBeenCalledWith(
      "identify",
      "identificationRemarks",
      "manual",
    );
  });

  test("INDIVIDUAL_ID loadOptions directly invokes searchIndividualsByNameAndId with raw query string", async () => {
    const store = makeStore();

    store.searchIndividualsByNameAndId.mockResolvedValueOnce({
      data: { hits: [] },
    });

    render(<IdentifySectionEdit store={store} />);

    const props = global.__SEARCH_PROPS__["INDIVIDUAL_ID"];
    await props.loadOptions("  In-42  ");

    expect(store.searchIndividualsByNameAndId).toHaveBeenCalledTimes(1);
    expect(store.searchIndividualsByNameAndId).toHaveBeenCalledWith("  In-42  ");
  });

  test("INDIVIDUAL_ID loadOptions maps search results and sets individual options", async () => {
    const store = makeStore();
    store.searchIndividualsByNameAndId.mockResolvedValueOnce({
      data: {
        hits: [
          { id: 5, displayName: "Ind5" },
          { id: 6, displayName: "Ind6" },
        ],
      },
    });

    render(<IdentifySectionEdit store={store} />);

    const props = global.__SEARCH_PROPS__["INDIVIDUAL_ID"];
    const res = await props.loadOptions("in");

    expect(store.searchIndividualsByNameAndId).toHaveBeenCalledWith("in");
    expect(store.setIndividualOptions).toHaveBeenCalledWith([
      { value: "5", label: "Ind5" },
      { value: "6", label: "Ind6" },
    ]);
    expect(res).toEqual([
      { value: "5", label: "Ind5" },
      { value: "6", label: "Ind6" },
    ]);
  });

  test("INDIVIDUAL_ID onChange sets field value", async () => {
    const store = makeStore({
      getFieldValue: jest.fn((s, k) => (k === "individualId" ? "" : "")),
    });

    render(<IdentifySectionEdit store={store} />);

    const props = global.__SEARCH_PROPS__["INDIVIDUAL_ID"];
    props.onChange("123");
    expect(store.setFieldValue).toHaveBeenCalledWith(
      "identify",
      "individualId",
      "123",
    );
  });

  test("ALTERNATE_ID text change triggers setFieldValue", async () => {
    const store = makeStore({
      getFieldValue: jest.fn((s, k) => (k === "otherCatalogNumbers" ? "" : "")),
    });

    render(<IdentifySectionEdit store={store} />);

    fireEvent.change(screen.getByTestId("text-ALTERNATE_ID"), {
      target: { value: "ALT-9" },
    });
    expect(store.setFieldValue).toHaveBeenCalledWith(
      "identify",
      "otherCatalogNumbers",
      "ALT-9",
    );
  });

  test("shows UNASSIGN_INDIVIDUAL and clicks call remove + close when encounter has individualId", async () => {
    const user = userEvent.setup();
    const store = makeStore({
      encounterData: { individualId: "42" },
    });

    render(<IdentifySectionEdit store={store} />);

    const btn = screen.getByTestId("btn-UNASSIGN_INDIVIDUAL");
    await user.click(btn);

    expect(store.setEditIdentifyCard).toHaveBeenCalledWith(false);
    expect(store.removeIndividualFromEncounter).toHaveBeenCalledTimes(1);
  });

  test("shows REMOVE_FROM_SIGHTING and clicks call remove occurrence + close when has occurrenceId", async () => {
    const user = userEvent.setup();
    const store = makeStore({
      encounterData: { occurrenceId: "occ-1" },
    });

    render(<IdentifySectionEdit store={store} />);

    const btn = screen.getByTestId("btn-REMOVE_FROM_SIGHTING");
    await user.click(btn);

    expect(store.removeOccurrenceIdFromEncounter).toHaveBeenCalledTimes(1);
    expect(store.setEditIdentifyCard).toHaveBeenCalledWith(false);
  });

  test("SIGHTING_ID loadOptions filters to access=full and maps ids", async () => {
    const store = makeStore();
    store.searchSightingsById.mockResolvedValueOnce({
      data: {
        hits: [
          { id: "s1", access: "full" },
          { id: "s2", access: "restricted" },
          { id: "s3", access: "full" },
        ],
      },
    });

    render(<IdentifySectionEdit store={store} />);

    const props = global.__SEARCH_PROPS__["SIGHTING_ID"];
    const res = await props.loadOptions("s");

    expect(store.searchSightingsById).toHaveBeenCalledWith("s");
    expect(res).toEqual([
      { value: "s1", label: "s1" },
      { value: "s3", label: "s3" },
    ]);
  });

  test("shows section Alert when identify section has errors", () => {
    const store = makeStore({
      errors: {
        hasSectionError: jest.fn((section) => section === "identify"),
        getSectionErrors: jest.fn(() => ["e1", "e2"]),
      },
    });

    render(<IdentifySectionEdit store={store} />);
    expect(screen.getByRole("alert")).toHaveTextContent("e1;e2");
  });
});