package terminator;

public class Coordonnate {
    public double x, y;

    public Coordonnate(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double distance(Coordonnate cc) {
        return Math.sqrt(Math.pow(cc.getX() - x, 2) + Math.pow(cc.getY() - y, 2));
    }

    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
