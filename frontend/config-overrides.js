// config-overrides.js
const webpack = require("webpack");
module.exports = function override(config, env) {
  if (env === "production") {
    config.devtool = "source-map";
    // config.devtool = 'cheap-module-source-map';
  }
  config.plugins.push(
    new webpack.DefinePlugin({
      "process.env.SITE_NAME": JSON.stringify(process.env.SITE_NAME),
    }),
  );

  // TEMP: allow dynamic import for external ESM URL (procaptcha prosopo bundle)
  // Follow-up: remove URL import from app code and load via script instead.
  config.output = config.output || {};
  config.output.environment = {
    ...(config.output.environment || {}),
    dynamicImport: true,
  };

  return config;
};
