#!/bin/bash
mkdir lib
cd lib
wget -c http://www.bouncycastle.org/download/bcprov-jdk15on-154.jar
wget -c http://www.urbanophile.com/arenn/hacking/getopt/java-getopt-1.0.13.jar
cd ..
make
