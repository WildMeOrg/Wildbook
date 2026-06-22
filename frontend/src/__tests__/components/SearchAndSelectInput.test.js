/* eslint-disable react/display-name */
import React from "react";
import { render, screen, act } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import SearchAndSelectInput from "../../components/generalInputs/SearchAndSelectInput";

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

        <button
          type="button"
          data-testid="btn-select-x"
          onClick={() =>
            props.onChange && props.onChange({ value: "x", label: "X" })
          }
        >
          select-x
        </button>

        <button
          type="button"
          data-testid="btn-clear"
          onClick={() => props.onChange && props.onChange(null)}
        >
          clear
        </button>

        <button
          type="button"
          data-testid="btn-create-newone"
          onClick={() => props.onCreateOption && props.onCreateOption("NewOne")}
        >
          create
        </button>
      </div>
    );
  };
});

// Works with both real and fake timers (does not rely on setTimeout)
const flushPromises = async () => {
  await act(async () => {});
};

describe("SearchAndSelectInput (basic)", () => {
  let user;

  beforeEach(() => {
    latestCreatableProps = undefined;
    jest.useRealTimers();
    user = userEvent.setup();
  });

  test("renders label and passes options to creatable select", () => {
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

  test("supports string options via normalizeOptions", () => {
    render(
      <SearchAndSelectInput
        label="L"
        value="b"
        onChange={jest.fn()}
        options={["a", "b"]}
      />,
    );

    expect(latestCreatableProps.options).toEqual([
      { value: "a", label: "a" },
      { value: "b", label: "b" },
    ]);
    expect(latestCreatableProps.value).toEqual({ value: "b", label: "b" });
  });

  test("stringifies option.value in normalizeOptions", () => {
    render(
      <SearchAndSelectInput
        label="L"
        value={1}
        onChange={jest.fn()}
        options={[{ value: 1, label: "One" }]}
      />,
    );

    expect(latestCreatableProps.options).toEqual([
      { value: "1", label: "One" },
    ]);
    expect(latestCreatableProps.value).toEqual({ value: "1", label: "One" });
  });

  test("shows value even when it is not in options", () => {
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
    expect(screen.getByTestId("creatable-value")).toHaveTextContent(
      "not-exist",
    );
  });

  test("value null/undefined results in null selectedOption", () => {
    const { rerender } = render(
      <SearchAndSelectInput
        label="L"
        value={null}
        onChange={jest.fn()}
        options={[{ value: "a", label: "A" }]}
      />,
    );

    expect(latestCreatableProps.value).toBe(null);

    rerender(
      <SearchAndSelectInput
        label="L"
        value={undefined}
        onChange={jest.fn()}
        options={[{ value: "a", label: "A" }]}
      />,
    );

    expect(latestCreatableProps.value).toBe(null);
  });

  test("selecting an existing option calls onChange with the option value", async () => {
    const handleChange = jest.fn();

    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={handleChange}
        options={[{ value: "x", label: "X" }]}
      />,
    );

    await user.click(screen.getByTestId("btn-select-x"));
    expect(handleChange).toHaveBeenCalledWith("x");
  });

  test("clearing selection calls onChange with empty string", async () => {
    const handleChange = jest.fn();
    render(
      <SearchAndSelectInput
        label="L"
        value="x"
        onChange={handleChange}
        options={[{ value: "x", label: "X" }]}
      />,
    );

    await user.click(screen.getByTestId("btn-clear"));
    expect(handleChange).toHaveBeenCalledWith("");
  });

  test("creating a new option calls onChange with the created value and prepends it into options", async () => {
    const handleChange = jest.fn();

    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={handleChange}
        options={[]}
      />,
    );

    await user.click(screen.getByTestId("btn-create-newone"));

    expect(handleChange).toHaveBeenCalledWith("NewOne");
    expect(latestCreatableProps.options[0]).toEqual({
      value: "NewOne",
      label: "NewOne",
    });
  });

  test("maps keepMenuOpenOnSelect to closeMenuOnSelect", () => {
    const { rerender } = render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={jest.fn()}
        options={[]}
      />,
    );

    expect(latestCreatableProps.closeMenuOnSelect).toBe(true);

    rerender(
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

  test("menuIsOpen depends on input length/minChars (non-async)", async () => {
    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={jest.fn()}
        options={[]}
        minChars={3}
      />,
    );

    expect(latestCreatableProps.menuIsOpen).toBe(false);

    await user.type(screen.getByTestId("creatable-input"), "ab");
    expect(latestCreatableProps.menuIsOpen).toBe(false);

    await user.type(screen.getByTestId("creatable-input"), "c");
    expect(latestCreatableProps.menuIsOpen).toBe(true);
  });

  test("size controls minHeight via styles.control", () => {
    const { rerender } = render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={jest.fn()}
        options={[]}
      />,
    );

    let control = latestCreatableProps.styles.control({}, { isFocused: false });
    expect(control.minHeight).toBe(38);

    rerender(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={jest.fn()}
        options={[]}
        size="sm"
      />,
    );
    control = latestCreatableProps.styles.control({}, { isFocused: false });
    expect(control.minHeight).toBe(31);

    rerender(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={jest.fn()}
        options={[]}
        size="lg"
      />,
    );
    control = latestCreatableProps.styles.control({}, { isFocused: false });
    expect(control.minHeight).toBe(49);
  });

  test("styles.control focused branch changes boxShadow/borderColor", () => {
    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={jest.fn()}
        options={[]}
      />,
    );

    const base = { boxShadow: "none", borderColor: "gray" };
    const focused = latestCreatableProps.styles.control(base, {
      isFocused: true,
    });

    expect(focused.borderColor).toBe("#86b7fe");
    expect(focused.boxShadow).toContain("rgba(13,110,253");
  });

  test("styles.menuPortal sets zIndex", () => {
    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={jest.fn()}
        options={[]}
      />,
    );

    const base = { zIndex: 1 };
    const out = latestCreatableProps.styles.menuPortal(base);
    expect(out.zIndex).toBe(9999);
  });
});

