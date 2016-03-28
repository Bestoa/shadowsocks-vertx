default: crypto util ss jar
crypto:
	javac -cp lib/bcprov-jdk15on-154.jar shadowsocks/crypto/*.java -Xlint
ss:
	javac shadowsocks/*.java -Xlint
util:
	javac -cp lib/java-getopt-1.0.13.jar shadowsocks/util/*.java -Xlint
jar:
	 jar cvfm ss.jar build.mf `find . -name *.class|xargs`
clean:
	rm -rf *.jar
	rm -rf `find . -name *.class|xargs`
