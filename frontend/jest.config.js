module.exports = {
  testMatch: [
    "**/__tests__/**/*.[jt]s?(x)", // Looks inside __tests__ folders
    "**/?(*.)+(spec|test).[tj]s?(x)", // Looks for .spec.js/.test.js files
  ],
  transform: {
    "^.+\\.[t|j]sx?$": "babel-jest",
  },
  transformIgnorePatterns: ["/node_modules/(?!(axios)).+\\.js$"],
  testEnvironment: "jsdom", // Set the environment for testing React components
  moduleNameMapper: {
    "^axios$": require.resolve("axios"),
  },
};
