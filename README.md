shadowsocks-vertx
================

[![Build Status](https://travis-ci.org/Bestoa/shadowsocks-vertx.svg?branch=master)](https://travis-ci.org/Bestoa/shadowsocks-vertx)
[![codecov](https://codecov.io/gh/Bestoa/shadowsocks-java/branch/master/graph/badge.svg)](https://codecov.io/gh/Bestoa/shadowsocks-java)
[![License](http://img.shields.io/:license-apache-blue.svg?style=flat-square)](http://www.apache.org/licenses/LICENSE-2.0.html)

shadowsocks-vertx is a lightweight tunnel proxy which can help you get through firewalls. It is a java port of shadowsocks.

The protocol is compatible with the origin shadowsocks (if both have been upgraded to the latest version).

JDK 8 is needed.

#Current state:
Server + Client. Version 0.8

Support these args:

    1. -m crypto method
    2. -k password
    3. -p bind port(server)/remote port(client)
    4. -a OTA enforcing mode
    5. -l local port
    6. -s server
    7. -S server mode
    8. -L Local mode(client, default)
    9. -c config file
    10. -t timeout(unit is second, currently unused)
    11. -h show help.

Crypto method:

    1. aes-128-cfb/ofb
    2. aes-192-cfb/ofb
    3. aes-256-cfb/ofb
    4. chacha20

One time auth feature done.

Support JSON config file.
Note:

    1. Not support: local_address, client will bind 0.0.0.0 not 127.0.0.1
    2. Not support: fast_open. Java don't have such feature.
    3. Not support: workers. Vertx will set work thread number automatically.
    4. Additional: server_mode, set the running mode, server or client.

You could refer to demo config etc/demo.json.

How to run:
===========
### (1) Before you start
You must have 'gradle' installed first, or use gradle wrapper ./gradlew.

### (2) generate distributable zip
```
$ gradle distZip
```
or
```
$ ./gradlew distZip
```

Then you will get shadowsocks-xx.zip in build/distributions.
Unzip it, the folder should contain bin and lib.

#### How to run
```
//Server
$ bin/shadowsocks -S ...
//Local
$ bin/shadowsocks -L ...
```

### (3) generate all-in-one jar
```
$ gradle fatJar
```
or
```
$ ./gradlew fatJar
```


Then you will get shadowsocks-fat-xx.jar in build/libs.

#### How to run
```
//Server
$ java -jar shadowsocks-fat-xx.jar -S ...
//Local
$ java -jar shadowsocks-fat-xx.jar -L ...
```
