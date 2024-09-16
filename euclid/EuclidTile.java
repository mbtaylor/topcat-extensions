
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xml.sax.SAXException;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.TableSink;
import uk.ac.starlink.ttools.func.Shapes;
import uk.ac.starlink.util.ContentCoding;
import uk.ac.starlink.util.LongList;
import uk.ac.starlink.vo.TapCapabilitiesDoc;
import uk.ac.starlink.vo.TapQuery;
import uk.ac.starlink.vo.TapService;
import uk.ac.starlink.vo.TapServices;
import uk.ac.starlink.vo.TapVersion;

/**
 * Knows how to map sky positions to Euclid tile_index values.
 * The tile IDs and extents are obtained from a query to the OTF archive
 * (performed once lazily on first use of these functions) along the lines of:
 * <pre>
 *    SELECT tile_index, fov FROM sedm.mosaic_product
 * </pre>
 *
 * <p>This is not bulletproof, since it will fail for tiles straddling the
 * antimeridian, but at time of writing no tiles in OTF do that.
 * It is not particuarly efficient either, but at time of writing the
 * number of distinct tiles is not large (a few thousand).
 *
 * <p>Use this class with TOPCAT/STILTS by putting it on the classpath
 * and providing its name in the jel.classes system property, e.g.:
 * <pre>
 *    topcat -classpath euclidtile.jar -Djel.classes=EuclidTile
 * </pre>
 *
 * @author   Mark Taylor
 * @since    16 Jul 2024
 */
public class EuclidTile {

    private static final String OTF_TAP =
        "https://easotf.esac.esa.int/tap-server/tap";
    private static final Logger logger_ =
        Logger.getLogger(EuclidTile.class.getName());

    private static Tile[] tiles_;

    private EuclidTile() {
    }

    /**
     * Identifies a Euclid tile_index within which the given position falls.
     * If it falls within more than one tile, the lowest-numbered index
     * is returned.
     *
     * @param  ra  right ascension in degrees
     * @param  dec  declination in degrees
     * @return  Euclid tile index containing input position, or null
     */
    public static Long euclidTileId(double ra, double dec) {
        for (Tile tile : getTiles()) {
            if (tile.containsPosition(ra, dec)) {
                return Long.valueOf(tile.id_);
            }
        }
        return null;
    }

    /**
     * Returns the number of distinct Euclid tiles within which the
     * given position falls.
     *
     * @param  ra  right ascension in degrees
     * @param  dec  declination in degrees
     * @return   number of Euclid tiles for position, may be zero or more
     */
    public static int euclidTileIdCount(double ra, double dec) {
        return euclidTileIds(ra, dec).length;
    }

    /**
     * Returns an array of Euclid tile_index values for tiles within which
     * the given position falls.
     * Most positions within the Euclid footprint will return a single-element
     * array, though in some cases there may be two or more.
     * Positions outside the Euclid footprint will return an empty array.
     *
     * @param  ra  right ascension in degrees
     * @param  dec  declination in degrees
     * @return  array of zero or more Euclid tile indices
     *          containing input position
     */
    public static long[] euclidTileIds(double ra, double dec) {
        LongList idList = new LongList();
        for ( Tile tile : getTiles() ) {
            if ( tile.containsPosition(ra, dec) ) {
                idList.add(tile.id_);
            }
        }
        return idList.toLongArray();
    }

    private static Tile[] getTiles() {
        if (tiles_ == null) {
            synchronized (EuclidTile.class) {
                if (tiles_ == null) {
                    tiles_ = readTiles();
                }
            }
        }
        return tiles_;
    }

    private static Tile[] readTiles() {
        try {
            URL baseUrl = new URL(OTF_TAP);
            TapService service = TapServices.createDefaultTapService(baseUrl);

            // SELECT DISTINCT is what we want here, but it won't work
            // because the service cannot identify distnct array values.
            // For the same reason, aggregate functions won't work.
            // So grab them all and filter to unique tile_ids when we get them.
            String adql = "SELECT tile_index, fov FROM sedm.mosaic_product";
            TapQuery tq = new TapQuery( service, adql, null );
            Map<Long,Tile> tileMap = new HashMap<>();
            TableSink sink = new TableSink() {
                public void acceptMetadata(StarTable meta) {
                }
                public void acceptRow(Object[] row) {
                    Long id = (Long) row[0];
                    double[] fov = (double[]) row[1];
                    Tile tile = new Tile(id.longValue(), fov);
                    Tile prev = tileMap.put(id, tile);
                    if (prev != null && !prev.equals( tile )) {
                        logger_.warning("Euclid tile ID " + id
                                      + " has differing fovs");
                    }
                }
                public void endRows() {
                }
            };
            tq.executeSync(sink, ContentCoding.GZIP);
            Tile[] tiles = tileMap.values().toArray(new Tile[0]);
            Arrays.sort(tiles, (t1, t2) -> Long.compare(t1.id_, t2.id_));
            logger_.info("Distinct Euclid tile IDs read: " + tiles.length);
            return tiles;
        }
        catch (IOException | SAXException e) {
            logger_.log(Level.WARNING, "Failed to read Euclid Tile IDs", e);
            return new Tile[0];
        }
    }

    private static class Tile {
        final long id_;
        final double[] vertices_;
        Tile(long id, double[] vertices) {
            id_ = id;
            vertices_ = vertices;
        }
        boolean containsPosition(double x, double y) {
            return Shapes.isInside(x, y, vertices_);
        }
        @Override
        public int hashCode() {
            return (int) id_ + Arrays.hashCode(vertices_);
        }
        @Override
        public boolean equals(Object o) {
            if (o instanceof Tile) {
                Tile other = (Tile) o;
                return this.id_ == other.id_
                    && Arrays.equals(this.vertices_, other.vertices_);
            }
            else {
                return false;
            }
        }
    }
}
