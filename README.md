shadowsocks-java
================

Prepare to rewrite simple Shadowsock with Java.
It will remove complex funtions.

#Current state:
Simple server, support -m -k -p.

Crypto method, AES-128-CFB/AES-192-CFB/AES-256/CFB.

#How to run:
=======
### (1) Before you start
You must have 'gradle' installed first.

### (2.1) instant run
$ gradle run

Then you get your ss server ready on default port 2048 with default password '123456'.

### (2.2) generate distributable jar
$ gradle jar

Then you get build/lib/xx.jar, now you can run it as:

$ java -jar build/lib/xx.jar -p 2048 -k 123456
