const {
  override,
  addWebpackPlugin,
  fixBabelImports,
} = require("customize-cra");
// const CompressionPlugin = require("compression-webpack-plugin");
const TerserPlugin = require("terser-webpack-plugin");
const webpack = require("webpack");
// const BundleAnalyzerPlugin = require("webpack-bundle-analyzer").BundleAnalyzerPlugin;

module.exports = override(
  fixBabelImports("import", {
    libraryName: "antd",
    libraryDirectory: "es",
    style: "css",
  }),

  (config) => {
    const env = process.env.NODE_ENV;
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
              },
            },
          }),
        ],
      };

      // save for future use, if needed to compress the assets

      // config.plugins.push(
      //   new CompressionPlugin({
      //     filename: "[path][base].gz",
      //     algorithm: "gzip",
      //     test: /\.(js|css|html|svg)$/,
      //     threshold: 10240,
      //     minRatio: 0.8,
      //     deleteOriginalAssets: false,
      //   })
      // );
    }

    config.stats = {
      all: true,
      timings: true,
      assets: true,
      chunks: true,
      modules: true,
    };

    return config;
  },

  addWebpackPlugin(
    new webpack.DefinePlugin({
      "process.env.SITE_NAME": JSON.stringify(process.env.SITE_NAME),
    }),
  ),

  // Uncomment the following line to enable the bundle analyzer, for debugging purposes
  // addWebpackPlugin(new BundleAnalyzerPlugin()),
);
