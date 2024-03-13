package hyperHeros;

class PolarCoordinate {

    private double angle;
    private double dist;

    public PolarCoordinate(double angle, double dist) {
        this.angle = angle;
        if (dist < 0) {
            throw new RuntimeException();
        }
        this.dist = dist;
    }

    public double getAngle() {
        return angle;
    }

    public void setAngle(double angle) {
        this.angle = angle;
    }

    public double getDist() {
        return dist;
    }

    public void setDist(double dist) {
        this.dist = dist;
    }

    @Override
    public String toString() {
        return "PolarCoordinate{" +
                "angle=" + angle +
                ", dist=" + dist +
                '}';
    }
}

public class CoordinateTransform {

    public CoordinateTransform() {
    }

    public static PolarCoordinate convertCartesianToPolar(Coordonnate origin, Coordonnate destination) {
        double deltaX = destination.getX() - origin.getX();
        double deltaY = destination.getY() - origin.getY();
        double angle = Math.atan2(deltaY, deltaX);
        double distance = origin.distance(destination);
        return new PolarCoordinate(angle, distance);
    }

    public static Coordonnate convertPolarToCartesian(Coordonnate origin, double angle, double distance) {
        double newX = calculateNewX(origin.getX(), angle, distance);
        double newY = calculateNewY(origin.getY(), angle, distance);
        return new Coordonnate(clampCoordinate(newX, 0, 3000), clampCoordinate(newY, 0, 2000));
    }

    public static boolean isOutOfBounds(Coordonnate coordinate) {
        return coordinate.getX() <= 100 || coordinate.getX() >= 3000 || coordinate.getY() <= 100
                || coordinate.getY() >= 2000;
    }

    private static double calculateNewX(double originX, double angle, double distance) {
        return originX + distance * Math.cos(angle);
    }

    private static double calculateNewY(double originY, double angle, double distance) {
        return originY + distance * Math.sin(angle);
    }

    private static double clampCoordinate(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}