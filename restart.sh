# 强杀 java 进程
killall -9 java

sleep 1

# 下载 jar
curl https://raw.githubusercontent.com/lenovobenben/shadowsocks-vertx/master/shadowsocks-fat-0.8.3.jar -o shadowsocks-fat-0.8.3.jar --progress

# 启动
java -jar shadowsocks-fat-0.8.3.jar config.json > consoleSS.log 2>&1  &

# 如果VPS不支持 ipv6 ，则必须添加jvm参数： java.net.preferIPv4Stack=true