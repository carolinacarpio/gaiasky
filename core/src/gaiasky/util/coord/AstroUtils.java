/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util.coord;

import gaiasky.util.Constants;
import gaiasky.util.LruCache;
import gaiasky.util.Nature;
import gaiasky.util.coord.vsop87.VSOP87;
import gaiasky.util.coord.vsop87.iVSOP87;
import gaiasky.util.math.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;

/**
 * Some astronomical goodies to get the position of the Sun, Moon, work out
 * Julian dates, etc.
 */
public class AstroUtils {

    /**
     * Julian date of J2000 epoch (julian days since January 1, 4713 BCE)
     **/
    static final public double JD_J2000 = getJulianDate(2000.0);
    /**
     * Julian date of reference epoch J2015.0 = JD2455197.5 =
     * 2015-01-01T00:00:00
     **/
    static final public double JD_J2015 = getJulianDate(2015.0);

    /**
     * Julian date of the Gaia DR2 reference epoch, J2015.5 = JD2455197.5 =
     * 2015-01-01T00:00:00
     **/
    static final public double JD_J2015_5 = getJulianDate(2015.5);

    /**
     * Julian date of the variable stars' epoch: 2010-01-01T00:00:00
     **/
    static final public double JD_J2010 = getJulianDate(2010.0);

    /**
     * Milliseconds of J2000 in the scale of java.util.Date
     **/
    public static final long J2000_MS;

    /**
     * Get julian date from a double reference epoch
     *
     * @param refEpoch The reference epoch
     * @return The julian date
     */
    public static double getJulianDate(double refEpoch) {
        int year = (int) refEpoch;
        double part = refEpoch - year;
        return getJulianDate(year, 1, 1, 0, 0, 0, 0, true) + 365.25 * part;
    }

    static {
        Instant d = (LocalDateTime.of(2000, 1, 1, 0, 0, 0)).toInstant(ZoneOffset.UTC);
        J2000_MS = d.toEpochMilli();
    }

    /**
     * Julian date cache, since most dates are used more than once
     **/
    private static final LruCache<Long, Double> jdcache = new LruCache<>(50);

    /**
     * Initialize nsl Sun
     **/
    private static final NslSun nslSun = new NslSun();

    private static final Vector3d aux3 = new Vector3d();
    private static final Vector2d aux2 = new Vector2d();

    /**
     * Algorithm in "Astronomical Algorithms" book by Jean Meeus. Finds out the
     * distance from the Sun to the Earth in Km
     *
     * @param date The date
     * @return The distancee from the Sun to the Earth in Km
     */
    public static double getSunDistance(Instant date) {
        return getSunDistance(getJulianDateCache(date));
    }

    public static double getSunDistance(double jd) {
        double T = T(jd);
        double T2 = T * T;
        double T3 = T2 * T;
        double M = 357.5291 + 35999.0503 * T - 0.0001599 * T2 - 0.00000048 * T3;
        double e = 0.016708617 - 0.000042037 * T - 0.0000001236 * T2;
        double C = (1.9146 - 0.004817 * T - 0.000014 * T2) * Math.sin(Math.toRadians(M)) + (0.019993 - 0.000101 * T) * Math.sin(Math.toRadians(2.0 * M)) + 0.00029 * Math.sin(Math.toRadians(3 * M));
        double v = M + C;

        double R = (1.000001018 * (1 - e * e)) / (1 + e * Math.cos(Math.toRadians(v)));
        return R * Nature.AU_TO_KM;
    }

    private static Instant cacheSunLongitudeDate = Instant.now();
    private static double cacheSunLongitude;

    /**
     * Returns the Sun's ecliptic longitude in degrees for the given time.
     * Caches the last Sun's longitude for future use.
     *
     * @param date The time for which the longitude must be calculated
     * @return The Sun's longitude in [deg]
     */
    public static double getSunLongitude(Instant date) {
        if (!date.equals(cacheSunLongitudeDate)) {
            double julianDate = getJulianDateCache(date);

            nslSun.setTime(julianDate);
            double aux = Math.toDegrees(nslSun.getSolarLongitude()) % 360;

            cacheSunLongitudeDate = Instant.ofEpochMilli(date.toEpochMilli());
            cacheSunLongitude = aux % 360;
        }
        return cacheSunLongitude;
    }

