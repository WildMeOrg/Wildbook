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
  // 按需加载 antd 组件库
  fixBabelImports("import", {
    libraryName: "antd",
    libraryDirectory: "es",
    style: "css",
  }),

  // 自定义配置生产模式
  (config, env) => {
    if (env === "production") {
      // 开启 Source Map
      config.devtool = "source-map";

      // 添加 TerserPlugin 压缩未使用代码
      config.optimization = {
        ...config.optimization,
        minimize: true,
        minimizer: [
          new TerserPlugin({
            terserOptions: {
              compress: {
                drop_console: true, // 移除 console.log
                drop_debugger: true, // 移除 debugger
                unused: true, // 移除未使用代码 (Tree Shaking)
              },
              output: {
                comments: false, // 移除注释
              },
            },
            extractComments: false, // 不生成额外的 LICENSE 文件
          }),
        ],
      };

      // 添加 IgnorePlugin 移除 Moment.js 的语言包
      config.plugins.push(
        new webpack.IgnorePlugin({
          resourceRegExp: /^\.\/locale$/, // 忽略所有 locale 文件
          contextRegExp: /moment$/, // 只针对 moment 的 context
        }),
      );
    }
    return config;
  },

  // 定义全局环境变量
  addWebpackPlugin(
    new webpack.DefinePlugin({
      "process.env.SITE_NAME": JSON.stringify(process.env.SITE_NAME),
    }),
  ),

  // 添加 Bundle Analyzer 插件
  addWebpackPlugin(new BundleAnalyzerPlugin()),
);
