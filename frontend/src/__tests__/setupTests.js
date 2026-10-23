import "@testing-library/jest-dom";

// jsdom does not ship TextEncoder/TextDecoder; polyfill from Node's util module.
const { TextEncoder, TextDecoder } = require("util");
if (typeof global.TextEncoder === "undefined") global.TextEncoder = TextEncoder;
if (typeof global.TextDecoder === "undefined") global.TextDecoder = TextDecoder;

jest.mock("react-intl", () => {
  const actual = jest.requireActual("react-intl");
  return {
    ...actual,
    useIntl: () => {
      const messages = require("../locale/en.json");
      return {
        // Substitute simple {placeholder} arguments. Dropping `values` made any message
        // with an argument render its raw ICU pattern, so tests could not assert the copy
        // a user actually sees (e.g. "PDF not available for locale de.").
        formatMessage: ({ id, defaultMessage }, values) => {
          const message = messages[id] ?? defaultMessage ?? id;
          if (!values) return message;
          return String(message).replace(/\{(\w+)\}/g, (match, key) =>
            key in values ? String(values[key]) : match,
          );
        },
      };
    },
    FormattedMessage: jest.fn().mockImplementation(({ id }) => id),
  };
});

test("dummy test", () => {
  expect(true).toBe(true);
});

process.env.PUBLIC_URL = "";
process.env.SITE_NAME = "Test Site";