    /**
     * Gets the ecliptic longitude of the Sun in degrees as published in
     * Wikipedia.
     *
     * @param jd The Julian date for which to calculate the latitude.
     * @return The ecliptic longitude of the Sun at the given Julian date, in
     * degrees.
     * @see <a href=
     * "http://en.wikipedia.org/wiki/Position_of_the_Sun">http://en.wikipedia.org/wiki/Position_of_the_Sun</a>
     */
    public static double getSunLongitudeWikipedia(double jd) {
        double n = jd - JD_J2000;
        double L = 280.460d + 0.9856474d * n;
        double g = 357.528d + 0.9856003d * n;
        double l = L + 1.915 * Math.sin(Math.toRadians(g)) + 0.02 * Math.sin(Math.toRadians(2.0 * g));
        return l;
    }

    /**
     * Algorithm in "Astronomical Algorithms" book by Jean Meeus. Returns a
     * vector with the equatorial longitude (&alpha;) in radians, the equatorial
     * latitude (&delta;) in radians and the distance in kilometers.
     *
     * @param date
     */
    public static void moonEquatorialCoordinates(Vector3d placeholder, Instant date) {
        moonEquatorialCoordinates(placeholder, getJulianDateCache(date));
    }

    /**
     * Algorithm in "Astronomical Algorithms" book by Jean Meeus. Returns a
     * vector with the equatorial longitude (&alpha;) in radians, the equatorial
     * latitude (&delta;) in radians and the distance in kilometers.
     *
     * @param julianDate
     */
    public static void moonEquatorialCoordinates(Vector3d placeholder, double julianDate) {
        moonEclipticCoordinates(julianDate, aux3);
        Vector2d equatorial = Coordinates.eclipticToEquatorial(aux3.x, aux3.y, aux2);
        placeholder.set(equatorial.x, equatorial.y, aux3.z);
    }

    /**
     * Algorithm in "Astronomical Algorithms" book by Jean Meeus. Returns a
     * vector with the ecliptic longitude (&lambda;) in radians, the ecliptic
     * latitude (&beta;) in radians and the distance in kilometers.
     *
     * @param date
     * @param out  The output vector.
     * @return The output vector, for chaining.
     */
    public static Vector3d moonEclipticCoordinates(Instant date, Vector3d out) {
        return moonEclipticCoordinates(getJulianDateCache(date), out);
    }
    /**
     * Algorithm in "Astronomical Algorithms" book by Jean Meeus. Returns a
     * vector with the ecliptic longitude (&lambda;) in radians, the ecliptic
     * latitude (&beta;) in radians and the distance in kilometers.
     *
     * @param date
     * @param out  The output vector.
     * @return The output vector, for chaining.
     */
    public static Vector3b moonEclipticCoordinates(Instant date, Vector3b out) {
        Vector3d outd = new Vector3d();
        moonEclipticCoordinates(getJulianDateCache(date), outd);
        return out.set(outd);
    }

    /**
     * Algorithm in "Astronomical Algorithms" book by Jean Meeus. Returns a
     * vector with the ecliptic longitude (&lambda;) in radians, the ecliptic
     * latitude (&beta;) in radians and the distance between the centers of the
     * Earth and the Moon in kilometers.
     *
     * @param julianDate
     * @param out        The output vector with geocentric longitude (lambda) [rad],
     *                   geocentric latitude (beta) [rad], distance between the centers
     *                   of the Earth and the Moon [km]
     * @return The output vector, for chaining.
     */
    public static Vector3d moonEclipticCoordinates(double julianDate, Vector3d out) {

        // Time T measured in Julian centuries from the Epoch J2000.0
        double T = T(julianDate);
        double T2 = T * T;
        double T3 = T2 * T;
        double T4 = T3 * T;
        // Moon's mean longitude, referred to the mean equinox of the date
        double Lp = 218.3164477 + 481267.88123421 * T - 0.0015786 * T2 + T3 / 538841.0 - T4 / 65194000.0;
        Lp = prettyAngle(Lp);
        // Mean elongation of the Moon
        double D = 297.8501921 + 445267.1114034 * T - 0.0018819 * T2 + T3 / 545868.0 - T4 / 113065000.0;
        D = prettyAngle(D);
        // Sun's mean anomaly
        double M = 357.5291092 + 35999.0502909 * T - 0.0001536 * T2 + T3 / 24490000.0;
        M = prettyAngle(M);
        // Moon's mean anomaly
        double Mp = 134.9633964 + 477198.8675055 * T + 0.0087414 * T2 + T3 / 69699.0 - T4 / 14712000.0;
        Mp = prettyAngle(Mp);
        // Moon's argument of latitude (mean distance of the Moon from its
        // ascending node)
        double F = 93.272095 + 483202.0175233 * T - 0.0036539 * T2 - T3 / 3526000.0 + T4 / 863310000.0;
        F = prettyAngle(F);
        // Three further arguments (again, in degrees) are needed
        double A1 = 119.75 + 131.849 * T;
        A1 = prettyAngle(A1);
        double A2 = 53.09 + 479264.290 * T;
        A2 = prettyAngle(A2);
        double A3 = 313.45 + 481266.484 * T;
        A3 = prettyAngle(A3);

        // Multiply by E the arguments that contain M or -M, multiply by E2 the
        // arguments that contain 2M or -2M
        double E = 1.0 - 0.002516 * T - 0.0000074 * T2;

        double[] aux = calculateSumlSumr(D, M, Mp, F, E, A1, A2, Lp);
        double suml = aux[0];
        double sumr = aux[1];
        double sumb = calculateSumb(D, M, Mp, F, E, A1, A3, Lp);

        double lambda = prettyAngle(Lp + suml * 0.000001);
        double beta = prettyAngle((sumb * 0.000001));
        double dist = 385000.56 + sumr * 0.001;

        return out.set(Math.toRadians(lambda), Math.toRadians(beta), dist);
    }


