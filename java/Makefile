default: crypto ss
crypto:
	javac -cp lib/bcprov-jdk15on-154.jar shadowsocks/crypto/*.java -Xlint
ss:
	javac shadowsocks/*.java -Xlint
run:
	java -cp ".:lib/bcprov-jdk15on-154.jar" shadowsocks.SSserver
clean:
	rm -rf shadowsocks/*.class
	rm -rf shadowsocks/crypto/*.class
