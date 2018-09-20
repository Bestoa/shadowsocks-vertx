rem 杀进程
taskkill /f /im javaw.exe

rem 暂停 1 秒
choice /t 1 /d y /n >nul

rem 启动后自动隐藏 cmd 窗口
start javaw -jar D:\shadowsocks-fat-1.0.0.jar D:\c.json

rem 如果不支持 ipv6 ，则必须添加jvm参数： java.net.preferIPv4Stack=true