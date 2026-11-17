#!/usr/bin/env bash
# Builds the five images and pushes them to the ECR repositories created by infra/aws/ecs-managed.
# Usage: scripts/push-ecr.sh <aws-region> [tag]     (needs: aws cli logged in, docker)
set -euo pipefail
cd "$(dirname "$0")/.."
REGION="${1:?usage: push-ecr.sh <region> [tag]}"; TAG="${2:-latest}"
ACCOUNT="$(aws sts get-caller-identity --query Account --output text)"
REGISTRY="$ACCOUNT.dkr.ecr.$REGION.amazonaws.com"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"
for svc in core-lms-service assessment-service worker-service api-gateway; do
  EXTRA=""; [[ "$svc" == "worker-service" ]] && EXTRA="--build-arg INSTALL_FFMPEG=true"   # video transcoding
  docker build -f Dockerfile --build-arg MODULE="$svc" $EXTRA -t "$REGISTRY/oep/$svc:$TAG" .
  docker push "$REGISTRY/oep/$svc:$TAG"
done
docker build -t "$REGISTRY/oep/frontend:$TAG" --build-arg VITE_API_BASE_URL= ../frontend
docker push "$REGISTRY/oep/frontend:$TAG"
echo "Pushed 5 images with tag $TAG. Now: aws ecs update-service --cluster oep-<env> --service <name> --force-new-deployment"
