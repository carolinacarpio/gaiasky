/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.desktop.util;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Produces a plot of ColorXP (BP-RP) vs log(tEff)).
 */
public class TEffPlot {

    public static void main(String[] args) {
        File f = new File("/tmp/teffcxp.dat");

        double startXp = -1.5;
        double endXp = 9.0;
        int nSteps = 100;
        double step = (endXp - startXp) / (double) nSteps;

        try {
            PrintWriter writer = new PrintWriter(f, StandardCharsets.UTF_8);
            writer.println("bp-rp,logteff,teff");

            double currXp = startXp;
            while (currXp < endXp) {

                double logTEff = 3.999 - 0.654 * currXp + 0.709 * currXp * currXp - 0.316 * currXp * currXp * currXp;
                double tEff = Math.pow(10, logTEff);
                writer.println(currXp + "," + logTEff + "," + tEff);
                currXp += step;
            }

            writer.close();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

}
