shadowsocks-vertx
================

[![License](http://img.shields.io/:license-apache-blue.svg?style=flat-square)](http://www.apache.org/licenses/LICENSE-2.0.html)

Intro
===========

Shadowsocks-vertx is a socks5 proxy which can help you get through firewalls. It is a java port of shadowsocks. This project is based on vert.x.

About shadowsocks, please refer to https://shadowsocks.org/

About vert.x, please refer to https://vertx.io/

Features
===========

Not support udp.

Support ipv6.

Support JSON config file, refer to etc/config.json. 

Support encrypt method:

    aes-256-cfb, chacha20, rc4-md5


**Support customized iv_len !**

**Support noise data !**


How to run
===========

### 1 Install jdk8
```
$ yum -y install java-1.8.0-openjdk java-1.8.0-openjdk-devel
```

### 2 Download fat jar
```
$ wget https://raw.githubusercontent.com/lenovobenben/shadowsocks-vertx/master/shadowsocks-fat-1.0.0.jar
```

### 3 Edit config.json

refer to etc/config.json.

### 4 Run both server and client
```
$ java -jar shadowsocks-fat-1.0.0.jar config.json
```

### 5 Web browser

Chrome + SwitchyOmega .



Linux settings
===========

### 1 Set tcp_keepalive

Edit /etc/sysctl.conf ,then ```sysctl -p``` .  
Recommended as follows:

    net.ipv4.tcp_keepalive_time = 600  (default 7200)  
    net.ipv4.tcp_keepalive_intvl = 20  (default 75)  
    net.ipv4.tcp_keepalive_probes = 4  (default 9)
 

### 2 Google BBR
It is recommended to use TCP-BBR !

### 3 Auto start
vi /etc/rc.d/rc.local to auto start it !
