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
        formatMessage: ({ id }) => messages[id] || id,
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