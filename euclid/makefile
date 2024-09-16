
JARFILE = euclidtile.jar
JSRC = EuclidTile.java EuclidTileTest.java
TSTILTS = java -classpath $(JARFILE):stilts.jar -Djel.classes=EuclidTile \
               uk.ac.starlink.ttools.Stilts

DOCLET = uk.ac.starlink.ttools.build.SunDoclet
# for JDK9+:
# DOCLET = uk.ac.starlink.ttools.build.JdkDoclet

build: $(JARFILE)

stilts.jar:
	curl -OL http://www.starlink.ac.uk/stilts/stilts.jar

$(JARFILE): $(JSRC) stilts.jar
	rm -rf tmp
	mkdir tmp
	javac -classpath stilts.jar -d tmp $(JSRC)
	javadoc -docletpath stilts.jar -doclet '$(DOCLET)$$Html' -d tmp $(JSRC)
	cp $(JSRC) tmp/
	cp makefile tmp/
	cd tmp; jar cf ../$@ *
	rm -rf tmp

testcalcs: build
	$(TSTILTS) calc 'euclidTileId(75,-49)'
	$(TSTILTS) calc 'euclidTileIds(76,-45.4)'

test: build
	java -classpath $(JARFILE):stilts.jar EuclidTileTest

funcs: build
	$(TSTILTS) funcs

clean:
	rm -rf tmp
	rm -f $(JARFILE)

veryclean: clean
	rm -f stilts.jar

