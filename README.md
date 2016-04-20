shadowsocks-java
================

[![Build Status](https://travis-ci.org/bestoa/shadowsocks-java.svg?branch=master)](https://travis-ci.org/bestoa/shadowsocks-java)

Rewrite simple Shadowsocks with Java.

It will remove complex funtions.

#Current state:
Simple server + client. Version 0.5

Support these args:

    1. -m crypto method
    2. -k password
    3. -p bind port(server)/remote port(client)
    4. -a OTA enforcing mode
    5. -l local port
    6. -s server
    7. -S server mode(default)
    8. -L Local mode(client)

Crypto method: 

    1. AES-128-CFB
    2. AES-192-CFB
    3. AES-256-CFB
    4. Chacha20

One time auth feature done.

For server you could use: -m -k -p -a -S
For client you could use: -m -k -p -s -l -a -L

How to run:
===========
### (1) Before you start
You must have 'gradle' installed first.

### (2) generate distributable jar
$ gradle jar

Then you get build/lib/xx.jar, now you can run it as:

$ java -jar build/lib/xx.jar
