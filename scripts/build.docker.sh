#!/bin/bash

set -ex

docker build --target org.wildme.wildbook.base --tag wildme/wildbook-base:next-gen .
docker build --no-cache --target org.wildme.wildbook.build --tag wildme/wildbook-build:next-gen .
docker build --target org.wildme.wildbook.install --tag wildme/wildbook-install:next-gen .
docker build --target org.wildme.wildbook.deploy --tag wildme/wildbook:next-gen .
docker tag wildme/wildbook:next-gen wildme/edm:latest