    /**
     * Ecliptic coordinates of pluto at the given date
     *
     * @param date The date
     * @param out  The out vector
     * @return Ecliptic coordinates of Pluto at the given julian date
     */
    public static Vector3b plutoEclipticCoordinates(Instant date, Vector3b out) {
        if (!Constants.withinVSOPTime(date.toEpochMilli()))
            return null;
        return plutoEclipticCoordinates(getDaysSinceJ2000(date), out);
    }

    /**
     * Ecliptic coordinates of pluto at the given date. See
     * http://www.stjarnhimlen.se/comp/ppcomp.html
     *
     * @param d   Julian date
     * @param out The out vector
     * @return Ecliptic coordinates of Pluto at the given julian date
     */
    private static Vector3b plutoEclipticCoordinates(double d, Vector3b out) {
        ITrigonometry trigo = MathManager.instance.trigonometryInterface;

        double S = Math.toRadians(50.03 + 0.033459652 * d);
        double P = Math.toRadians(238.95 + 0.003968789 * d);

        double lonecl = 238.9508 + 0.00400703 * d - 19.799 * trigo.sin(P) + 19.848 * trigo.cos(P) + 0.897 * trigo.sin(2.0 * P) - 4.956 * trigo.cos(2.0 * P) + 0.610 * trigo.sin(3.0 * P) + 1.211 * trigo.cos(3.0 * P) - 0.341 * trigo.sin(4.0 * P) - 0.190 * trigo.cos(4.0 * P) + 0.128 * trigo.sin(5.0 * P) - 0.034 * trigo.cos(5.0 * P) - 0.038 * trigo.sin(6.0 * P) + 0.031 * trigo.cos(6.0 * P) + 0.020 * trigo.sin(S - P) - 0.010 * trigo.cos(S - P);

        double latecl = -3.9082 - 5.453 * trigo.sin(P) - 14.975 * trigo.cos(P) + 3.527 * trigo.sin(2.0 * P) + 1.673 * trigo.cos(2.0 * P) - 1.051 * trigo.sin(3.0 * P) + 0.328 * trigo.cos(3.0 * P) + 0.179 * trigo.sin(4.0 * P) - 0.292 * trigo.cos(4.0 * P) + 0.019 * trigo.sin(5.0 * P) + 0.100 * trigo.cos(5.0 * P) - 0.031 * trigo.sin(6.0 * P) - 0.026 * trigo.cos(6.0 * P) + 0.011 * trigo.cos(S - P);

        double r = 40.72 + 6.68 * trigo.sin(P) + 6.90 * trigo.cos(P) - 1.18 * trigo.sin(2.0 * P) - 0.03 * trigo.cos(2.0 * P) + 0.15 * trigo.sin(3.0 * P) - 0.14 * trigo.cos(3.0 * P);

        //Apfloat radius = new Apfloat(r, Constants.PREC).multiply(new Apfloat(Nature.AU_TO_KM, Constants.PREC)).multiply(new Apfloat(5000000d, Constants.PREC));
        //return out.set(new Apfloat(Math.toRadians(lonecl), Constants.PREC), new Apfloat(Math.toRadians(latecl), Constants.PREC), radius);
        return out.set(Math.toRadians(lonecl), Math.toRadians(latecl), Nature.AU_TO_KM * r);
    }

