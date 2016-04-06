shadowsocks-java
================

[![Build Status](https://travis-ci.org/bestoa/shadowsocks-java.svg?branch=master)](https://travis-ci.org/bestoa/shadowsocks-java)

Rewrite simple Shadowsock with Java.
It will remove complex funtions.

#Current state:
Simple server v0.4, support -m -k -p.

Crypto method: 
  1. AES-128-CFB
  2. AES-192-CFB
  3. AES-256-CFB
  4. Chacha20

One time auth feature done.

#How to run:
=======
### (1) Before you start
You must have 'gradle' installed first.

### (2.1) instant run
$ gradle run

Then you get your ss server ready on default port 8388 with default password '123456'.

### (2.2) generate distributable jar
$ gradle jar

Then you get build/lib/xx.jar, now you can run it as:

$ java -jar build/lib/xx.jar -p 8388 -k 123456
