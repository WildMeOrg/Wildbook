// // config-overrides.js
// const webpack = require("webpack");
// module.exports = function override(config, env) {
//   if (env === "production") {
//     config.devtool = "source-map";
//     // config.devtool = 'cheap-module-source-map';
//   }
//   config.plugins.push(
//     new webpack.DefinePlugin({
//       "process.env.SITE_NAME": JSON.stringify(process.env.SITE_NAME),
//     }),
//   );
//   return config;
// };

const {
  override,
  addWebpackPlugin,
  fixBabelImports,
} = require("customize-cra");
const webpack = require("webpack");
const BundleAnalyzerPlugin =
  require("webpack-bundle-analyzer").BundleAnalyzerPlugin;
const TerserPlugin = require("terser-webpack-plugin");

module.exports = override(
  fixBabelImports("import", {
    libraryName: "antd",
    libraryDirectory: "es",
    style: "css",
  }),

  (config, env) => {
    if (env === "production") {
      config.devtool = "source-map";

      config.optimization = {
        ...config.optimization,
        minimize: true,
        minimizer: [
          new TerserPlugin({
            terserOptions: {
              compress: {
                drop_console: true,
                drop_debugger: true,
                unused: true,
              },
              output: {},
            },
            extractComments: false,
          }),
        ],
      };

      config.plugins.push(
        new webpack.IgnorePlugin({
          resourceRegExp: /^\.\/locale$/,
          contextRegExp: /moment$/,
        }),
      );
    }
    return config;
  },

  addWebpackPlugin(
    new webpack.DefinePlugin({
      "process.env.SITE_NAME": JSON.stringify(process.env.SITE_NAME),
    }),
  ),

  addWebpackPlugin(new BundleAnalyzerPlugin()),
);
