#!/bin/sh

#export PUBLIC_URL=https://example.com/react/
export PUBLIC_URL=http://localhost:81/react/
export SITE_NAME="Test Site Name"

npm install react-app-rewired

cd frontend
npm run build

rsync -a build/ ../src/main/webapp/react



# hacky cleanup of changes left over
cd ..
git checkout -- package.json package-lock.json

