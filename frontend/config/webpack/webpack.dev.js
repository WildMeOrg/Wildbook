const path = require('path');
const webpack = require('webpack');
const UnusedWebpackPlugin = require('unused-webpack-plugin');

module.exports = {
  mode: 'development',
  devtool: 'source-map',
  devServer: {
    disableHostCheck: true,
    headers: {
      'Access-Control-Allow-Origin': '*',
    },
    historyApiFallback: true,
    hot: true,
    port: 3000,
    writeToDisk: true,
  },
  plugins: [
    new webpack.HotModuleReplacementPlugin(),
    new UnusedWebpackPlugin({
      directories: [path.join(__dirname, '../../src')],
    }),
  ],
};
