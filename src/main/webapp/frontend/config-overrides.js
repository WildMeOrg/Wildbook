const { execSync } = require('child_process');
const { resolve } = require('path');
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CopyPlugin = require('copy-webpack-plugin');
const path = require('path');
const merge = require('webpack-merge');
const fs = require('fs');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const VersionFile = require('webpack-version-file');
const config = require('./config/webpack/config.json');
const prodConfig = require('./config/webpack/webpack.prod');
const devConfig = require('./config/webpack/webpack.dev');

const isDev =
  (process.env && process.env.NODE_ENV === 'development') ||
  process.env.env === 'dev';

const defaultWildbookUrl = isDev
  ? config.wildbook_dev_url
  : config.wildbook_prod_url;

const rootDir = resolve(__dirname, './');
const devdist = path.resolve(rootDir, 'devdist');

const extraConfig = isDev ? devConfig : prodConfig;

// no entry specified - only things that would be the same between each app
module.exports = env => {
  let wildbookUrl = defaultWildbookUrl;
  if (env && env.wildbook) wildbookUrl = env.wildbook;
  if (wildbookUrl === 'relative') wildbookUrl = '';

  const globals = {
    __DEV__: isDev && !process.env.LINKED_DEV,
    __config__: JSON.stringify(config),
    __wildbook_url__: JSON.stringify(wildbookUrl),
  };

  return merge(
    {
      target: 'web',
      entry: { main: resolve(rootDir, 'src', 'index.jsx') },
      output: {
        hashDigestLength: 8,
        filename: '[name].bundle.js',
        chunkFilename: '[name].[chunkhash].chunk.js',
        publicPath: '/',
        path: devdist,
      },
      module: {
        rules: [
          {
            test: /\.m?js/,
            resolve: {
              fullySpecified: false,
            },
          },
          {
            test: /\.(js|mjs|jsx|ts|tsx)$/,
            include: path.resolve(rootDir, 'src'),
            use: {
              loader: 'babel-loader',
              options: JSON.parse(
                fs.readFileSync(path.resolve(rootDir, '.babelrc')),
              ),
            },
          },
          {
            test: /\.css$/,
            exclude: /\.module\.css$/,
            sideEffects: true,
            use: [MiniCssExtractPlugin.loader, 'css-loader'],
          },
          {
            test: /\.(gif|jpg|jpeg|png|webm)$/,
            include: [resolve(rootDir, 'src')],
            use: {
              loader: 'file-loader',
              options: {},
            },
          },
          {
            test: /\.svg$/,
            use: ['file-loader', '@svgr/webpack'],
          },
          {
            test: /\.(woff|woff2|ttf|eot)$/,
            use: {
              loader: 'file-loader',
              options: {},
            },
          },
          {
            test: /\.pdf$/,
            use: {
              loader: 'file-loader',
              options: {},
            },
          },
        ],
      },
      resolve: {
        extensions: [
          '.wasm',
          '.mjs',
          '.js',
          '.jsx',
          '.json',
          '.ts',
          '.tsx',
        ],
      },
      optimization: {
        splitChunks: {
          chunks: 'all',
          cacheGroups: {
            vendor: {
              name: 'vendor',
              test: /[\\/]node_modules[\\/]/,
              chunks: 'initial',
              priority: -10,
            },
            icons: {
              name: 'icons',
              test: /[\\/]static\/icons[\\/]/,
            },
          },
        },
      },
      plugins: [
        new VersionFile({
          output: './src/constants/version.js',
          templateString:
            'export default {\n' +
            '  packageVersion: "<%= version %>",\n' +
            '  buildTimestamp: <%= buildDate.getTime() %>,\n' +
            '  commitHash: "<%= commitHash %>",\n' +
            '  scmVersion: "<%= scmVersion %>",\n' +
            '};\n',
          data: {
            commitHash: execSync('git rev-parse --short HEAD')
              .toString()
              .replace(/[\n ]/g, ''),
            // format is <tag>[-<commit-distance>-g<hash>[-dirty]] when there is a tag
            // or <hash>[-dirty] when there are no tags (note, --always is required for this case)
            scmVersion: execSync(
              'git describe --always --dirty --tags --long --match "*[0-9]*"',
            )
              .toString()
              .replace(/[\n ]/g, ''),
          },
        }),
        new webpack.ProvidePlugin({
          process: 'process/browser',
        }),
        new HtmlWebpackPlugin({
          templateContent: `
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="utf-8">
              </head>
              <body>
                <div id="root"></div>
              </body>
            </html>
          `,
          meta: {
            viewport:
              'width=device-width, initial-scale=1, user-scalable=no',
          },
          title: '',
          filename: 'index.html',
          minify: {
            useShortDoctype: true,
            keepClosingSlash: true,
            collapseWhitespace: true,
            preserveLineBreaks: true,
          },
        }),
        new webpack.DefinePlugin(globals),
        new MiniCssExtractPlugin({
          // Options similar to the same options in webpackOptions.output
          // both options are optional
          filename: '[name].css',
          chunkFilename: '[id].css',
        }),
        new CopyPlugin({
          patterns: [
            {
              from: resolve(rootDir, 'src/copy_on_build'),
            },
          ],
        }),
      ],
    },
    extraConfig,
  );
};
