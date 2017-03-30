# tomcat-launcher

## Usage
See [hello-tomcat](https://github.com/pivotalservices/hello-tomcat)


#### run clean build

    ./gradlew clean build

#### run unit tests

    ./gradlew clean test

#### run integration tests
    
    ./gradlew clean integrationTest

#### run clean build and exclude integration tests

    ./gradlew clean build -x integrationTest

#### run clean build and exclude unit tests

    ./gradlew clean build -x test

#### run unit and integration tests
    
    ./gradlew clean test integrationTest
