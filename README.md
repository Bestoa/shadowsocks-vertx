shadowsocks-vertx
================

Only support SS-V1

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/c374229026604afe9383ab11e2f46d04)](https://app.codacy.com/app/Bestoa/shadowsocks-vertx?utm_source=github.com&utm_medium=referral&utm_content=Bestoa/shadowsocks-vertx&utm_campaign=Badge_Grade_Dashboard)
[![Build Status](https://travis-ci.org/Bestoa/shadowsocks-vertx.svg?branch=master)](https://travis-ci.org/Bestoa/shadowsocks-vertx)
[![codecov](https://codecov.io/gh/Bestoa/shadowsocks-vertx/branch/master/graph/badge.svg)](https://codecov.io/gh/Bestoa/shadowsocks-vertx)
[![License](http://img.shields.io/:license-apache-blue.svg?style=flat-square)](http://www.apache.org/licenses/LICENSE-2.0.html)

## Intro

shadowsocks-vertx is a lightweight tunnel proxy which can help you get through firewalls. It is a java port of shadowsocks. This project is based on vert.x.

The protocol is compatible with the origin shadowsocks (if both have been upgraded to the latest version).

JDK 8 is needed.

Current version 0.8.4

It is **unstable**! If you encounter any problems, please open an issue.

About shadowsocks, please refer to [shadowsocks](https://shadowsocks.org/)

About vert.x, please refer to [vertx](http://vertx.io/)

## Features

Supported argument:

    1. -m/--method crypto method
    2. -k/--password password
    3. -p/--server_port bind port(server)/remote port(client)
    4. -a/--auth OTA enforcing mode
    5. -l/--local_port local port
    6. -s/--server server
    7. -S/--server_mode server mode(default is client mode)
    8. -c/--config config file
    9. -t/--timeout timeout(unit is second, currently unused)
    10. -h/--help show help.

Supported encrypt method:

    1. aes-128-cfb
    2. aes-192-cfb
    3. aes-256-cfb
    4. chacha20/chacha20-ietf

Supported one-time auth.

Support JSON config file. Please refer to [shadowsocks config](https://github.com/shadowsocks/shadowsocks/wiki/Configuration-via-Config-File).
Note:

    1. Not support: local_address, client will bind 0.0.0.0 not 127.0.0.1
    2. Not support: fast_open. Java doesn't have such feature.
    3. Not support: workers. Vertx will set work thread number automatically.
    4. Additional: server_mode, set the running mode, true for the server.

You could refer to demo config etc/demo.json.

### How to run

#### (1) Before you start
You must have 'gradle' installed first, or use gradle wrapper ./gradlew.

#### (2) generate distributable zip
```shell
$ gradle distZip
```
or
```shell
$ ./gradlew distZip
```

Then you will get shadowsocks-ver.zip in build/distributions.
Unzip it, the folder should contain bin and lib.

#### Run
```shell
//Server
$ bin/shadowsocks -S ...
$ bin/shadowsocks --server_mode ...
//Local
$ bin/shadowsocks ...
```

### (3) generate all-in-one jar
```shell
$ gradle fatJar
```
or
```shell
$ ./gradlew fatJar
```


Then you will get shadowsocks-fat-ver.jar in build/libs.

#### How to run
```shell
//Server
$ java -jar shadowsocks-fat-xx.jar -S ...
$ java -jar shadowsocks-fat-xx.jar --server_mode ...
//Local
$ java -jar shadowsocks-fat-xx.jar ...
```

