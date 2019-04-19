@echo off
rem Licensed to the Apache Software Foundation (ASF) under one or more
rem contributor license agreements.  See the NOTICE file distributed with
rem this work for additional information regarding copyright ownership.
rem The ASF licenses this file to You under the Apache License, Version 2.0
rem (the "License"); you may not use this file except in compliance with
rem the License.  You may obtain a copy of the License at
rem
rem     http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.

rem ---------------------------------------------------------------------------
rem Start script for the CATALINA Server
rem ---------------------------------------------------------------------------

setlocal

rem Guess CATALINA_HOME if not defined
rem 设置CURRENT_DIR为当前目录，%cd%是盘符+当前目录
set "CURRENT_DIR=%cd%"
rem 如果设置CATALINA_HOME环境变量，跳转到gotHome
if not "%CATALINA_HOME%" == "" goto gotHome
rem 没有设置，将CATALINA_HOME设置为当前目录
set "CATALINA_HOME=%CURRENT_DIR%"
if exist "%CATALINA_HOME%\bin\catalina.bat" goto okHome
rem 跳转到上层目录，并设置CATALINA_HOME为当前目录
cd ..
set "CATALINA_HOME=%cd%"
cd "%CURRENT_DIR%"
:gotHome
rem 如果CATALINA_HOME\bin下面存在catalina.bat，跳转到okHome
if exist "%CATALINA_HOME%\bin\catalina.bat" goto okHome
echo The CATALINA_HOME environment variable is not defined correctly
echo This environment variable is needed to run this program
goto end
:okHome

rem 设置EXECUTABLE
set "EXECUTABLE=%CATALINA_HOME%\bin\catalina.bat"

rem Check that target executable exists
rem 检验目标EXECUTABLE是否存在，存在跳转到okExec
if exist "%EXECUTABLE%" goto okExec
echo Cannot find "%EXECUTABLE%"
echo This file is needed to run this program
goto end
:okExec

rem Get remaining unshifted command line arguments and save them in the
rem 设置命令行参数
set CMD_LINE_ARGS=
:setArgs
rem %0 本BAT的绝对路径。%1 代表命令后面的第一个参数，%2 - %9 同理
if ""%1""=="""" goto doneSetArgs
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto setArgs
:doneSetArgs

rem 执行catalina.bat，第一个参数为start
call "%EXECUTABLE%" start %CMD_LINE_ARGS%

:end
