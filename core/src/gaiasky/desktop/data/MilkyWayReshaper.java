/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.desktop.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files;
import com.badlogic.gdx.files.FileHandle;
import gaiasky.data.group.PointDataProvider;
import gaiasky.desktop.format.DesktopDateFormatFactory;
import gaiasky.desktop.format.DesktopNumberFormatFactory;
import gaiasky.interafce.ConsoleLogger;
import gaiasky.scenegraph.particle.IParticleRecord;
import gaiasky.util.I18n;
import gaiasky.util.Logger;
import gaiasky.util.Settings;
import gaiasky.util.SettingsManager;
import gaiasky.util.format.DateFormatFactory;
import gaiasky.util.format.NumberFormatFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class MilkyWayReshaper {
    private static final Logger.Log logger = Logger.getLogger(MilkyWayReshaper.class);

    static int[] moduluses = new int[] {10, 5, 2, 0, 0};
    static String f = "data/galaxy/";
    static String[] filesIn = new String[] { f + "galaxy_ArmDust.dat.gz", f + "galaxy_Bulge.dat.gz", f + "galaxy_Gas.dat.gz", f + "galaxy_HII.dat.gz", f + "galaxy_Stars.dat.gz" };
    static String[] filesOut = new String[] { f + "galaxy_ArmDust.dat", f + "galaxy_Bulge.dat", f + "galaxy_Gas.dat", f + "galaxy_HII.dat", f + "galaxy_Stars.dat" };

    public static void main(String[] args) {
        try {
            // Assets location
            String ASSETS_LOC = Settings.ASSETS_LOC + "/";

            Gdx.files = new Lwjgl3Files();

            // Add notification watch
            new ConsoleLogger();

            // Initialize number format
            NumberFormatFactory.initialize(new DesktopNumberFormatFactory());

            // Initialize date format
            DateFormatFactory.initialize(new DesktopDateFormatFactory());

            // Initialize i18n
            I18n.initialize(new FileHandle(ASSETS_LOC + "i18n/gsbundle"));

            // Initialize configuration
            File dummyv = new File(ASSETS_LOC + "data/dummyversion");
            if (!dummyv.exists()) {
                dummyv = new File(ASSETS_LOC + "dummyversion");
            }
            SettingsManager.initialize(new FileInputStream(ASSETS_LOC + "conf/global.properties"), new FileInputStream(dummyv));

            for(int ds = 0; ds < filesIn.length; ds++) {
                logger.info();
                int modulus = moduluses[ds];
                String fileIn = filesIn[ds];
                String fileOut = filesOut[ds];

                logger.info("Processing file: " + fileIn);

                // Load
                PointDataProvider provider = new PointDataProvider();
                List<IParticleRecord> particles = provider.loadData(fileIn);

                String out = Settings.settings.data.dataFile(fileOut);
                if (Files.exists(Paths.get(out))) {
                    logger.error("ERROR - Output file exists: " + out);
                    continue;
                }

                if (particles.size() > 0) {
                    FileWriter fw = new FileWriter(out);
                    int ntokens = particles.get(0).rawDoubleData().length;
                    if (ntokens == 3) {
                        // Position
                        fw.write("X Y Z\n");
                    } else if (ntokens == 4) {
                        // Position + size
                        fw.write("X Y Z size\n");
                    } else if (ntokens == 7) {
                        // Position + size + color
                        fw.write("X Y Z size r g b\n");
                    } else {
                        logger.error("ERROR - Incorrect number of fields: " + ntokens);
                        continue;
                    }
                    int particle = 0;
                    int added = 0;
                    for (IParticleRecord pb : particles) {
                        if (modulus == 0 || particle % modulus == 0) {
                            double[] d = pb.rawDoubleData();
                            for (int i = 0; i < d.length; i++) {
                                fw.write(d[i] + (i < d.length - 1 ? " " : ""));
                            }
                            fw.write("\n");
                            added++;
                        }
                        particle++;
                    }
                    fw.flush();
                    fw.close();
                    logger.info(I18n.txt("notif.written", added, out));
                } else {
                    logger.info("No particles in input file");
                }
            }

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            logger.error(e, sw.toString());
        }
    }
}
