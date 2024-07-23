// config-overrides.js
const webpack = require('webpack');
module.exports = function override(config, env) { 
  if(env === 'production'){
    config.devtool = 'source-map';
    // config.devtool = 'cheap-module-source-map';
  }
    config.plugins.push(
      new webpack.DefinePlugin({
        'process.env.SITE_NAME': JSON.stringify(process.env.SITE_NAME),
      })
    );
    return config;
  };