    /**
     * Calculates the longitude Sum(l) and distance Sum(r) of the Moon using the
     * table.
     *
     * @param D  Mean elongation of the Moon
     * @param M  Sun's mean anomaly
     * @param Mp Moon's mean anomaly
     * @param F  Moon's argument of latitude (mean distance of the Moon from
     *           its ascending node)
     * @param E  Factor for eccentricity of Earth's orbit around the Sun
     * @param A1 Term due to action of Venus
     * @param A2 Term due to Jupiter
     * @param Lp Moon's mean longitude, referring to the equinox of the date
     * @return Suml and Sumr
     */
    private static double[] calculateSumlSumr(double D, double M, double Mp, double F, double E, double A1, double A2, double Lp) {
        ITrigonometry trigo = MathManager.instance.trigonometryInterface;

        double suml = 0.0, sumr = 0.0;
        for (int i = 0; i < table47a.length; i++) {
            double[] curr = table47a[i];
            // Take into effect terms that contain M and thus depend on the
            // eccentricity of the Earth's orbit around the
            // Sun, which presently is decreasing with time.
            double mul = 1.0;
            if (curr[1] == 1.0 || curr[1] == -1.0) {
                mul = E;
            } else if (curr[1] == 2.0 || curr[1] == -2.0) {
                mul = E * E;
            }
            double argument = Math.toRadians(curr[0] * D + curr[1] * M + curr[2] * Mp + curr[3] * F);
            suml += curr[4] * mul * trigo.sin(argument);
            sumr += curr[5] * mul * trigo.cos(argument);
        }
        // Addition to Suml. The terms involving A1 are due to the action of
        // Venus. The term involving A2 is due to Jupiter
        // while those involving L' are due to the flattening of the Earth.
        double sumladd = 3958.0 * trigo.sin(Math.toRadians(A1)) + 1962.0 * trigo.sin(Math.toRadians(Lp - F)) + 318.0 * trigo.sin(Math.toRadians(A2));
        suml += sumladd;

        return new double[] { suml, sumr };

    }

    private static double calculateSumb(double D, double M, double Mp, double F, double E, double A1, double A3, double Lp) {
        ITrigonometry trigo = MathManager.instance.trigonometryInterface;

        double sumb = 0.0;
        for (int i = 0; i < table47b.length; i++) {
            double[] curr = table47b[i];
            // Take into effect terms that contain M and thus depend on the
            // eccentricity of the Earth's orbit around the
            // Sun, which presently is decreasing with time.
            double mul = 1.0;
            if (curr[1] == 1.0 || curr[1] == -1.0) {
                mul = E;
            } else if (curr[1] == 2.0 || curr[1] == -2.0) {
                mul = E * E;
            }
            sumb += curr[4] * mul * trigo.sin(Math.toRadians(curr[0] * D + curr[1] * M + curr[2] * Mp + curr[3] * F));
        }
        // Addition to Sumb. The terms involving A1 are due to the action of
        // Venus. The term involving A2 is due to Jupiter
        // while those involing L' are due to the flattening of the Earth.
        double sumbadd = -2235.0 * trigo.sin(Math.toRadians(Lp)) + 382.0 * trigo.sin(Math.toRadians(A3)) + 175.0 * trigo.sin(Math.toRadians(A1 - F)) + 175.0 * trigo.sin(Math.toRadians(A1 + F)) + 127.0 * trigo.sin(Math.toRadians(Lp - Mp)) - 115.0 * trigo.sin(Math.toRadians(Lp + Mp));
        sumb += sumbadd;

        return sumb;
    }

