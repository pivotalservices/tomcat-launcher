#!/bin/bash

PIPELINE_NAME=${1:-cache-gradle}
ALIAS=${2:-docker}
CREDENTIALS=${3:-credentials.yml}

echo y | fly -t "${ALIAS}" sp -p "${PIPELINE_NAME}" -c cache-gradle-pipeline.yml -l "${CREDENTIALS}"
