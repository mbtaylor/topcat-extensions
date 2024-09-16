# Euclid TOPCAT extension functions

These functions are for use with the Euclid On-The-Fly TAP service at
https://easotf.esac.esa.int/tap-server/tap
The jar file may be at
https://www.star.bristol.ac.uk/mbt/euclid/euclidtile.jar

Use like:

```
topcat -classpath euclidtile.jar -Djel.classes=EuclidTile
```
or alternatively (if you don't have the `topcat` script):
```
java -classpath topcat-full.jar:euclidtile.jar -Djel.classes=EuclidTile uk.ac.starlink.topcat.Driver
```

The documentation for the functions can be seen in TOPCAT's
[Available Functions](https://www.star.bristol.ac.uk/mbt/topcat/sun253/MethodWindow.html) window
under the *EuclidTile* heading (at the bottom), or from STILTS like:
```
   stilts -classpath euclidtile.jar -Djel.classes=EuclidTile funcs
```

You can try a single calculation using `stilts calc` like:
```
   stilts -classpath euclidtile.jar -Djel.classes=EuclidTile calc 'euclidTileIds(76,-45.4)'
   ->   [102024002, 102024003]
```

