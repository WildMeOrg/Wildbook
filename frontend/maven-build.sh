#!/bin/sh

# Skip frontend build if running in Docker (Docker builds it separately)
if [ "$SKIP_FRONTEND_BUILD" = "true" ]; then
  echo "Skipping frontend build (SKIP_FRONTEND_BUILD=true)"
  exit 0
fi

# it seems like we dont need a full/absolute url here
#export PUBLIC_URL=https://example.com/react/
export PUBLIC_URL=/react/
export SITE_NAME="Wildbook"

npm install react-app-rewired

cd frontend

# Use `npm ci` for consistent and faster installs in production, as it installs exact versions
# from `package-lock.json` without modifying it, ensuring stability across environments.
npm ci

npm run jest

npm run build

rsync -a build/ ../src/main/webapp/react

# hacky cleanup of changes left over
cd ..
git checkout -- package.json package-lock.json
