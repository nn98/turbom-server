package com.nextstep.infra.geo;

import com.nextstep.domain.site.Coordinate;
import java.math.BigDecimal;
import java.util.Optional;

public final class KoreanTmCoordinateConverter {

    private static final double BESSEL_A = 6377397.155;
    private static final double BESSEL_INV_F = 299.1528128;
    private static final double BESSEL_E2 = eccentricitySquared(BESSEL_INV_F);
    private static final double BESSEL_EP2 = BESSEL_E2 / (1.0 - BESSEL_E2);

    private static final double WGS84_A = 6378137.0;
    private static final double WGS84_INV_F = 298.257223563;

    private static final double LATITUDE_OF_ORIGIN = Math.toRadians(38.0);
    private static final double CENTRAL_MERIDIAN = Math.toRadians(127.002890277778);
    private static final double FALSE_EASTING = 200000.0;
    private static final double FALSE_NORTHING = 500000.0;
    private static final double SCALE_FACTOR = 1.0;

    private static final double TOWGS84_DX = -145.907;
    private static final double TOWGS84_DY = 505.034;
    private static final double TOWGS84_DZ = 685.756;
    private static final double TOWGS84_RX = Math.toRadians(-1.162 / 3600.0);
    private static final double TOWGS84_RY = Math.toRadians(2.347 / 3600.0);
    private static final double TOWGS84_RZ = Math.toRadians(1.592 / 3600.0);
    private static final double TOWGS84_SCALE = 1.0 + 6.342e-6;

    private KoreanTmCoordinateConverter() {
    }

    public static Optional<Coordinate> fromEpsg5174(BigDecimal originalX, BigDecimal originalY) {
        if (originalX == null || originalY == null) return Optional.empty();

        double easting = originalX.doubleValue();
        double northing = originalY.doubleValue();
        if (!Double.isFinite(easting) || !Double.isFinite(northing)) return Optional.empty();

        Geodetic source = inverseTransverseMercator(easting, northing);
        Ecef bessel = toEcef(source.latitudeRadians(), source.longitudeRadians(), BESSEL_A, BESSEL_INV_F);
        Ecef wgs84 = toWgs84(bessel);
        Geodetic result = toGeodetic(wgs84, WGS84_A, WGS84_INV_F);

        return Optional.of(new Coordinate(
            Math.toDegrees(result.latitudeRadians()),
            Math.toDegrees(result.longitudeRadians())
        ));
    }

    private static Geodetic inverseTransverseMercator(double easting, double northing) {
        double meridionalOrigin = meridionalArc(LATITUDE_OF_ORIGIN);
        double meridionalDistance = meridionalOrigin + (northing - FALSE_NORTHING) / SCALE_FACTOR;
        double e1 = (1.0 - Math.sqrt(1.0 - BESSEL_E2)) / (1.0 + Math.sqrt(1.0 - BESSEL_E2));
        double mu = meridionalDistance / (BESSEL_A * (1.0 - BESSEL_E2 / 4.0
            - 3.0 * Math.pow(BESSEL_E2, 2.0) / 64.0
            - 5.0 * Math.pow(BESSEL_E2, 3.0) / 256.0));

        double footprintLatitude = mu
            + (3.0 * e1 / 2.0 - 27.0 * Math.pow(e1, 3.0) / 32.0) * Math.sin(2.0 * mu)
            + (21.0 * Math.pow(e1, 2.0) / 16.0 - 55.0 * Math.pow(e1, 4.0) / 32.0) * Math.sin(4.0 * mu)
            + (151.0 * Math.pow(e1, 3.0) / 96.0) * Math.sin(6.0 * mu)
            + (1097.0 * Math.pow(e1, 4.0) / 512.0) * Math.sin(8.0 * mu);

        double sin = Math.sin(footprintLatitude);
        double cos = Math.cos(footprintLatitude);
        double tan = Math.tan(footprintLatitude);
        double radiusPrimeVertical = BESSEL_A / Math.sqrt(1.0 - BESSEL_E2 * sin * sin);
        double radiusMeridian = BESSEL_A * (1.0 - BESSEL_E2) / Math.pow(1.0 - BESSEL_E2 * sin * sin, 1.5);
        double tanSquared = tan * tan;
        double c = BESSEL_EP2 * cos * cos;
        double d = (easting - FALSE_EASTING) / (radiusPrimeVertical * SCALE_FACTOR);

        double latitude = footprintLatitude - (radiusPrimeVertical * tan / radiusMeridian) * (
            d * d / 2.0
                - (5.0 + 3.0 * tanSquared + 10.0 * c - 4.0 * c * c - 9.0 * BESSEL_EP2) * Math.pow(d, 4.0) / 24.0
                + (61.0 + 90.0 * tanSquared + 298.0 * c + 45.0 * tanSquared * tanSquared
                    - 252.0 * BESSEL_EP2 - 3.0 * c * c) * Math.pow(d, 6.0) / 720.0
        );
        double longitude = CENTRAL_MERIDIAN + (
            d
                - (1.0 + 2.0 * tanSquared + c) * Math.pow(d, 3.0) / 6.0
                + (5.0 - 2.0 * c + 28.0 * tanSquared - 3.0 * c * c
                    + 8.0 * BESSEL_EP2 + 24.0 * tanSquared * tanSquared) * Math.pow(d, 5.0) / 120.0
        ) / cos;

        return new Geodetic(latitude, longitude);
    }