    /**
     * Periodic terms for the longitude (Sum(l)) and distance (Sum(r)) of the
     * Moon. The unit is 0.000001 degree for Sum(l), and 0.001 km for Sum(r).
     * Multiple of D M M' F CoeffSine CoeffCosine
     */
    private static final double[][] table47a = { { 0, 0, 1, 0, 6288774.0, -20905355 }, { 2, 0, -1, 0, 1274027, -3699111 }, { 2, 0, 0, 0, 658314, -2955968 }, { 0, 0, 2, 0, 213618, -569925 }, { 0, 1, 0, 0, -185116, 48888 }, { 0, 0, 0, 2, -114332, -3149 }, { 2, 0, -2, 0, 58793, 246158 }, { 2, -1, -1, 0, 57066.0, -152138 }, { 2, 0, 1, 0, 53322, -170733 }, { 2, -1, 0, 0, 45758, -204586 }, { 0, 1, -1, 0, -40923, -129620 }, { 1, 0, 0, 0, -34720, 108743 }, { 0, 1, 1, 0, -30383, 104755 },
            { 2, 0, 0, -2, 15327, 10321 }, { 0, 0, 1, 2, -12528, 0 }, { 0, 0, 1, -2, 10980, 79661 }, { 4, 0, -1, 0, 10675, -34782 }, { 0, 0, 3, 0, 10034, -23210 }, { 4, 0, -2, 0, 8548, -21636 }, { 2, 1, -1, 0, -7888, 24208 }, { 2, 1, 0, 0, -6766, 30824 }, { 1, 0, -1, 0, -5163, -8379 }, { 1, 1, 0, 0, 4987, -16675 }, { 2, -1, 1, 0, 4036, -12831 }, { 2, 0, 2, 0, 3994, -10445 }, { 4, 0, 0, 0, 3861, -11650 }, { 2, 0, -3, 0, 3665, 14403 }, { 0, 1, -2, 0, -2689, -7003 }, { 2, 0, -1, 2, -2602, 0 },
            { 2, -1, -2, 0, 2390, 10056 }, { 1, 0, 1, 0, -2348, 6322 }, { 2, -2, 0, 0, 2236, -9884 }, { 0, 1, 2, 0, -2120, 5751 }, { 0, 2, 0, 0, -2069, 0 }, { 2, -2, -1, 0, 2048, -4950 }, { 2, 0, 1, -2, -1773, 4130 }, { 2, 0, 0, 2, -1595, 0 }, { 4, -1, -1, 0, 1215, -3958 }, { 0, 0, 2, 2, -1110, 0 }, { 3, 0, -1, 0, -892, 3258 }, { 2, 1, 1, 0, -810, 2616 }, { 4, -1, -2, 0, 759, -1897 }, { 0, 2, -1, 0, -713, -2117 }, { 2, 2, -1, 0, -700, 2354 }, { 2, 1, -2, 0, 691, 0 }, { 2, -1, 0, -2, 596, 0 },
            { 4, 0, 1, 0, 549, -1423 }, { 0, 0, 4, 0, 537, -1117 }, { 4, -1, 0, 0, 520, -1571 }, { 1, 0, -2, 0, -487, -1739 }, { 2, 1, 0, -2, -399, 0 }, { 0, 0, 2, -2, -381, -4421 }, { 1, 1, 1, 0, 351, 0 }, { 3, 0, -2, 0, -340, 0 }, { 4, 0, -3, 0, 330, 0 }, { 2, -1, 2, 0, 327, 0 }, { 0, 2, 1, 0, -323, 1165 }, { 1, 1, -1, 0, 299, 0 }, { 2, 0, 3, 0, 294, 0 }, { 2, 0, -1, -2, 0, 8752 } };

    /**
     * Periodic terms for the latitude of the Moon (Sum(b)). The unit is
     * 0.000001 degree. Multiple of D M M' F Coefficient of the sine of the
     * argument
     */
    private static final double[][] table47b = { { 0, 0, 0, 1, 5128122 }, { 0, 0, 1, 1, 280602 }, { 0, 0, 1, -1, 277693 }, { 2, 0, 0, -1, 173237 }, { 2, 0, -1, 1, 55413 }, { 2, 0, -1, -1, 46271 }, { 2, 0, 0, 1, 32573 }, { 0, 0, 2, 1, 17198 }, { 2, 0, 1, -1, 9266 }, { 0, 0, 2, -1, 8822 }, { 2, -1, 0, -1, 8216 }, { 2, 0, -2, -1, 4324 }, { 2, 0, 1, 1, 4200 }, { 2, 1, 0, -1, -3359 }, { 2, -1, -1, 1, 2463 }, { 2, -1, 0, 1, 2211 }, { 2, -1, -1, -1, 2065 }, { 0, 1, -1, -1, -1870 },
            { 4, 0, -1, -1, 1828 }, { 0, 1, 0, 1, -1794 }, { 0, 0, 0, 3, -1749 }, { 0, 1, -1, 1, -1565 }, { 1, 0, 0, 1, -1491 }, { 0, 1, 1, 1, -1475 }, { 0, 1, 1, -1, -1410 }, { 0, 1, 0, -1, -1344 }, { 1, 0, 0, -1, -1335 }, { 0, 0, 3, 1, 1107 }, { 4, 0, 0, -1, 1021 }, { 4, 0, -1, 1, 833 }, { 0, 0, 1, -3, 777 }, { 4, 0, -2, 1, 671 }, { 2, 0, 0, -3, 607 }, { 2, 0, 2, -1, 596 }, { 2, -1, 1, -1, 491 }, { 2, 0, -2, 1, -451 }, { 0, 0, 3, -1, 439 }, { 2, 0, 2, 1, 422 }, { 2, 0, -3, -1, 421 },
            { 2, 1, -1, 1, -366 }, { 2, 1, 0, 1, -351 }, { 4, 0, 0, 1, 331 }, { 2, -1, 1, 1, 315 }, { 2, -2, 0, -1, 302 }, { 0, 0, 1, 3, -283 }, { 2, 1, 1, -1, -229 }, { 1, 1, 0, -1, 223 }, { 1, 1, 0, 1, 223 }, { 0, 1, -2, -1, -220 }, { 2, 1, -1, -1, -220 }, { 1, 0, 1, 1, -185 }, { 2, -1, -2, -1, 181 }, { 0, 1, 2, 1, -177 }, { 4, 0, -2, -1, 176 }, { 4, -1, -1, -1, 166 }, { 1, 0, 1, -1, -164 }, { 4, 0, 1, -1, 132 }, { 1, 0, -1, -1, -119 }, { 4, -1, 0, -1, 115 }, { 2, -2, 0, 1, 107 } };

