@echo off
cd /d "%~dp0\.."
call mvn -q compile jar:jar -DskipTests 1>&2
java -cp "target/classes;target/app.jar" com.coreshift.TestRunner
