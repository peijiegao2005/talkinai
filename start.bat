@echo off
chcp 65001 >nul
echo ============================================
echo   SoulChat 启动脚本
echo ============================================
echo.

set JAVA_OPTS=-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
set JAVA_OPTS=%JAVA_OPTS% -XX:+ParallelRefProcEnabled
set JAVA_OPTS=%JAVA_OPTS% -XX:+DisableExplicitGC
set JAVA_OPTS=%JAVA_OPTS% -XX:+HeapDumpOnOutOfMemoryError
set JAVA_OPTS=%JAVA_OPTS% -XX:HeapDumpPath=logs/heapdump.hprof
set JAVA_OPTS=%JAVA_OPTS% -Dfile.encoding=UTF-8
set JAVA_OPTS=%JAVA_OPTS% -Djava.security.egd=file:/dev/./urandom

echo JVM 参数: %JAVA_OPTS%
echo 端口: 8080
echo.

:: 确保日志目录存在
if not exist "logs" mkdir logs

:: 启动应用
java %JAVA_OPTS% -jar target/soulchat-1.0.0.jar

pause
