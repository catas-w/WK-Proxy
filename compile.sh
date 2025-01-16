#!/bin/sh

mvn clean
mvn install

# shellcheck disable=SC2164
cd gui;
export GRAALVM_HOME='/Library/Java/JavaVirtualMachines/graalvm-java23-darwin-aarch64-gluon-23+25.1-dev/Contents/Home';
export JAVA_HOME=$GRAALVM_HOME
echo $JAVA_HOME;

cp src/main/resources/graal/darwin/* ./src/main/resources/META-INF.native-image/

mvn clean;
#mvn gluonfx:runagent
mvn gluonfx:compile

rm target/gluonfx/aarch64-darwin/gvm/lib/libjnidispatch.a

mvn gluonfx:link

mvn gluonfx:package