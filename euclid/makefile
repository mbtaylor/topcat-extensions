
JARFILE = euclidtile.jar
JSRC = EuclidTile.java EuclidTileTest.java
VERSION_FILE = version.EuclidTile
TSTILTS = java -classpath $(JARFILE):stilts.jar -Djel.classes=EuclidTile \
               uk.ac.starlink.ttools.Stilts

DOCLET = uk.ac.starlink.ttools.build.SunDoclet
# for JDK9+:
# DOCLET = uk.ac.starlink.ttools.build.JdkDoclet

build: $(JARFILE)

stilts.jar:
	curl -OL http://www.starlink.ac.uk/stilts/stilts.jar

$(VERSION_FILE): $(JSRC)
	git show -s --format=%h >$@

$(JARFILE): $(JSRC) $(VERSION_FILE) stilts.jar 
	rm -rf tmp
	mkdir tmp
	javac -classpath stilts.jar -d tmp $(JSRC)
	javadoc -classpath stilts.jar \
                -docletpath stilts.jar -doclet '$(DOCLET)$$Html' \
                -d tmp $(JSRC)
	cp $(JSRC) tmp/
	cp $(VERSION_FILE) tmp/
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

deploy: $(JARFILE) test
	cp $(JARFILE) /home/mbt/public_html/euclid/
	websync euclid

clean:
	rm -rf tmp
	rm -f $(JARFILE) $(VERSION_FILE)

veryclean: clean
	rm -f stilts.jar

