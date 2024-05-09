#!/usr/bin/env bash

set -ex

# See https://stackoverflow.com/a/246128/176882
export ROOT_LOC="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# Change to the script's root directory location
cd ${ROOT_LOC}

# Build the images in dependence order
docker build -t wildme/nginx:latest nginx
docker build -t wildme/wildbook:latest wildbook
