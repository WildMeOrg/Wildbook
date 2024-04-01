// config-overrides.js
const webpack = require('webpack');
module.exports = function override(config, env) {
  console.log(' config', config );
  console.log(' env', env );  
  if(env === 'production'){
    config.devtool = 'source-map';
    console.log('source-map');
    // config.devtool = 'cheap-module-source-map';
  }

    config.plugins.push(
      new webpack.DefinePlugin({
        'process.env.SITE_NAME': JSON.stringify(process.env.SITE_NAME),
      })
    );
    return config;
  };
  
  // const devConfig = require('./config/webpack/webpack.dev.js');
  // const prodConfig = require('./config/webpack/webpack.prod.js');
  
  // module.exports = function override(config, env) {
  //   const envConfig = env === 'development' ? devConfig : prodConfig;
  //   console.log('envConfig', envConfig);
  //   console.log('env',env);
  //   return { ...config, ...envConfig };
  // };
