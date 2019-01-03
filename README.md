Shadowsocks-Vertx
================

[![License](http://img.shields.io/:license-apache-blue.svg?style=flat-square)](http://www.apache.org/licenses/LICENSE-2.0.html)

简介
===========

Shadowsocks-Vertx 是一个 socks5 代理，基于 Java Vert.x 。

Features
===========

不支持 UDP ！

支持 json 配置文件，参见 etc/config.json。 

仅支持以下3种加密算法:

    aes-256-cfb, chacha20, rc4-md5


**完美支持IPv6**

**支持自定义的iv长度**

**支持添加噪音数据**


运行方法
===========

### 服务端（以 CentOS 为例）
1 安装JDK8
```
$ yum -y install java-1.8.0-openjdk java-1.8.0-openjdk-devel
```

2 下载 fat jar
```
$ wget https://raw.githubusercontent.com/lenovobenben/shadowsocks-vertx/master/shadowsocks-fat-1.0.0.jar
```

3 编辑 config.json

4 运行 java
```
$ java -jar shadowsocks-fat-1.0.0.jar config.json
```

### 客户端（以 Windows 为例）
1 安装JDK8  
2 下载 fat jar  
3 编辑 config.json  
4 运行 java  
操作简单，不赘述。

浏览器： Chrome + SwitchyOmega 。

SwitchyOmega 选择 socks5 ，端口选择 1080 （默认）即可。


Linux 优化
===========

### 开启 Google BBR 加速

### 加入开机自启动

