/* eslint-disable react/display-name */
import React from "react";
import { render, screen, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id }) => <span>{id}</span>,
}));

let latestCreatableProps;
jest.mock("react-select/creatable", () => {
  const React = require("react");
  return (props) => {
    latestCreatableProps = props;
    return (
      <div data-testid="creatable-mock">
        <input
          data-testid="creatable-input"
          value={props.inputValue || ""}
          onChange={(e) =>
            props.onInputChange &&
            props.onInputChange(e.target.value, { action: "input-change" })
          }
        />
        <span data-testid="creatable-value">
          {props.value ? props.value.label : ""}
        </span>
      </div>
    );
  };
});

import SearchAndSelectInput from "../../../src/components/SearchAndSelectInput";

const flushPromises = () => new Promise((res) => setImmediate(res));

describe("SearchAndSelectInput", () => {
  beforeEach(() => {
    latestCreatableProps = undefined;
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
  });

  test("renders label and static options", () => {
    render(
      <SearchAndSelectInput
        label="MY_LABEL"
        value=""
        onChange={jest.fn()}
        options={[
          { value: "a", label: "A" },
          { value: "b", label: "B" },
        ]}
      />,
    );

    expect(screen.getByText("MY_LABEL")).toBeInTheDocument();
    expect(screen.getByTestId("creatable-mock")).toBeInTheDocument();
    expect(latestCreatableProps.options).toEqual([
      { value: "a", label: "A" },
      { value: "b", label: "B" },
    ]);
  });

  test("when value is not in options, it still shows it", () => {
    render(
      <SearchAndSelectInput
        label="X"
        value="not-exist"
        onChange={jest.fn()}
        options={[{ value: "a", label: "A" }]}
      />,
    );

    expect(latestCreatableProps.value).toEqual({
      value: "not-exist",
      label: "not-exist",
    });
  });

  test("select existing option triggers onChange with its value", async () => {
    const handleChange = jest.fn();

    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={handleChange}
        options={[{ value: "x", label: "X" }]}
      />,
    );

    act(() => {
      latestCreatableProps.onChange({ value: "x", label: "X" });
    });

    expect(handleChange).toHaveBeenCalledWith("x");
  });

  test("creating new option adds it and calls onChange", async () => {
    const handleChange = jest.fn();

    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={handleChange}
        options={[]}
      />,
    );

    act(() => {
      latestCreatableProps.onCreateOption("NewOne");
    });

    expect(handleChange).toHaveBeenCalledWith("NewOne");
  });

  test("debounced async loadOptions is called after typing enough chars", async () => {
    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
    const loadOptions = jest
      .fn()
      .mockResolvedValue([{ value: "r1", label: "Result 1" }]);

    render(
      <SearchAndSelectInput
        label="SEARCH"
        value=""
        onChange={jest.fn()}
        loadOptions={loadOptions}
        minChars={2}
        debounceMs={300}
      />,
    );

    const input = screen.getByTestId("creatable-input");
    await user.type(input, "a");
    act(() => {
      jest.advanceTimersByTime(350);
    });
    await flushPromises();
    expect(loadOptions).not.toHaveBeenCalled();

    await user.type(input, "b");
    act(() => {
      jest.advanceTimersByTime(350);
    });
    await flushPromises();

    expect(loadOptions).toHaveBeenCalledWith("ab");
    expect(latestCreatableProps.options).toEqual([
      { value: "r1", label: "Result 1" },
    ]);
  });

  test("onSearchError is called when loadOptions throws", async () => {
    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
    const loadOptions = jest.fn().mockRejectedValue(new Error("boom"));
    const onSearchError = jest.fn();

    render(
      <SearchAndSelectInput
        label="SEARCH"
        value=""
        onChange={jest.fn()}
        loadOptions={loadOptions}
        onSearchError={onSearchError}
      />,
    );

    const input = screen.getByTestId("creatable-input");
    await user.type(input, "abc");

    act(() => {
      jest.advanceTimersByTime(300);
    });
    await flushPromises();

    expect(loadOptions).toHaveBeenCalledWith("abc");
    expect(onSearchError).toHaveBeenCalled();
  });

  test("clears inputValue after selecting option", () => {
    const handleChange = jest.fn();
    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={handleChange}
        options={[{ value: "x", label: "X" }]}
      />,
    );

    act(() => {
      latestCreatableProps.onInputChange("abc", { action: "input-change" });
    });
    expect(latestCreatableProps.inputValue).toBe("abc");

    act(() => {
      latestCreatableProps.onChange({ value: "x", label: "X" });
    });

    expect(latestCreatableProps.inputValue).toBe("");
  });

  test("respects keepMenuOpenOnSelect=false (default) by setting closeMenuOnSelect=true", () => {
    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={jest.fn()}
        options={[]}
      />,
    );
    expect(latestCreatableProps.closeMenuOnSelect).toBe(true);
  });

  test("respects keepMenuOpenOnSelect=true", () => {
    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={jest.fn()}
        options={[]}
        keepMenuOpenOnSelect={true}
      />,
    );
    expect(latestCreatableProps.closeMenuOnSelect).toBe(false);
  });
});
