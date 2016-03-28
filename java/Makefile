default: crypto util ss
crypto:
	javac -cp lib/bcprov-jdk15on-154.jar shadowsocks/crypto/*.java -Xlint
ss:
	javac shadowsocks/*.java -Xlint
util:
	javac -cp lib/java-getopt-1.0.13.jar shadowsocks/util/*.java -Xlint
run:
	java -cp ".:lib/bcprov-jdk15on-154.jar:lib/java-getopt-1.0.13.jar" shadowsocks.SSserver
clean:
	rm -rf shadowsocks/*.class
	rm -rf shadowsocks/crypto/*.class
	rm -rf shadowsocks/util/*.class
