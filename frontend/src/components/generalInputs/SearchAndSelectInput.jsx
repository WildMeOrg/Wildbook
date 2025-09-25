import React, { useMemo, useRef, useState, useEffect, useCallback } from "react";
import Form from "react-bootstrap/Form";
import CreatableSelect from "react-select/creatable";

function normalizeOptions(options = []) {
  return options.map((opt) =>
    typeof opt === "string"
      ? { value: opt, label: opt }
      : { value: String(opt.value), label: opt.label }
  );
}

function useDebouncedCallback(fn, delay) {
  const timer = useRef(null);
  const fnRef = useRef(fn);
  useEffect(() => { fnRef.current = fn; }, [fn]);
  return useCallback((...args) => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => fnRef.current(...args), delay);
  }, [delay]);
}

export default function SearchAndSelectInput({
  label = "Select",
  value = "",
  onChange,
  options = [],
  placeholder = "Select or type...",
  size,
  className = "",
  isClearable = true,
  loadOptions,
  debounceMs = 300,
  minChars = 1,
  onSearchError,
  keepMenuOpenOnSelect = false,
  ...props
}) {
  const [asyncOptions, setAsyncOptions] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [inputValue, setInputValue] = useState("");

  const reqSeq = useRef(0);

  const normalizedStatic = useMemo(() => normalizeOptions(options), [options]);
  const normalizedAsync = useMemo(() => normalizeOptions(asyncOptions), [asyncOptions]);
  const mergedOptions = useMemo(() => {
    const map = new Map();
    [...normalizedStatic, ...normalizedAsync].forEach((o) => {
      if (!map.has(o.value)) map.set(o.value, o);
    });
    return Array.from(map.values());
  }, [normalizedStatic, normalizedAsync]);

  const normalizedValue = value == null ? "" : String(value);

  const selectedOption = useMemo(() => {
    const found = mergedOptions.find((o) => o.value === normalizedValue);
    if (found) return found;
    if (normalizedValue) return { value: normalizedValue, label: normalizedValue };
    return null;
  }, [mergedOptions, normalizedValue]);

  const handleChange = (opt) => {
    setInputValue("");
    onChange?.(opt ? opt.value : "");
  };

  const handleCreate = (label) => {
    const newOpt = { value: String(label), label: String(label) };
    setAsyncOptions((prev) => [newOpt, ...prev]);

    onChange?.(newOpt.value);
    setInputValue("");
  };

  const debouncedSearch = useDebouncedCallback(async (q) => {
    if (!loadOptions) return;
    const mySeq = ++reqSeq.current;
    if (!q || q.length < minChars) {
      setAsyncOptions([]);
      setIsLoading(false);
      return;
    }
    try {
      setIsLoading(true);
      const res = await loadOptions(q);
      if (mySeq === reqSeq.current) {
        setAsyncOptions(Array.isArray(res) ? res : []);
      }
    } catch (err) {
      if (mySeq === reqSeq.current) setAsyncOptions([]);
      onSearchError?.(err);
    } finally {
      if (mySeq === reqSeq.current) setIsLoading(false);
    }
  }, debounceMs);

  const handleInputChange = (val, { action }) => {
    if (action === "input-change" || action === "set-value") {
      setInputValue(val);
      if (loadOptions) {
        setIsLoading(true);
        debouncedSearch(val);
      }
    }
    return val;
  };

  const controlHeights = { sm: 31, md: 38, lg: 49 };
  const minHeight =
    size === "sm" ? controlHeights.sm : size === "lg" ? controlHeights.lg : controlHeights.md;

  return (
    <Form.Group className={className + " mt-2"} id={`group-${label}`}>
      {label && <Form.Label>{label}</Form.Label>}
      <CreatableSelect
        value={selectedOption}
        onChange={handleChange}
        onCreateOption={handleCreate}
        options={mergedOptions}
        placeholder={placeholder}
        isClearable={isClearable}
        isLoading={isLoading}
        onInputChange={handleInputChange}
        inputValue={inputValue}
        closeMenuOnSelect={!keepMenuOpenOnSelect}
        openMenuOnFocus
        openMenuOnClick
        menuIsOpen={Boolean(inputValue?.length >= minChars || isLoading)}
        filterOption={null}
        menuPortalTarget={typeof document !== "undefined" ? document.body : null}
        styles={{
          menuPortal: (base) => ({ ...base, zIndex: 9999 }),
          control: (base, state) => ({
            ...base,
            minHeight,
            boxShadow: state.isFocused
              ? "0 0 0 0.2rem rgba(13,110,253,.25)"
              : base.boxShadow,
            borderColor: state.isFocused ? "#86b7fe" : base.borderColor,
          }),
        }}
        classNamePrefix="react-select"
        {...props}
      />
    </Form.Group>
  );
}
