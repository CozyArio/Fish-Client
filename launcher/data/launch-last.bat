@echo off
setlocal EnableDelayedExpansion
cd /d "C:\Users\motyl\Documents\AI CODING PEHNIS\Fish Client"
echo [START %DATE% %TIME%] "C:\Users\motyl\Documents\AI CODING PEHNIS\Fish Client\gradlew.bat" runClient --daemon>> "C:\Users\motyl\Documents\AI CODING PEHNIS\Fish Client\launcher\data\launch-last.log"
"C:\Users\motyl\Documents\AI CODING PEHNIS\Fish Client\gradlew.bat" runClient --daemon >> "C:\Users\motyl\Documents\AI CODING PEHNIS\Fish Client\launcher\data\launch-last.log" 2>&1
set CODE=%ERRORLEVEL%
echo [END %DATE% %TIME%] exit=!CODE!>> "C:\Users\motyl\Documents\AI CODING PEHNIS\Fish Client\launcher\data\launch-last.log"
exit /b !CODE!