    private static double meridionalArc(double latitude) {
        double e4 = BESSEL_E2 * BESSEL_E2;
        double e6 = e4 * BESSEL_E2;
        return BESSEL_A * (
            (1.0 - BESSEL_E2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0) * latitude
                - (3.0 * BESSEL_E2 / 8.0 + 3.0 * e4 / 32.0 + 45.0 * e6 / 1024.0) * Math.sin(2.0 * latitude)
                + (15.0 * e4 / 256.0 + 45.0 * e6 / 1024.0) * Math.sin(4.0 * latitude)
                - (35.0 * e6 / 3072.0) * Math.sin(6.0 * latitude)
        );
    }

    private static Ecef toEcef(double latitude, double longitude, double semiMajor, double inverseFlattening) {
        double e2 = eccentricitySquared(inverseFlattening);
        double sinLat = Math.sin(latitude);
        double cosLat = Math.cos(latitude);
        double radiusPrimeVertical = semiMajor / Math.sqrt(1.0 - e2 * sinLat * sinLat);
        double x = radiusPrimeVertical * cosLat * Math.cos(longitude);
        double y = radiusPrimeVertical * cosLat * Math.sin(longitude);
        double z = radiusPrimeVertical * (1.0 - e2) * sinLat;
        return new Ecef(x, y, z);
    }

    private static Ecef toWgs84(Ecef source) {
        double x = TOWGS84_DX + TOWGS84_SCALE * source.x() - TOWGS84_RZ * source.y() + TOWGS84_RY * source.z();
        double y = TOWGS84_DY + TOWGS84_RZ * source.x() + TOWGS84_SCALE * source.y() - TOWGS84_RX * source.z();
        double z = TOWGS84_DZ - TOWGS84_RY * source.x() + TOWGS84_RX * source.y() + TOWGS84_SCALE * source.z();
        return new Ecef(x, y, z);
    }

    private static Geodetic toGeodetic(Ecef ecef, double semiMajor, double inverseFlattening) {
        double flattening = 1.0 / inverseFlattening;
        double semiMinor = semiMajor * (1.0 - flattening);
        double e2 = eccentricitySquared(inverseFlattening);
        double ep2 = (semiMajor * semiMajor - semiMinor * semiMinor) / (semiMinor * semiMinor);
        double p = Math.hypot(ecef.x(), ecef.y());
        double theta = Math.atan2(ecef.z() * semiMajor, p * semiMinor);

        double latitude = Math.atan2(
            ecef.z() + ep2 * semiMinor * Math.pow(Math.sin(theta), 3.0),
            p - e2 * semiMajor * Math.pow(Math.cos(theta), 3.0)
        );
        double longitude = Math.atan2(ecef.y(), ecef.x());

        for (int i = 0; i < 3; i++) {
            double radiusPrimeVertical = semiMajor / Math.sqrt(1.0 - e2 * Math.pow(Math.sin(latitude), 2.0));
            double height = p / Math.cos(latitude) - radiusPrimeVertical;
            latitude = Math.atan2(ecef.z(), p * (1.0 - e2 * radiusPrimeVertical / (radiusPrimeVertical + height)));
        }

        return new Geodetic(latitude, longitude);
    }

    private static double eccentricitySquared(double inverseFlattening) {
        double flattening = 1.0 / inverseFlattening;
        return 2.0 * flattening - flattening * flattening;
    }

    private record Geodetic(double latitudeRadians, double longitudeRadians) {
    }

    private record Ecef(double x, double y, double z) {
    }
}
