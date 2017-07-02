@echo off
set JAVA_CMD=%JAVA_HOME%\bin\java
set CLASSPATH=.
FOR %%F IN (target\*.jar) DO call :addcp %%F

FOR %%F IN (target\lib\*.jar) DO call :addcp %%F
goto extlibe

:addcp
set CLASSPATH=%CLASSPATH%;%1
goto :eof

:extlibe
"%JAVA_CMD%" -Xmx512M -cp "%CLASSPATH%" kr.pe.ghp.fileserver.server.Main