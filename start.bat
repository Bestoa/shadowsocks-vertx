rem 杀进程
taskkill /f /im javaw.exe

rem 暂停 1 秒
choice /t 1 /d y /n >nul

rem 启动后自动隐藏 cmd 窗口
start javaw -jar D:\shadowsocks-fat-0.8.3.jar D:\c.json
