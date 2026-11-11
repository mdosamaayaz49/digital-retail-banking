#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mvn -B -f backend/pom.xml verify
cd frontend
npm ci
npm test
npm run build
