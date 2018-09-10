shadowsocks-vertx
================

[![License](http://img.shields.io/:license-apache-blue.svg?style=flat-square)](http://www.apache.org/licenses/LICENSE-2.0.html)

Intro
===========

Shadowsocks-vertx is a socks5 proxy which can help you get through firewalls. It is a java port of shadowsocks. This project is based on vert.x.

About shadowsocks, please refer to https://shadowsocks.org/

About vert.x, please refer to http://vertx.io/

Features
===========

Not support ipv6.

Not support udp.

Support JSON config file, refer to etc/config.json. 

Supported encrypt method:

    aes-256-cfb, chacha20, rc4-md5

The original iv_len for them:

    aes-256-cfb    : 16
    chacha20       : 8
    rc4-md5        : 16

If the iv_len is equal to the original length of the algorithm, it will be compatible with the official shadowsocks.
But it is not recommended ! **You can customize iv_len !**


How to run
===========

### 1 Install java8 and gradle

### 2 Generate fat jar
```
$ gradle clean build fatJar
```

Then you will get **shadowsocks-fat-ver.jar** in build/libs.

### 3 Run both server and client
```
$ java -jar shadowsocks-fat-ver.jar config.json
```

### 4 Web browser

Chrome + SwitchyOmega .



Linux settings
===========

### 1 Tcp_keepalive

Edit /etc/sysctl.conf ,then ```sysctl -p``` .  
Recommended as follows:

    net.ipv4.tcp_keepalive_time = 600  (default 7200)  
    net.ipv4.tcp_keepalive_intvl = 20  (default 75)  
    net.ipv4.tcp_keepalive_probes = 4  (default 9)
 

### 2 Google BBR
It is recommended to use TCP-BBR !

### 3 Auto run
vi /etc/rc.d/rc.local to auto start it !
