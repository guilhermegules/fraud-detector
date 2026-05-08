#!/bin/bash
set -e

IMAGE="guilhermegules/fraud-detector-rinha"
TAG=$(date +%Y%m%d%H%M)

docker build \
    -t "$IMAGE:$TAG" \
    -t "$IMAGE:latest" \
    -t "fraud-detector-rinha:latest" \
    -f Dockerfile ..

docker push --all-tags $IMAGE

echo "Published $IMAGE:$TAG"