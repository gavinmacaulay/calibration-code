@echo off

REM This file contains simple demonstration commands to 
REM  compile and package the ES60Adjust code.
REM
REM Open a CMD window, change to the same directory as this file and 
REM  run this file. This will produce the file 'es60adjust.jar', which
REM  can be executed as any normal Java program.
REM
REM Change the value of JAVABIN to suit the installed location of your JDK.

SET JAVABIN="c:\program files\java\jdk1.7.0_21\bin"

cd src

REM Compile...
%JAVABIN%\javac au\csiro\marine\echo\*.java au\csiro\marine\echo\data\es60\*.java

REM Package...
%JAVABIN%\jar cfe ..\es60adjust.jar "au.csiro.marine.echo.ES60Adjust" .

cd ..

