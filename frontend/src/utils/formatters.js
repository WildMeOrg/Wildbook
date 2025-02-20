import { format } from "date-fns";

export const formatDate = (input, fancy = false, fallback = "") => {
  const formatter = fancy ? "PP" : "yyyy-MM-dd HH:mm";
  try {
    const jsDate = typeof input === "string" ? new Date(input) : input;
    const formattedDate = format(jsDate, formatter);
    return formattedDate;
  } catch (_error) {
    // console.error(error);
    return fallback;
  }
};
