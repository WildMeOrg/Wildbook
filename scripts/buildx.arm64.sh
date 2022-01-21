#!/usr/bin/env bash

set -ex

export DOCKER_BUILDKIT=1

export DOCKER_CLI_EXPERIMENTAL=enabled

# docker buildx create --name multi-arch-builder --use

docker buildx build \
    --target org.wildme.wildbook.base \
    -t wildme/wildbook-base:arm64 \
    --cache-to=type=local,dest=.buildx.cache,mode=max \
    --cache-from=type=local,src=.buildx.cache,mode=max \
    --compress \
    --platform linux/arm64 \
    --load \
    .

docker buildx build \
    --target org.wildme.wildbook.build \
    -t wildme/wildbook-build:arm64 \
    --no-cache \
    --cache-to=type=local,dest=.buildx.cache,mode=max \
    --cache-from=type=local,src=.buildx.cache,mode=max \
    --compress \
    --platform linux/arm64 \
    --load \
    .

docker buildx build \
    --target org.wildme.wildbook.install \
    -t wildme/wildbook-install:arm64 \
    --no-cache \
    --cache-to=type=local,dest=.buildx.cache,mode=max \
    --cache-from=type=local,src=.buildx.cache,mode=max \
    --compress \
    --platform linux/arm64 \
    --load \
    .

docker buildx build \
    --target org.wildme.wildbook.deploy \
    -t wildme/wildbook:arm64 \
    -t wildme/edm:arm64 \
    --no-cache \
    --cache-to=type=local,dest=.buildx.cache,mode=max \
    --cache-from=type=local,src=.buildx.cache,mode=max \
    --compress \
    --platform linux/arm64 \
    --load \
    .
