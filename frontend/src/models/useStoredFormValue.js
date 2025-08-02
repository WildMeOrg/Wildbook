import { useState, useEffect } from "react";

export function useStoredFormValue(filterId, term, field) {
  const [storedValue, setStoredValue] = useState("");

  useEffect(() => {
    const formData = JSON.parse(sessionStorage.getItem("formData")) || [];
    const result = formData.find((item) => item.filterId === filterId);
    setStoredValue(result ? result.query[term]?.[field] || "" : "");
  }, [filterId, term, field]);

  return storedValue;
}
