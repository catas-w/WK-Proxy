

chcp 65001
cd D:\PY_Projects\Wicked-Proxy\gui

D:\vs2019\VC\Auxiliary\Build\vcvars64.bat

@REM set GRAALVM_HOME=C:\Program Files\Java\graalvm-java23-windows-amd64-gluon-23+25.1-dev
set GRAALVM_HOME=C:\Program Files\Java\graalvm-svm-java17-windows-gluon-22.1.0.1-Final
set JAVA_HOME=%GRAALVM_HOME%
echo %JAVA_HOME%

set PATH=%PATH%;C:\Program Files (x86)\WiX Toolset v3.14\bin
echo %PATH%

@REM set PATH=%PATH%;D:\visualStudio\product\VC\Tools\MSVC\14.29.30133\include
@REM set PATH=%PATH%;C:\Program Files (x86)\Windows Kits\10\Include\10.0.19041.0\ucrt
@REM set PATH=D:\vs2019\VC\Tools\MSVC\14.27.29110\include;%PATH%
@REM echo %PATH%

mvn clean

@REM mvn gluonfx:runagent
mvn gluonfx:compile
mvn gluonfx:link

mvn gluonfx:package




