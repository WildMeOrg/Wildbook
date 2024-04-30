// const webpack = require("webpack");
const { resolve } = require('path');
const path = require('path');
const TerserPlugin = require('terser-webpack-plugin');

const rootDir = resolve(__dirname, '../../');
const dist = path.resolve(rootDir, 'dist');

module.exports = {
  mode: 'production',
  devtool: 'source-map',
  entry: {
    main: path.resolve(rootDir, 'src/index.jsx'),
  },
  output: {
    path: dist,
    filename: '[name].js',
    chunkFilename: '[name].chunk.js',
    publicPath: '/',
  },
  optimization: {
      minimizer: [new TerserPlugin()],
  },
};
