#!/bin/bash

export ROOT_FOLDER=$( pwd )
export REPO_RESOURCE=repo
export VERSION_RESOURCE=version
export OUTPUT_RESOURCE=out

echo "Root folder is [${ROOT_FOLDER}]"
echo "Repo resource folder is [${REPO_RESOURCE}]"
echo "Version resource folder is [${VERSION_RESOURCE}]"

source ${ROOT_FOLDER}/${REPO_RESOURCE}/ci/tasks/pipeline.sh

echo "Testing the built application on test environment"
cd ${ROOT_FOLDER}/${REPO_RESOURCE}

prepareForIntegrationTests

. ${SCRIPTS_OUTPUT_FOLDER}/test_integration.sh
