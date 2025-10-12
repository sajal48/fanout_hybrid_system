@echo off
REM JMeter Load Test Runner for Twitter Feed System (Windows)
REM Usage: run-jmeter-test.bat [gui]

setlocal enabledelayedexpansion

REM Configuration
set "TEST_FILE=twitter-feed-load-test.jmx"
set "RESULTS_DIR=results"
set "TIMESTAMP=%date:~-4,4%%date:~-10,2%%date:~-7,2%-%time:~0,2%%time:~3,2%%time:~6,2%"
set "TIMESTAMP=!TIMESTAMP: =0!"
set "REPORT_NAME=load-test-!TIMESTAMP!"

echo =========================================
echo   JMeter Load Test Runner
echo   Twitter Feed System
echo =========================================
echo.

REM Check if JMeter is in PATH
where jmeter >nul 2>&1
if %errorlevel%==0 (
    echo [INFO] Found JMeter in PATH
    set "JMETER_CMD=jmeter"
    goto :check_test_file
)

REM Try to find JMeter in common Windows locations
set "JMETER_CMD="
for %%d in (
    "C:\apache-jmeter*\bin\jmeter.bat"
    "C:\Program Files\apache-jmeter*\bin\jmeter.bat"
    "C:\Program Files (x86)\apache-jmeter*\bin\jmeter.bat"
    "%USERPROFILE%\apache-jmeter*\bin\jmeter.bat"
    "%USERPROFILE%\Downloads\apache-jmeter*\bin\jmeter.bat"
    "D:\apache-jmeter*\bin\jmeter.bat"
) do (
    if exist "%%d" (
        set "JMETER_CMD=%%d"
        echo [INFO] Found JMeter at: %%d
        goto :check_test_file
    )
)

echo [ERROR] JMeter not found!
echo Please install JMeter and ensure jmeter.bat is in your PATH
echo Download from: https://jmeter.apache.org/download_jmeter.cgi
echo.
echo Alternative: Set JMETER_HOME environment variable
pause
exit /b 1

:check_test_file
if not exist "%TEST_FILE%" (
    echo [ERROR] Test file not found: %TEST_FILE%
    echo Please ensure you're running this from the correct directory
    pause
    exit /b 1
)

if not exist "data" (
    echo [ERROR] Data directory not found
    echo Please ensure CSV files are in 'data/' directory
    pause
    exit /b 1
)

echo [INFO] Test file found: %TEST_FILE%
echo [INFO] Data directory found: data/

REM Check if application is running
echo [INFO] Checking if Twitter Feed System is running...
curl -s --connect-timeout 5 http://localhost:8080/actuator/health >nul 2>&1
if %errorlevel%==0 (
    echo [SUCCESS] Application is running on localhost:8080
) else (
    echo [WARNING] Application may not be running on localhost:8080
    echo [WARNING] Please ensure the Twitter Feed System is started
    echo.
    set /p "continue=Continue anyway? (y/N): "
    if /i not "!continue!"=="y" (
        echo Exiting...
        pause
        exit /b 1
    )
)

REM Create results directory
if not exist "%RESULTS_DIR%" mkdir "%RESULTS_DIR%"

REM Check if GUI mode is requested
if /i "%1"=="gui" (
    echo [INFO] Starting JMeter in GUI mode...
    echo [INFO] 1. The test plan will open in JMeter GUI
    echo [INFO] 2. Click the green 'Start' button to run the test
    echo [INFO] 3. Monitor results in the listeners
    echo.
    "%JMETER_CMD%" -t "%TEST_FILE%"
) else (
    echo [INFO] Starting JMeter load test in command line mode...
    echo [INFO] Results will be saved to: %RESULTS_DIR%\%REPORT_NAME%.jtl
    echo [INFO] HTML report will be generated in: %RESULTS_DIR%\%REPORT_NAME%-report
    echo.
    
    "%JMETER_CMD%" -n -t "%TEST_FILE%" -l "%RESULTS_DIR%\%REPORT_NAME%.jtl" -e -o "%RESULTS_DIR%\%REPORT_NAME%-report"
    
    if %errorlevel%==0 (
        echo.
        echo [SUCCESS] Load test completed!
        echo [SUCCESS] Results saved to: %RESULTS_DIR%\%REPORT_NAME%.jtl
        echo [SUCCESS] HTML report generated: %RESULTS_DIR%\%REPORT_NAME%-report\index.html
        echo.
        echo Opening HTML report...
        start "" "%RESULTS_DIR%\%REPORT_NAME%-report\index.html"
    ) else (
        echo [ERROR] JMeter test failed with error code %errorlevel%
        echo Check the JMeter logs for details
        pause
        exit /b 1
    )
)

echo.
echo =========================================
echo   Test Execution Complete!
echo =========================================
echo.
echo 📊 Next Steps:
echo   1. Review HTML report for detailed analysis
echo   2. Check Grafana dashboard: http://localhost:3000
echo   3. Monitor application logs for any errors
echo   4. Compare results with performance baselines
echo.
echo 🔗 Useful Links:
echo   Application: http://localhost:8080
echo   Grafana: http://localhost:3000/d/twitter-feed-metrics/application-metrics
echo   Prometheus: http://localhost:9090
echo.
pause