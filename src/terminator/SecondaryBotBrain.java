package terminator;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

import java.util.ArrayList;
import java.util.Random;

public class SecondaryBotBrain extends Brain {

    private static final double PRECISION = 0.01;
    private static final double FIREANGLEPRECISION = Math.PI / (double) 6;
    private static final double ANGLEPRECISION = 0.01;

    private STATE currentState;
    private Coordonnate myPosition;
    private boolean freeze;
    private double oldAngle;
    private SIDE botSide;
    private static ArrayList<Coordonnate> obstaclesList = new ArrayList<>();
    private botName whoAmI;
    private ArrayList<Coordonnate> pathToFollow;

    public SecondaryBotBrain() {
        super();
    }

    public void activate() {
        try {
            nominateBot();
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            e.printStackTrace();
        }
        currentState = STATE.MOVESTATE;
        oldAngle = myGetHeading();
    }

    public void step() {
        double myX = myPosition.getX() + Parameters.teamAMainBotSpeed * Math.cos(myGetHeading());
        double myY = myPosition.getY() + Parameters.teamAMainBotSpeed * Math.sin(myGetHeading());
        myPosition = new Coordonnate(myX, myY);
        ArrayList<IRadarResult> radarResults = detectRadar();
        freeze = false;

        if (getHealth() <= 0) {
            obstaclesList.add(myPosition);
            sendLogMessage("Wreck detected at " + myPosition);
            return;
        }

        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.OpponentMainBot
                    || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot || r.getObjectType() == IRadarResult.Types.BULLET) {
                moveBack();
                return;
            }
            if (r.getObjectDistance() <= 150 && !isRoughlySameDirection(r.getObjectDirection(), getHeading())
                    && r.getObjectType() != IRadarResult.Types.BULLET) {
                freeze = true;
            }
        }

        if (freeze) {
            return;
        }

        if (currentState == STATE.MOVESTATE) {
            if (detectFront().getObjectType() == IFrontSensorResult.Types.WALL) {
                currentState = STATE.TURNLEFTSTATE;
                oldAngle = myGetHeading();
                stepTurn(Parameters.Direction.LEFT);
                return;
            } else {
                move();
                return;
            }
        } else if (currentState == STATE.TURNRIGHTSTATE) {
            if (!(isSameDirection(getHeading(), oldAngle + Parameters.RIGHTTURNFULLANGLE))) {
                stepTurn(Parameters.Direction.RIGHT);
            } else {
                currentState = STATE.MOVESTATE;
                move();
                return;
            }
            return;
        } else if (currentState == STATE.TURNLEFTSTATE) {
            if (!(isSameDirection(getHeading(), oldAngle + Parameters.LEFTTURNFULLANGLE))) {
                stepTurn(Parameters.Direction.LEFT);
            } else {
                currentState = STATE.MOVESTATE;
                move();
                return;
            }
            return;
        } else if (currentState == STATE.FIRESTATE) {
            return;
        }
    }

    public void addObstacle(Coordonnate pos) {
        for (Coordonnate obtacle : obstaclesList) {
            if (Math.abs(obtacle.getX() - pos.getX()) < 1 && Math.abs(obtacle.getY() - pos.getY()) < 1)
                return;
        }
        obstaclesList.add(pos);
        int destinationX = botSide == SIDE.LEFT ? 2900 : 2000;
        int[] yCoordinates = { 600, 1200, 400, 1100, 1700 }; // y coordinates for the bots

        if (whoAmI == botName.ZORRO1) {
            pathToFollow = PathFinder.findPath(myPosition, new Coordonnate(destinationX,
                    yCoordinates[0]), obstaclesList);
        } else if (whoAmI == botName.ZORRO2) {
            pathToFollow = PathFinder.findPath(myPosition, new Coordonnate(destinationX,
                    yCoordinates[1]), obstaclesList);
        }

    }

    private void nominateBot()
            throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
        boolean up = false, down = false;
        for (IRadarResult r : detectRadar()) {
            up |= (r.getObjectDirection() == Parameters.NORTH);
            down |= (r.getObjectDirection() == Parameters.SOUTH);
        }
        botSide = getHeading() == Parameters.WEST ? SIDE.LEFT : SIDE.RIGHT;
        String botNumber = up ? "1" : down ? "2" : "";
        String prefix = botSide == SIDE.LEFT ? "teamASecondaryBot" : "teamBSecondaryBot";;
        double initX = Parameters.class.getField(prefix + botNumber + "InitX").getDouble(null)
                * Math.cos(myGetHeading());
        double initY = Parameters.class.getField(prefix + botNumber + "InitY").getDouble(null)
                * Math.sin(myGetHeading());

        whoAmI = botName.valueOf(prefix + botNumber);
        myPosition = new Coordonnate(initX, initY);
        sendLogMessage("I am " + whoAmI + " at " + myPosition);
    }

    private boolean seeWall() {
        return wallIsDetected(myPosition);
    }

    private boolean isRoughlySameDirection(double dir1, double dir2) {
        return Math.abs(normalizeRadian(dir1) - normalizeRadian(dir2)) < FIREANGLEPRECISION;
    }

    private boolean seeAllies(ArrayList<IRadarResult> detectedObjects) {
        for (IRadarResult res : detectedObjects) {
            if (res.getObjectType() == IRadarResult.Types.TeamMainBot ||
                    res.getObjectType() == IRadarResult.Types.TeamSecondaryBot) {
                return true;
            }
        }
        return false;
    }

    private boolean directionSouth() {
        return (Math.abs(getHeading() - Parameters.SOUTH) < PRECISION);
    }

    private boolean directionEast() {
        return (Math.abs(getHeading() - Parameters.EAST) < PRECISION);
    }

    private boolean directionNorth() {
        return (Math.abs(getHeading() - Parameters.NORTH) < PRECISION);
    }

    private double myGetHeading() {
        return normalizeRadian(getHeading());
    }

    private boolean isSameDirection(double dir1, double dir2) {
        return Math.abs(normalizeRadian(dir1) - normalizeRadian(dir2)) < ANGLEPRECISION;
    }

    private double normalizeRadian(double angle) {
        double result = angle;
        while (result < 0)
            result += 2 * Math.PI;
        while (result >= 2 * Math.PI)
            result -= 2 * Math.PI;
        return result;
    }

    public static boolean wallIsDetected(Coordonnate pos) {
        return (pos.x <= 100.0 || pos.x >= 3000.0 || pos.y <= 100.0 || pos.y >= 2000.0);
    }
}