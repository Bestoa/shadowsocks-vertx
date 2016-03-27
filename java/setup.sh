#!/bin/bash
mkdir lib
cd lib
wget -c http://www.bouncycastle.org/download/bcprov-jdk15on-154.jar
cd ..
make
