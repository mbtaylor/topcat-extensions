
import java.util.Arrays;

public class EuclidTileTest {

    private static void test() {
        testOtf("otf", true);
        testOtf("https://easotf.esac.esa.int/tap-server/tap", false);
    }

    private static void testOtf(String serviceName, boolean testCutoutUrl) {
        long t1 = EuclidTile.euclidTileId(serviceName, 75, -49);
        assertTrue(t1 == 102020553);
        long[] ts = EuclidTile.euclidTileIds(serviceName, 76, -45.4);
        assertTrue(Arrays.equals(new long[]{102024002, 102024003}, ts));
        String[] filters = EuclidTile.euclidTileFilters(serviceName, 102020553);
        assertTrue(filters.length >= 7);
        assertTrue(Arrays.asList(filters).contains("VIS"));
        String url =
            EuclidTile.euclidTileCutoutUrl(serviceName, 102020553, "VIS");
        if (testCutoutUrl) {
            assertTrue(url.equals(
                  "https://easotf.esac.esa.int/sas-cutout/cutout?"
                + "filepath=/data_staging_otf/repository_otf/F-006/"
                         + "MER/102020553/"
                + "VIS/EUC_MER_BGSUB-MOSAIC-VIS_TILE102020553"
                + "-1969C4_20240301T185204.814169Z_00.00.fits&"
                + "collection=VIS&tileindex=102020553"
            ));
        }
    }

    /**
     * Main method.
     *
     * @param  args  ignored
     */
    public static void main(String[] args) {
        test();
        System.out.println();
    }

    private static void assertTrue(boolean flag) {
        if (!flag) {
            throw new AssertionError("Test failed");
        }
        System.err.print(".");
    }
}
