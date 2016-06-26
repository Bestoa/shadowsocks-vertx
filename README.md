shadowsocks-java
================

[![Build Status](https://travis-ci.org/Bestoa/shadowsocks-java.svg?branch=master)](https://travis-ci.org/Bestoa/shadowsocks-java)
[![codecov](https://codecov.io/gh/Bestoa/shadowsocks-java/branch/master/graph/badge.svg)](https://codecov.io/gh/Bestoa/shadowsocks-java)

Rewrite simple Shadowsocks with Java.

It will remove complex funtions.

This need JDK 8.

#Current state:
Simple server + client. Version 0.7

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
    10. -t timeout(unit is second)

Crypto method:

    1. AES-128-CFB
    2. AES-192-CFB
    3. AES-256-CFB
    4. Chacha20

One time auth feature done.

Support JSON config file.(local\_address/fast\_open/workers is not support)

You could refer to demo config etc/demo.json.

How to run:
===========
### (1) Before you start
You must have 'gradle' installed first.

### (2) generate distributable zip
```
$ gradle distZip
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

Then you will get shadowsocks-fat-xx.jar in build/libs.

#### How to run
```
//Server
$ java -jar shadowsocks-fat-xx.jar -S ...
//Local
$ java -jar shadowsocks-fat-xx.jar -L ...
```