    /**
     * Returns a vector with the heliocentric ecliptic latitude and longitude in
     * radians and the distance in internal units.
     *
     * @param body         The body.
     * @param instant      The date to get the position.
     * @param out          The output vector
     * @param highAccuracy Whether to use the full precision algorithms or skip some
     *                     terms for speed
     * @return The output vector with L, B and R, for chaining.
     */
    public static Vector3b getEclipticCoordinates(String body, Instant instant, Vector3b out, boolean highAccuracy) {

        switch (body) {
        case "Moon":
            return new MoonAACoordinates().getEclipticSphericalCoordinates(instant, out);
        case "Pluto":
            return new PlutoCoordinates().getEclipticSphericalCoordinates(instant, out);
        default:
            iVSOP87 coor = VSOP87.instance.getVOSP87(body);
            coor.setHighAccuracy(highAccuracy);
            coor.getEclipticSphericalCoordinates(instant, out);
            return out;
        }
    }

    private static double prettyAngle(double angle) {
        return angle - 360d * (int) (angle / 360d);
    }

    /**
     * Gets the Julian date number given the Gregorian calendar quantities.
     *
     * @param gregorian Whether to use the Gregorian or the Julian calendar
     * @return The julian date number
     */
    public static double getJulianDate(int year, int month, int day, int hour, int min, int sec, int nanos, boolean gregorian) {
        if (gregorian) {
            return getJulianDayNumberWikipediaGregorianCalendar(year, month, day) + getDayFraction(hour, min, sec, nanos);
        } else {
            return getJulianDayNumberWikipediaJulianCalendar(year, month, day) + getDayFraction(hour, min, sec, nanos);
        }
    }

    /**
     * Gets the Julian Date for the given date. It uses a cache.
     *
     * @param instant The date.
     * @return The Julian Date.
     */
    public static synchronized double getJulianDateCache(Instant instant) {
        long time = instant.toEpochMilli();
        if (jdcache.containsKey(time)) {
            return jdcache.get(time);
        } else {
            Double jd = getJulianDate(instant);
            jdcache.put(time, jd);
            return jd;
        }
    }

    public static double getJulianDate(Instant instant) {
        LocalDateTime date = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        int year = date.get(ChronoField.YEAR);
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        int hour = date.getHour();
        int min = date.getMinute();
        int sec = date.getSecond();
        int nanos = date.get(ChronoField.NANO_OF_SECOND);
        return getJulianDate(year, month, day, hour, min, sec, nanos, true);
    }

    /**
     * Returns the elapsed milliseconds since the epoch J2010 until the given
     * date. Can be negavite.
     *
     * @param date The date
     * @return The elapsed milliseconds
     */
    public static double getMsSinceJ2010(Instant date) {
        return (getJulianDateCache(date) - JD_J2010) * Nature.D_TO_MS;
    }

    /**
     * Returns the elapsed milliseconds since the epoch J2000 until the given
     * date. Can be negavite.
     *
     * @param date The date
     * @return The elapsed milliseconds
     */
    public static double getMsSinceJ2000(Instant date) {
        return (getJulianDateCache(date) - JD_J2000) * Nature.D_TO_MS;
    }

    /**
     * Returns the elapsed days since the epoch J2000 until the given date. Can
     * be negavite.
     *
     * @param date The date
     * @return The elapsed days
     */
    public static double getDaysSinceJ2000(Instant date) {
        return getJulianDateCache(date) - JD_J2000;
    }

    /**
     * Returns the elapsed milliseconds since the epoch J2015 until the given
     * date. Can be negavite.
     *
     * @param date The date
     * @return The elapsed milliseconds
     */
    public static double getMsSinceJ2015(Instant date) {
        return (getJulianDateCache(date) - JD_J2015) * Nature.D_TO_MS;
    }

