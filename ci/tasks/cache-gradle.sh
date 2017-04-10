#!/bin/bash

export ROOT_FOLDER=$( pwd )
export REPO_RESOURCE=repo
export VERSION_RESOURCE=version
export OUTPUT_RESOURCE=out

echo "Root folder is [${ROOT_FOLDER}]"
echo "Repo resource folder is [${REPO_RESOURCE}]"
echo "Version resource folder is [${VERSION_RESOURCE}]"

# source ${ROOT_FOLDER}/${REPO_RESOURCE}/ci/tasks/pipeline.sh

M2_LOCAL=${ROOT_FOLDER}/${M2_REPO}/repository
echo "Changing the maven local to [${M2_LOCAL}]"
export MAVEN_ARGS="-Dmaven.repo.local=${M2_LOCAL}"

export GRADLE_USER_HOME=${ROOT_FOLDER}/${M2_REPO}/.gradle
export GRADLE_OPTS=-Dorg.gradle.native=false
echo "Gradle user home folder is [${GRADLE_USER_HOME}]"

if [ "$1" == "init" ]; then
	mkdir -p ${GRADLE_USER_HOME}
	mkdir -p ${M2_LOCAL}
fi

cd ${REPO_RESOURCE}
./gradlew --full-stacktrace --parallel --no-daemon clean build test -x integrationTest --project-cache-dir ${GRADLE_USER_HOME}
cd ${ROOT_FOLDER}/m2
tar -C rootfs -cf rootfs.tar .
mv rootfs.tar ${ROOT_FOLDER}/to-push/
