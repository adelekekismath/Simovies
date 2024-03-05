package characteristics;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

import java.util.ArrayList;

public class Secondary extends Brain {
    // PARAMETERS
    private static final double HEADINGPRECISION = 0.001;
    private static final double ANGLEPRECISION = 0.1;

    private static final int ROCKY = 0x1EADDA;
    private static final int MARIO = 0x5EC0;
    private static final int UNDEFINED = 0xBADC0DE0;

    private static final int TURNNORTHTASK = 1;
    private static final int TURNSOUTHTASK = 8;
    private static final int MOVENORTHTASK = 2;
    private static final int MOVESOUTHTASK = 9;
    private static final int TURNEASTTASK = 3;
    private static final int TURNWESTTASK = 10;
    private static final int MOVEEASTTASK = 4;
    private static final int UTURNTASK = 5;
    private static final int MOVEWESTTASK = 6;
    private static final int AVOIDOBSTACLETASK = 7;
    private static final int SINK = 0xBADC0DE1;
    private int compteur = 0;
    private static int compteurId = 0;

    // VARIABLES
    private int state;
    private double myX, myY;
    private boolean isMoving;
    private int whoAmI;
    private double width;
    private int id;

    // CONSTRUCTORS
    public Secondary() {
        super();
        id = compteurId;
        compteurId++;

    }

    // ABSTRACT METHODS IMPLEMENTATION
    public void activate() {
        // ODOMETRY CODE
        whoAmI = (id == 0) ? MARIO : ROCKY;
        myX = Parameters.teamASecondaryBot1InitX;
        myY = Parameters.teamASecondaryBot1InitY;

        // INIT
        state = TURNSOUTHTASK;
        isMoving = true;
    }

    public void step() {
        // ODOMETRY CODE
        if (isMoving && whoAmI == ROCKY || whoAmI == MARIO) {
            myX += Parameters.teamASecondaryBotSpeed * Math.cos(getHeading());
            myY += Parameters.teamASecondaryBotSpeed * Math.sin(getHeading());
            isMoving = false;
        }

        // DEBUG MESSAGE
        if (whoAmI == ROCKY && state != SINK) {
            sendLogMessage("#ROCKY *thinks* he is rolling at position (" + (int) myX + ", " + (int) myY + ").");
        }
        if (whoAmI == MARIO && state != SINK) {
            sendLogMessage("#MARIO *thinks* he is running at position (" + (int) myX + ", " + (int) myY + ").");
        }

        // AUTOMATON
        switch (state) {
            case TURNSOUTHTASK:
                if (!isSameDirection(getHeading(), Parameters.SOUTH)) {
                    stepTurn(Parameters.Direction.RIGHT);
                    return;
                } else {
                    state = MOVESOUTHTASK;
                    myMove();
                    return;
                }
            case MOVESOUTHTASK:
                IFrontSensorResult frontResultMoveNorth = detectFront();
                if (whoAmI == ROCKY && frontResultMoveNorth.getObjectType() != IFrontSensorResult.Types.WALL ) {
                    myMove();
                    return;
                } else if (whoAmI == ROCKY && frontResultMoveNorth.getObjectType() == IFrontSensorResult.Types.WALL) {
                    state = TURNWESTTASK;
                    stepTurn(Parameters.Direction.LEFT);
                    return;
                }
                if (whoAmI == MARIO && detectRadar().isEmpty()) {
                    state = TURNWESTTASK;
                    move();
                    return;
                }
                else if (whoAmI == MARIO && !detectRadar().isEmpty()) {
                    for (IRadarResult r : detectRadar()) {
                        if (isSameDirection(getHeading(), r.getObjectDirection())){
                            if (r.getObjectDistance() < 300) {
                                    isMoving = false;
                            }
                            return;
                        }
                    }
                    myMove();
                    return;
                }
                else if (whoAmI == MARIO && detectFront().getObjectType() == IFrontSensorResult.Types.WALL) {
                    state = TURNWESTTASK;
                    stepTurn(Parameters.Direction.LEFT);
                    return;
                }
                else if (whoAmI == MARIO && detectFront().getObjectType() != IFrontSensorResult.Types.WALL && detectRadar().isEmpty()) {
                    myMove();
                    return;
                }

                break;
            case TURNWESTTASK:
                if (whoAmI == ROCKY && !isSameDirection(getHeading(), Parameters.EAST)) {
                    stepTurn(Parameters.Direction.LEFT);
                    return;
                } else if (whoAmI == ROCKY && isSameDirection(getHeading(), Parameters.EAST)) {
                    state = MOVEWESTTASK;
                    
                    return;
                }
                if (whoAmI == MARIO && detectRadar().isEmpty() && !isSameDirection(getHeading(), Parameters.EAST) ) {
                    stepTurn(Parameters.Direction.LEFT);
                    return;
                }
                else if (whoAmI == MARIO && !detectRadar().isEmpty()
                        && !isSameDirection(getHeading(), Parameters.EAST)) {
                    isMoving = false;
                    return;
                }
                else if (whoAmI == MARIO && detectRadar().isEmpty() && isSameDirection(getHeading(), Parameters.EAST)) {
                    state = MOVEWESTTASK;
                    myMove();
                    return;
                }
                break;
            case MOVEWESTTASK:
                if(whoAmI == ROCKY && detectFront().getObjectType() != IFrontSensorResult.Types.WALL){
                    myMove();
                    return;
                } else if (whoAmI == ROCKY && detectFront().getObjectType() == IFrontSensorResult.Types.WALL) {
                    state = SINK;
                    return;
                }
                if (whoAmI == MARIO && detectRadar().isEmpty() && detectFront().getObjectType() != IFrontSensorResult.Types.WALL) {
                    myMove();
                    return;
                } else if (whoAmI == MARIO && detectRadar().isEmpty()
                        && detectFront().getObjectType() == IFrontSensorResult.Types.WALL) {
                    state = SINK;
                    return;
                }
                else if (whoAmI == MARIO && !detectRadar().isEmpty()) {
                    isMoving = false;
                    compteur++;
                    if (compteur == 20) {
                        state = SINK;
                    }
                    return;
                }
                break;
            case SINK:
                isMoving = false;
                break;
            default:
                // Handle any unexpected state
                sendLogMessage("Unknown state: " + state);
                break;
        }
    }
    private void myMove() {
        isMoving = true;
        move();
    }

    private boolean isSameDirection(double dir1, double dir2) {
        return Math.abs(dir1 - dir2) < 0.01;
    }
}