    /**
     * Returns the elapsed milliseconds since the given julian date jd until the
     * given date. Can be negative
     *
     * @param date     The date
     * @param epoch_jd The reference epoch in julian days
     * @return The elapsed milliseconds
     */
    public static double getMsSince(Instant date, double epoch_jd) {
        return (getJulianDateCache(date) - epoch_jd) * Nature.D_TO_MS;
    }

    /**
     * Returns the elapsed days since the given julian date jd until the
     * given date. Can be negative
     *
     * @param date     The date
     * @param epoch_jd The reference epoch in julian days
     * @return The elapsed days
     */
    public static double getDaysSince(Instant date, double epoch_jd) {
        return (getJulianDateCache(date) - epoch_jd);
    }

    /**
     * Gets the Gregorian calendar quantities given the Julian date.
     *
     * @param julianDate The Julian date
     * @return Vector with {year, month, day, hour, min, sec, nanos}
     */
    public static long[] getCalendarDay(double julianDate) {
        /**
         * y 4716 v 3 j 1401 u 5 m 2 s 153 n 12 w 2 r 4 B 274277 p 1461 C −38
         *
         * 1. f = J + j + (((4 * J + B)/146097) * 3)/4 + C 2. e = r * f + v 3. g
         * = mod(e, p)/r 4. h = u * g + w 5. D = (mod(h, s))/u + 1 6. M =
         * mod(h/s + m, n) + 1 7. Y = e/p - y + (n + m - M)/n
         */

        // J is the julian date number
        long J = (int) julianDate;
        long y = 4716l, j = 1401l, m = 2l, n = 12l, r = 4l, p = 1461l, v = 3l, u = 5l, s = 153l, w = 2l, B = 274277l, C = -38l;
        long f = J + j + (((4l * J + B) / 146097l) * 3l) / 4l + C;
        long e = r * f + v;
        long g = (e % p) / r;
        long h = u * g + w;
        long D = (h % s) / u + 1;
        long M = ((h / s + m) % n) + 1;
        long Y = e / p - y + (n + m - M) / n;

        double dayFraction = julianDate - J;
        long[] df = getDayQuantities(dayFraction);

        return new long[] { Y, M, D, df[0], df[1], df[2], df[3] };

    }

    /**
     * Returns the Julian day number. Uses the method shown in "Astronomical
     * Algorithms" by Jean Meeus.
     *
     * @param year  The year
     * @param month The month in [1:12]
     * @param day   The day in the month, starting at 1
     * @return The Julian date
     * @deprecated This does not work well!
     */
    @Deprecated
    public static double getJulianDayNumberBook(int year, int month, int day) {
        int a = year / 100;
        int b = 2 - a + (a / 4);

        // Julian day
        return (int) (365.242 * (year + 4716)) + (int) (30.6001 * (month)) + day + b - 1524.5d;
    }

    /**
     * Returns the Julian day number of a date in the Gregorian calendar. Uses
     * Wikipedia's algorithm.
     *
     * @param year  The year
     * @param month The month in [1:12]
     * @param day   The day in the month, starting at 1
     * @return The Julian date
     * @see <a href=
     * "http://en.wikipedia.org/wiki/Julian_day">http://en.wikipedia.org/wiki/Julian_day</a>
     */
    public static double getJulianDayNumberWikipediaGregorianCalendar(int year, int month, int day) {
        long a = (14l - month) / 12l;
        long y = year + 4800l - a;
        long m = month + 12l * a - 3l;

        return day + ((153l * m + 2l) / 5l) + 365l * y + (y / 4l) - (y / 100l) + (y / 400l) - 32045.5d;
    }

    /**
     * Returns the Julian day number of a date in the Julian calendar. Uses
     * Wikipedia's algorithm.
     *
     * @param year  The year
     * @param month The month in [1:12]
     * @param day   The day in the month, starting at 1
     * @return The Julian date
     * @see <a href=
     * "http://en.wikipedia.org/wiki/Julian_day">http://en.wikipedia.org/wiki/Julian_day</a>
     */
    public static double getJulianDayNumberWikipediaJulianCalendar(int year, int month, int day) {
        long a = (14l - month) / 12l;
        long y = year + 4800l - a;
        long m = month + 12l * a - 3l;

        return day + ((153l * m + 2l) / 5l) + 365l * y + (y / 4l) - 32083.5d;
    }

    public static Instant julianDateToInstant(double jd) {
        long[] cd = getCalendarDay(jd);
        LocalDateTime ldt = LocalDateTime.of((int) cd[0], (int) cd[1], (int) cd[2], (int) cd[3], (int) cd[4], (int) cd[5], (int) cd[6]);
        return ldt.toInstant(ZoneOffset.UTC);
    }

