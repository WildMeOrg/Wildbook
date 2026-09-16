/* eslint-disable react/display-name */
import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id }) => <span>{id}</span>,
}));

jest.mock("../../../components/generalInputs/SelectInput", () => (props) => (
  <select
    data-testid="assigned-user-select"
    value={props.value}
    onChange={(e) => props.onChange && props.onChange(e.target.value)}
  >
    <option value="">--</option>
    {(props.options || []).map((o) => (
      <option key={o.value} value={o.value}>
        {o.label}
      </option>
    ))}
  </select>
));

jest.mock("react-bootstrap", () => ({
  Alert: ({ children }) => <div data-testid="alert">{children}</div>,
}));

import { MetadataSectionEdit } from "../../../pages/Encounter/MetadataSectionEdit";

const makeStore = (overrides = {}) => ({
  encounterData: {
    id: "E-123",
    createdAt: "2025-10-30T12:00:00Z",
    version: "2025-10-30T12:30:00Z",
    importTaskId: "task-999",
  },
  siteSettingsData: {
    users: [{ username: "alice" }, { username: "bob" }, { username: "" }],
  },
  getFieldValue: jest.fn(() => "alice"),
  setFieldValue: jest.fn(),
  errors: {
    hasSectionError: jest.fn(() => false),
    getSectionErrors: jest.fn(() => []),
  },
  ...overrides,
});

describe("MetadataSectionEdit", () => {
  test("renders basic metadata fields", () => {
    const store = makeStore();
    render(<MetadataSectionEdit store={store} />);

    expect(screen.getByText("ENCOUNTER_ID")).toBeInTheDocument();
    expect(screen.getByText("DATE_CREATED")).toBeInTheDocument();
    expect(screen.getByText("LAST_EDIT")).toBeInTheDocument();
    expect(screen.getByText("IMPORTED_VIA")).toBeInTheDocument();

    expect(screen.getByText(/E-123/)).toBeInTheDocument();
    expect(screen.getByText(/2025-10-30T12:00:00Z/)).toBeInTheDocument();
  });

  test("renders LAST_EDIT with toLocaleString when version exists", () => {
    const store = makeStore({
      encounterData: {
        id: "E-123",
        createdAt: "2025-10-30T12:00:00Z",
        version: "2025-10-30T12:30:00Z",
      },
    });
    render(<MetadataSectionEdit store={store} />);

    expect(screen.queryByText("None")).not.toBeInTheDocument();
  });

  test("renders LAST_EDIT as 'None' when no version", () => {
    const store = makeStore({
      encounterData: {
        id: "E-123",
        createdAt: "2025-10-30T12:00:00Z",
        version: null,
      },
    });
    render(<MetadataSectionEdit store={store} />);

    expect(screen.getByText(/None/)).toBeInTheDocument();
  });

  test("renders importTaskId as link when present", () => {
    const store = makeStore();
    render(<MetadataSectionEdit store={store} />);

    const link = screen.getByText("task-999");
    expect(link).toBeInTheDocument();
    expect(link.tagName.toLowerCase()).toBe("a");
    expect(link).toHaveAttribute("href", "/react/bulk-import-task?id=task-999");
  });

  test("does not render link when no importTaskId", () => {
    const store = makeStore({
      encounterData: {
        id: "E-123",
        createdAt: "2025-10-30T12:00:00Z",
        version: "2025-10-30T12:30:00Z",
        importTaskId: null,
      },
    });
    render(<MetadataSectionEdit store={store} />);

    expect(screen.queryByText("task-999")).not.toBeInTheDocument();
  });

  test("SelectInput options come from store.siteSettingsData.users", () => {
    const store = makeStore();
    render(<MetadataSectionEdit store={store} />);

    const select = screen.getByTestId("assigned-user-select");
    expect(select).toHaveTextContent("alice");
    expect(select).toHaveTextContent("bob");
    expect(select).not.toHaveTextContent("");
  });

  test("changing assigned user calls store.setFieldValue", () => {
    const store = makeStore({
      getFieldValue: jest.fn(() => ""),
    });
    render(<MetadataSectionEdit store={store} />);

    const select = screen.getByTestId("assigned-user-select");
    fireEvent.change(select, { target: { value: "bob" } });

    expect(store.setFieldValue).toHaveBeenCalledWith(
      "metadata",
      "submitterID",
      "bob",
    );
  });

  test("shows alert when metadata section has error", () => {
    const store = makeStore({
      errors: {
        hasSectionError: jest.fn(() => true),
        getSectionErrors: jest.fn(() => ["ERR1", "ERR2"]),
      },
    });
    render(<MetadataSectionEdit store={store} />);

    expect(screen.getByTestId("alert")).toBeInTheDocument();
    expect(screen.getByText("ERR1;ERR2")).toBeInTheDocument();
  });
});
