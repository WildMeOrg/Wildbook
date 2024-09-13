const dotenv = require('dotenv');
const fs = require('fs');
const os = require('os');
const path = require('path');

console.log('Deploying React build to development environment');

// Function to resolve "~" to the home directory
function resolveHome(filepath) {
  if (filepath[0] === '~') {
    return path.join(os.homedir(), filepath.slice(1));
  }
  return filepath;
}

// Load environment variables from .env file
const envPath = path.resolve(__dirname, path.join('..', '..', 'devops', 'development', '.env'));
if (!fs.existsSync(envPath)) {
  console.error(`Deploy failed: ${envPath} not found`);
  process.exit(1);
}
dotenv.config({ path: path.resolve(__dirname, envPath) });

// Ensure WILDBOOK_BASE_DIR is set
const localDeployRootPath = resolveHome(process.env.WILDBOOK_BASE_DIR);
if (!localDeployRootPath) {
  console.error(`Deploy failed: $WILDBOOK_BASE_DIR is not set. Set it in ${envPath}`);
  process.exit(1);
}

// Ensure the build directory exists
if (!fs.existsSync('build')) {
  console.error("Deploy failed: 'build' directory not found. Was the build successful?");
  process.exit(1);
}

const localDeployReactPath = path.join(localDeployRootPath, 'webapps', 'wildbook', 'react');
console.log(`Deploying to ${localDeployReactPath}`);

// If the react directory exists, remove its contents
if (!fs.existsSync(localDeployReactPath)) {
  console.log(`React deploy directory doesn't exist; creating it`);
  fs.mkdirSync(localDeployReactPath, { recursive: true });
}

// Copy build directory to the deploy path
const srcPath = 'build';
console.log(`Copying ${srcPath} to ${localDeployReactPath}`);
fs.cpSync(srcPath, localDeployReactPath, { recursive: true });

console.log(`Successfully deployed React build to ${localDeployReactPath}`);
