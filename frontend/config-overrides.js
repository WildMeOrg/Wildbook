// config-overrides.js
module.exports = function override(config, env) {
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
  