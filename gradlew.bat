@echo off
rem -----------------------------------------------------------------------------
rem
rem  Gradle start up script for Windows
rem
rem -----------------------------------------------------------------------------

set DEFAULT_JVM_OPTS=

set DIR=%~dp0
set CLASSPATH=%DIR%gradle\wrapper\gradle-wrapper.jar

"%JAVA_HOME%\bin\java" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
