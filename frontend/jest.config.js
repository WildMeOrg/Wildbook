module.exports = {
  testMatch: ["**/__tests__/**/*.[jt]s?(x)", "**/?(*.)+(spec|test).[tj]s?(x)"],
  transform: {
    "^.+\\.[t|j]sx?$": "babel-jest",
  },
  transformIgnorePatterns: ["/node_modules/(?!(axios)).+\\.js$"],
  testEnvironment: "jsdom",
  moduleNameMapper: {
    "^axios$": require.resolve("axios"),
    "\\.(css|less|scss|sass)$": "identity-obj-proxy",
    "\\.json$": "<rootDir>/__mocks__/jsonMock.js",
  },

  setupFilesAfterEnv: ["<rootDir>/src/__tests__/setupTests.js"],
};