    /**
     * Gets the day fraction from the day quantities
     *
     * @param hour  The hour in 0-24
     * @param min   The minute in 0-1440
     * @param sec   The second in 0-86400
     * @param nanos The nanoseconds
     * @return The day fraction
     */
    public static double getDayFraction(int hour, int min, int sec, int nanos) {
        return hour / 24.0 + min / 1440.0 + (sec + nanos / 1.0E9) / 86400.0;
    }

    /**
     * Gets the day quantities from the day fraction
     *
     * @param dayFraction
     * @return [hours, minutes, seconds, nanos]
     */
    public static long[] getDayQuantities(double dayFraction) {
        double hourf = dayFraction * 24.0;
        double minf = (hourf - (long) hourf) * 60.0;
        double secf = (minf - (long) minf) * 60.0;
        double nanosf = (secf - (long) secf) * 1.0E9;
        return new long[] { (long) hourf, (long) minf, (long) secf, (long) nanosf };
    }

    /**
     * Returns the obliquity of the ecliptic (inclination of the Earth's axis of
     * rotation) for a given date, in degrees
     *
     * @return The obliquity in degrees
     */
    public static double obliquity(double julianDate) {
        // JPL's fundamental ephemerides have been continually updated. The
        // Astronomical Almanac for 2010 specifies:
        // E = 23° 26′ 21″.406 − 46″.836769 T − 0″.0001831 T2 + 0″.00200340 T3 −
        // 0″.576×10−6 T4 − 4″.34×10−8 T5
        double T = T(julianDate);
        /*
         * double T2 = T * T; double T3 = T2 * T; double T4 = T3 * T; double T5
         * = T4 * T; double todeg = 1.0 / 3600.0; return 23.0 + 26.0 / 60.0 +
         * 21.406 * todeg - 46.836769 * todeg * T - 0.0001831 * todeg * T2 +
         * 0.00200340 * todeg * T3 - 0.576e-6 * todeg * T4 - 4.34e-8 * todeg *
         * T5;
         */
        return 23.0 + 26.0 / 60.0 + (21.406 - (46.836769 - (0.0001831 + (0.00200340 - (0.576e-6 - 4.34e-8 * T) * T) * T) * T) * T) / 3600.0;
    }

    /**
     * Time T measured in Julian centuries from the Epoch J2000.0
     *
     * @param julianDate The julian date
     * @return The time in julian centuries
     */
    public static double T(double julianDate) {
        return (julianDate - 2451545.0) / 36525.0;
    }

    public static double tau(double julianDate) {
        return (julianDate - 2451545.0) / 365250.0;
    }

    /**
     * Converts proper motions + radial velocity into a cartesian vector.
     * See <a href="http://www.astronexus.com/a-a/motions-long-term">this article</a>.
     *
     * @param mualphastar Mu alpha star, in mas/yr
     * @param mudelta     Mu delta, in mas/yr
     * @param radvel      Radial velocity in km/s
     * @param ra          Right ascension in radians
     * @param dec         Declination in radians
     * @param distpc      Distance in parsecs to the star
     * @return The proper motion vector in internal_units/year
     */
    public static Vector3d properMotionsToCartesian(double mualphastar, double mudelta, double radvel, double ra, double dec, double distpc, Vector3d out) {
        double ma = mualphastar * Nature.MILLIARCSEC_TO_ARCSEC;
        double md = mudelta * Nature.MILLIARCSEC_TO_ARCSEC;

        // Multiply arcsec/yr with distance in parsecs gives a linear velocity. The factor 4.74 converts result to km/s
        double vta = ma * distpc * 4.74d;
        double vtd = md * distpc * 4.74d;

        double cosalpha = Math.cos(ra);
        double sinalpha = Math.sin(ra);
        double cosdelta = Math.cos(dec);
        double sindelta = Math.sin(dec);

        // +x to delta=0, alpha=0
        // +y to delta=0, alpha=90
        // +z to delta=90
        // components in km/s

        /*
         * vx = (vR cos \delta cos \alpha) - (vTA sin \alpha) - (vTD sin \delta cos \alpha)
         * vy = (vR cos \delta sin \alpha) + (vTA cos \alpha) - (vTD sin \delta sin \alpha)
         * vz = vR sin \delta + vTD cos \delta
         */
        double vx = (radvel * cosdelta * cosalpha) - (vta * sinalpha) - (vtd * sindelta * cosalpha);
        double vy = (radvel * cosdelta * sinalpha) + (vta * cosalpha) - (vtd * sindelta * sinalpha);
        double vz = (radvel * sindelta) + (vtd * cosdelta);

        return (out.set(vy, vz, vx)).scl(Constants.KM_TO_U / Nature.S_TO_Y);

    }
}
