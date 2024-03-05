package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

import java.util.ArrayList;
import java.util.Random;

public class BrainCanevas extends Brain {
    private boolean turnTask, turnRight, moveTask, berzerk, back, highway;
    private double endTaskDirection, lastSeenDirection, lastShot;
    private int endTaskCounter, berzerkInertia;
    private boolean firstMove, berzerkTurning;
    private static final double PRECISION = 0.001;

    public BrainCanevas() {
        super();
    }

    public void activate() {
        move();
    }

    public void step() {
        ArrayList<IRadarResult> radarResults = detectRadar();

        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.OpponentMainBot) {
                fire(r.getObjectDirection());
                return;
            }
        }

        if (seeAllies(radarResults)) {
            stepTurn(Parameters.Direction.LEFT);
            if (directionNorth()) {
                move();
            }
        }

        if (seeWall()) {
            stepTurn(Parameters.Direction.RIGHT);
            if (directionSouth()) {
                move();
            }
        }

        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.OpponentMainBot) {
                berzerk = true;
                back = (Math.cos(getHeading() - r.getObjectDirection()) > 0);
                endTaskCounter = 21;
                fire(r.getObjectDirection());
                lastSeenDirection = r.getObjectDirection();
                berzerkTurning = true;
                endTaskDirection = lastSeenDirection;
                adjustBerzerkTurning();
                return;
            }
        }

        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                fire(r.getObjectDirection());
                return;
            }
        }

        if (detectFront().getObjectType() == IFrontSensorResult.Types.NOTHING) {
            move();
        }
    }

    private boolean seeWall() {
        return (detectFront().getObjectType() == IFrontSensorResult.Types.WALL);
    }

    private boolean seeAllies(ArrayList<IRadarResult> detectedObjects) {
        for (IRadarResult res : detectedObjects) {
            if (res.getObjectType() == IRadarResult.Types.TeamMainBot ||
                    res.getObjectType() == IRadarResult.Types.TeamSecondaryBot) 
            {
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

    private void adjustBerzerkTurning() {
        double ref = endTaskDirection - getHeading();
        if (ref < 0) ref += Math.PI * 2;
        turnRight = (ref > 0 && ref < Math.PI);
    }
}
