
JARFILE = euclidtile.jar
JSRC = EuclidTile.java

DOCLET = uk.ac.starlink.ttools.build.SunDoclet
# for JDK9+:
# DOCLET = uk.ac.starlink.ttools.build.JdkDoclet

build: $(JARFILE)

stilts.jar:
	curl -OL http://www.starlink.ac.uk/stilts/stilts.jar

$(JARFILE): stilts.jar
	rm -rf tmp
	mkdir tmp
	javac -classpath stilts.jar -d tmp $(JSRC)
	javadoc -docletpath stilts.jar -doclet '$(DOCLET)$$Html' -d tmp $(JSRC)
	cp $(JSRC) tmp/
	cp makefile tmp/
	cd tmp; jar cf ../$@ *
	rm -rf tmp

test: build
	java -classpath $(JARFILE):stilts.jar -Djel.classes=EuclidTile \
             uk.ac.starlink.ttools.Stilts \
             calc 'euclidTileId(75,-49)'

clean:
	rm -rf tmp
	rm -f $(JARFILE)

veryclean: clean
	rm -f stilts.jar

