package data.scripts.gearin;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;

public class GearInLogWriter {

    private static final Logger LOG = Global.getLogger(GearInLogWriter.class);

    private GearInLogWriter() {
    }

    public static void info(String stage, String objectId, String detail) {
        LOG.info(format(stage, objectId, detail));
    }

    public static void warn(String stage, String objectId, String detail) {
        LOG.warn(format(stage, objectId, detail));
    }

    public static void error(String stage, String objectId, String detail, Throwable cause) {
        LOG.error(format(stage, objectId, detail), cause);
    }

    private static String format(String stage, String objectId, String detail) {
        return "GearIn " + stage + ": object=" + objectId + " " + detail;
    }
}
