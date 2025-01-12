

chcp 65001
cd D:\PY_Projects\Wicked-Proxy\gui

@REM set GRAALVM_HOME=C:\Program Files\Java\graalvm-java23-windows-amd64-gluon-23+25.1-dev
set GRAALVM_HOME=C:\Program Files\Java\graalvm-svm-java17-windows-gluon-22.1.0.1-Final
set JAVA_HOME=%GRAALVM_HOME%
echo %JAVA_HOME%

set PATH=%PATH%;C:\Program Files (x86)\WiX Toolset v3.14\bin
echo %PATH%

mvn clean

@REM mvn gluonfx:runagent
mvn gluonfx:compile
mvn gluonfx:link

mvn gluonfx:package




