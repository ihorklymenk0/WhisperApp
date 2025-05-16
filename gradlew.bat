@echo off
set DIR=%~dp0
set CLASSPATH=%DIR%gradle\wrapper\gradle-wrapper.jar
"%JAVA_HOME%\bin\java" -cp "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
