import "@testing-library/jest-dom";

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