describe("SearchAndSelectInput (async loadOptions)", () => {
  let user;

  beforeEach(() => {
    latestCreatableProps = undefined;
    jest.useFakeTimers();
    user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  test("does not call loadOptions when input is shorter than minChars", async () => {
    const loadOptions = jest.fn();

    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={jest.fn()}
        options={[]}
        loadOptions={loadOptions}
        debounceMs={300}
        minChars={3}
      />,
    );

    await user.type(screen.getByTestId("creatable-input"), "ab");

    act(() => {
      jest.advanceTimersByTime(300);
    });

    expect(loadOptions).not.toHaveBeenCalled();

    await flushPromises();
    expect(latestCreatableProps.isLoading).toBe(false);
  });

  test("debounced async loadOptions populates async options", async () => {
    const loadOptions = jest
      .fn()
      .mockResolvedValueOnce([{ value: "aa", label: "AA" }, "bb"]);

    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={jest.fn()}
        options={[]}
        loadOptions={loadOptions}
        debounceMs={200}
        minChars={2}
      />,
    );

    await user.type(screen.getByTestId("creatable-input"), "ab");

    act(() => {
      jest.advanceTimersByTime(200);
    });

    await flushPromises();

    expect(loadOptions).toHaveBeenCalledWith("ab");
    expect(latestCreatableProps.options).toEqual([
      { value: "aa", label: "AA" },
      { value: "bb", label: "bb" },
    ]);
    expect(latestCreatableProps.isLoading).toBe(false);
  });

  test("dedupes merged options by value (static + async)", async () => {
    const loadOptions = jest.fn().mockResolvedValueOnce([
      { value: "a", label: "A-from-async" },
      { value: "c", label: "C" },
    ]);

    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={jest.fn()}
        options={[
          { value: "a", label: "A-from-static" },
          { value: "b", label: "B" },
        ]}
        loadOptions={loadOptions}
        debounceMs={200}
        minChars={1}
      />,
    );

    await user.type(screen.getByTestId("creatable-input"), "q");

    act(() => {
      jest.advanceTimersByTime(200);
    });
    await flushPromises();

    const values = latestCreatableProps.options.map((o) => o.value);
    expect(values).toEqual(expect.arrayContaining(["a", "b", "c"]));
    expect(values.filter((v) => v === "a")).toHaveLength(1);
  });

  test("onSearchError is called and async options cleared when loadOptions rejects", async () => {
    const err = new Error("boom");
    const loadOptions = jest.fn().mockRejectedValueOnce(err);
    const onSearchError = jest.fn();

    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={jest.fn()}
        options={[{ value: "s", label: "S" }]}
        loadOptions={loadOptions}
        onSearchError={onSearchError}
        debounceMs={100}
        minChars={1}
      />,
    );

    await user.type(screen.getByTestId("creatable-input"), "x");

    act(() => {
      jest.advanceTimersByTime(100);
    });
    await flushPromises();

    expect(onSearchError).toHaveBeenCalledWith(err);
    expect(latestCreatableProps.options).toEqual([{ value: "s", label: "S" }]);
    expect(latestCreatableProps.isLoading).toBe(false);
  });

  test("race: older request result must not override newer request", async () => {
    let resolve1;
    let resolve2;

    const p1 = new Promise((res) => {
      resolve1 = res;
    });
    const p2 = new Promise((res) => {
      resolve2 = res;
    });

    const loadOptions = jest
      .fn()
      .mockImplementationOnce(() => p1)
      .mockImplementationOnce(() => p2);

    render(
      <SearchAndSelectInput
        label="L"
        value=""
        onChange={jest.fn()}
        options={[]}
        loadOptions={loadOptions}
        debounceMs={50}
        minChars={1}
      />,
    );

    await user.type(screen.getByTestId("creatable-input"), "a");
    act(() => {
      jest.advanceTimersByTime(50);
    });
    expect(loadOptions).toHaveBeenCalledWith("a");

    await user.clear(screen.getByTestId("creatable-input"));
    await user.type(screen.getByTestId("creatable-input"), "ab");
    act(() => {
      jest.advanceTimersByTime(50);
    });
    expect(loadOptions).toHaveBeenCalledWith("ab");

    resolve2([{ value: "new", label: "NEW" }]);
    await flushPromises();
    expect(latestCreatableProps.options).toEqual([
      { value: "new", label: "NEW" },
    ]);

    resolve1([{ value: "old", label: "OLD" }]);
    await flushPromises();
    expect(latestCreatableProps.options).toEqual([
      { value: "new", label: "NEW" },
    ]);
  });
});
