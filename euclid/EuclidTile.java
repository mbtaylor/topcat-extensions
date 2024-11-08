
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 *
 * <p>The tile IDs and extents are obtained from a query,
 * performed lazily on first use of these functions, to one of the
 * Euclid archives.
 * The query is along the lines of:
 * <pre>
 *    SELECT tile_index, fov,
             filter_name, instrument_name, file_name, file_path
 *    FROM sedm.mosaic_product
 * </pre>
 *
 * <p>This is not bulletproof, since it will fail for tiles straddling the
 * antimeridian, but at time of writing no tiles in OTF do that.
 * It is not particuarly efficient either, but at time of writing the
 * number of distinct tiles is not large (a few thousand).
 *
 * <p>Each function has a first argument <code>serviceName</code>
 * indicating which of the Euclid TAP archive services is to be used.
 * This is usually a three-letter abbreviation, such as "otf" or "idr".
 * In some cases a similarly-named token is provided for convenience.
 * You can also use the whole URL.
 * So for instance to query OTF you can write any of:
 * <pre>
 *    euclidTileId(OTF, ra, dec)
 *    euclidTileId("otf", ra, dec)
 *    euclidTileId("https://easotf.esac.esa.int/tap-server/tap", ra, dec)
 * </pre>
 * If you reference a service here that doesn't exist or that doesn't have
 * a public <code>sedm.mosaic_product</code> table, things won't work.
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

    private static final Map<String,Service> serviceMap_ =
        new ConcurrentHashMap<>();
    private static final Logger logger_ =
        Logger.getLogger(EuclidTile.class.getName());

    private EuclidTile() {
    }

    /** Service nickname for OTF. */
    public static final String OTF = "otf";

    /** Service nickname for IDR. */
    public static final String IDR = "idr";

    /** Filter name DECAM_g. */
    public static final String DECAM_g = "DECAM_g";

    /** Filter name DECAM_i. */
    public static final String DECAM_i = "DECAM_i";

    /** Filter name DECAM_r. */
    public static final String DECAM_r = "DECAM_r";

    /** Filter name DECAM_z. */
    public static final String DECAM_z = "DECAM_z";

    /** Filter name HSC_g. */
    public static final String HSC_g = "HSC_g";

    /** Filter name HSC_i. */
    public static final String HSC_i = "HSC_i";

    /** Filter name HSC_i2. */
    public static final String HSC_i2 = "HSC_i2";

    /** Filter name HSC_r. */
    public static final String HSC_r = "HSC_r";

    /** Filter name HSC_r2. */
    public static final String HSC_r2 = "HSC_r2";

    /** Filter name HSC_z. */
    public static final String HSC_z = "HSC_z";

    /** Filter name MEGACAM_r. */
    public static final String MEGACAM_r = "MEGACAM_r";

    /** Filter name MEGACAM_u. */
    public static final String MEGAGAM_u = "MEGACAM_u";

    /** Filter name NIR_H. */
    public static final String NIR_H = "NIR_H";

    /** Filter name NIR_J. */
    public static final String NIR_J = "NIR_J";

    /** Filter name NIR_Y. */
    public static final String NIR_Y = "NIR_Y";

    /** Filter name PANSTARRS_i. */
    public static final String PANSTARRS_i = "PANSTARRS_i";

    /** Filter name VIS. */
    public static final String VIS = "VIS";

    /**
     * Identifies a Euclid tile_index within which the given position falls.
     * If it falls within more than one tile, the lowest-numbered index
     * is returned.
     *
     * @param  serviceName  name of Euclid TAP service, such as "otf"
     * @param  ra  right ascension in degrees
     * @param  dec  declination in degrees
     * @return  Euclid tile index containing input position, or null
     */
    public static Long euclidTileId(String serviceName, double ra, double dec) {
        for (Tile tile : getTiles(serviceName).values()) {
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
     * @param  serviceName  name of Euclid TAP service, such as "otf"
     * @param  ra  right ascension in degrees
     * @param  dec  declination in degrees
     * @return   number of Euclid tiles for position, may be zero or more
     */
    public static int euclidTileIdCount(String serviceName,
                                        double ra, double dec) {
        return euclidTileIds(serviceName, ra, dec).length;
    }

    /**
     * Returns an array of Euclid tile_index values for tiles within which
     * the given position falls.
     * Most positions within the Euclid footprint will return a single-element
     * array, though in some cases there may be two or more.
     * Positions outside the Euclid footprint will return an empty array.
     *
     * @param  serviceName  name of Euclid TAP service, such as "otf"
     * @param  ra  right ascension in degrees
     * @param  dec  declination in degrees
     * @return  array of zero or more Euclid tile indices
     *          containing input position
     */
    public static long[] euclidTileIds(String serviceName,
                                       double ra, double dec) {
        LongList idList = new LongList();
        for ( Tile tile : getTiles(serviceName).values() ) {
            if ( tile.containsPosition(ra, dec) ) {
                idList.add(tile.id_);
            }
        }
        long[] ids = idList.toLongArray();
        Arrays.sort(ids);
        return ids;
    }

    /**
     * Returns the cutout URL for a given tile_index and filter.
     * This is supposed to correspond to the cutout_access_url field
     * in the ivoa.obscore table.
     * It is reverse engineered from values actually found there,
     * so I'm hoping it will work, but it might not.
     * The cutout URL is likely to require authentication for download.
     *
     * @param  serviceName  name of Euclid TAP service, such as "otf"
     * @param  tileId  tile index
     * @param  filter  filter name
     * @return  cutout URL
     */
    public static String euclidTileCutoutUrl(String serviceName,
                                             long tileId, String filter) {
        Product product = getProduct(serviceName, tileId, filter);
        if (product == null) {
            return null;
        }
        else {
            return "https://eas"
                 + getService(serviceName).nickname_ 
                 + ".esac.esa.int/sas-cutout/cutout?filepath="
                 + product.fpath_
                 + "/"
                 + product.fname_
                 + "&collection="
                 + product.instrument_
                 + "&tileindex="
                 + tileId;
        }
    }

    /**
     * Returns the file_name field from the sedm.mosaic_product table
     * corresponding to a given tile_index and filter_name.
     *
     * @param  serviceName  name of Euclid TAP service, such as "otf"
     * @param  tileId  tile index
     * @param  filter  filter name
     * @return  file_name field
     */
    public static String euclidTileFileName(String serviceName,
                                            long tileId, String filter) {
        Product product = getProduct(serviceName, tileId, filter);
        return product == null ? null : product.fname_;
    }

    /**
     * Returns the file_path field from the sedm.mosaic_product table
     * corresponding to a given tile_index and filter_name.
     *
     * @param  serviceName  name of Euclid TAP service, such as "otf"
     * @param  tileId  tile index
     * @param  filter  filter name
     * @return  file_path field
     */
    public static String euclidTileFilePath(String serviceName,
                                            long tileId, String filter) {
        Product product = getProduct(serviceName, tileId, filter);
        return product == null ? null : product.fpath_;
    }

    /**
     * Returns a list of all the filter names for which a data product exists
     * in the sedm.mosaic_product table for a given tile index.
     *
     * @param  serviceName  name of Euclid TAP service, such as "otf"
     * @param  tileId  tile index
     * @return  list of filter names for supplied tile index
     */
    public static String[] euclidTileFilters(String serviceName, long tileId) {
        Tile tile = getTiles(serviceName).get(Long.valueOf(tileId));
        if (tile != null) {
            return tile.productMap_.keySet().stream()
                  .sorted()
                  .toArray(n -> new String[n]);
        }
        else {
            return new String[0];
        }
    }

    private static Map<Long,Tile> getTiles(String serviceName) {
        return getService(serviceName).getTiles();
    }

    private static Product getProduct(String serviceName,
                                      long tileId, String filter) {
        Tile tile = getTiles(serviceName).get(Long.valueOf(tileId));
        return tile == null ? null : tile.productMap_.get(filter);
    }

    private static Service getService(String serviceName) {
        return serviceMap_
              .computeIfAbsent(serviceName, EuclidTile::createService);
    }

    private static Service createService(String serviceName) {
        return serviceName.matches("[A-Za-z0-9_-]+")
             ? new Service(euclidNicknameTapUrl(serviceName), serviceName)
             : new Service(serviceName, "???");
    }

    private static String euclidNicknameTapUrl(String nickname) {
        return "https://eas" + nickname + ".esac.esa.int/tap-server/tap";
    }

    private static class Service {
        private final String tapUrl_;
        private final String nickname_;
        private Map<Long,Tile> tileMap_;
        Service(String tapUrl, String nickname) {
            tapUrl_ = tapUrl;
            nickname_ = nickname;
        }
        Map<Long,Tile> getTiles() {
            if (tileMap_ == null) {
                synchronized (EuclidTile.class) {
                    if (tileMap_ == null) {
                        tileMap_ = readTiles();
                    }
                }
            }
            return tileMap_;
        }
        Map<Long,Tile> readTiles() {
            try {
                URL baseUrl = new URL(tapUrl_);
                TapService service =
                    TapServices.createDefaultTapService(baseUrl);
                String adql =
                      "SELECT tile_index, fov, "
                    + "filter_name, instrument_name, file_name, file_path "
                    + "FROM sedm.mosaic_product";
                TapQuery tq = new TapQuery(service, adql, null);
                Map<Long,Tile> tileMap = new HashMap<>();
                TableSink sink = new TableSink() {
                    public void acceptMetadata(StarTable meta) {
                    }
                    public void acceptRow(Object[] row) {
                        Long id = (Long) row[0];
                        double[] fov = (double[]) row[1];
                        String filter = (String) row[2];
                        String instrument = (String) row[3];
                        String fname = (String) row[4];
                        String fpath = (String) row[5];
                        Tile newTile = new Tile(id.longValue(), fov);
                        Tile oldTile = tileMap.get(id);
                        final Tile tile;
                        if (oldTile == null) {
                            tileMap.put(id, newTile);
                            tile = newTile;
                        }
                        else {
                            if (!newTile.equals(oldTile)) {
                                logger_.warning("Euclid tile ID " + id
                                              + " has differing fovs");
                            }
                            tile = oldTile;
                        }
                        Product prod = new Product(instrument, fname, fpath);
                        tile.productMap_.put(filter, prod);
                    }
                    public void endRows() {
                    }
                };
                tq.executeSync(sink, ContentCoding.GZIP);
                logger_.info("Distinct Euclid tile IDs read: "
                           + tileMap.size());
                return tileMap;
            }
            catch (IOException | SAXException e) {
                logger_.log(Level.WARNING, "Failed to read Euclid Tile IDs", e);
                return Collections.emptyMap();
            }
        }
    }

    private static class Tile {
        final long id_;
        final double[] vertices_;
        final Map<String,Product> productMap_;
        Tile(long id, double[] vertices) {
            id_ = id;
            vertices_ = vertices;
            productMap_ = new HashMap<String,Product>();
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

    private static class Product {
        final String instrument_;
        final String fname_;
        final String fpath_;
        Product(String instrument, String fname, String fpath) {
            instrument_ = instrument;
            fpath_ = fpath;
            fname_ = fname;
        }
    }
}
