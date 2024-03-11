package terminator;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class MainBotBrain extends Brain {

    private static final double PRECISION = 0.01;
    private static final double FIREANGLEPRECISION = Math.PI / (double) 6;
    private static final double ANGLEPRECISION = 0.01;

    private STATE currentState;
    private Coordonnate myPosition;
    private SIDE botSide;
    private static ArrayList<Coordonnate> obstaclesList = new ArrayList<>();
    private botName whoAmI;
    private ArrayList<Coordonnate> pathToFollow = new ArrayList<>();
    private boolean currentObjectiveReached = false;
    private Coordonnate targetObjective;
    private boolean goToTheOtherSideOnDeparture = false;
    private double enemyDetected = 0;
    private long lastMessageTime = 0; // Dernier moment où un message a été envoyé
    private final long messageCooldown = 1000; // Temps minimum en millisecondes entre les messages

    public MainBotBrain() {
        super();
    }

    public void activate() {
        try {
            nominateBot();
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            e.printStackTrace();
        }
        takePlaceForDéparture();
        goToTheOtherSideOnDeparture = true;
        currentState = STATE.SINK;
    }

    public void step() {

        // COMMUNICATION
        ArrayList<String> messages = fetchAllMessages();
        for (String msg : messages) {
            String[] parts = msg.split(";");
            HashMap<String, String> messageMap = new HashMap<>();
            for (String part : parts) {
                String[] keyValue = part.split(":");
                messageMap.put(keyValue[0], keyValue[1]);
            }
            if ("enemyPosition".equals(messageMap.get("type"))) {
                if (myPosition.distance(new Coordonnate(Double.parseDouble(messageMap.get("x")),
                        Double.parseDouble(messageMap.get("y"))) ) < 500) {
                    targetObjective = new Coordonnate(Double.parseDouble(messageMap.get("x")) - 100,
                            Double.parseDouble(messageMap.get("y")) -100);
                    
                }
            } else if ("iamDead".equals(messageMap.get("type"))) {
                addObstacle(new Coordonnate(Double.parseDouble(messageMap.get("x")), Double.parseDouble(messageMap.get("y"))));
            }
        }

        // Traiter les résultats radar
        processRadarResults(detectRadar());

        if (enemyDetected != 0) {
            // Si un ennemi est détecté et tiré, ne pas bouger et attendre le prochain cycle
            fire(enemyDetected);
            enemyDetected = 0;
            return;
        }

        // Si mort, arrêter l'exécution
        if (getHealth() <= 0) {
            broadcast(getLogMessage());
            addObstacle(myPosition);
            return;
        }

        // Suivre le chemin si disponible
        if (currentObjectiveReached) {
            System.out.println("I have reached the target");
            if (!pathToFollow.isEmpty()) {
                setObjective(pathToFollow.get(0));
                pathToFollow.remove(0);
            } else {
                if (goToTheOtherSideOnDeparture)
                {
                    goToTheOtherSideOnDeparture = false;
                    one_two_three_go();
                }
                else pathToFollow = findPath(myPosition);
            }
        }
        goToTarget();

        if (currentState == STATE.MOVESTATE) {
            // Mettre à jour la myPosition actuelle du robot
            if (detectFront().getObjectType() == IFrontSensorResult.Types.WALL) {
                currentState = STATE.TURNLEFTSTATE;
                stepTurn(Parameters.Direction.LEFT);
                return;
            } else {
                walk(false);
                return;
            }
        } else if (currentState == STATE.MOVEBACKSTATE) {
            walk(true);
            return;
        } else if (currentState == STATE.TURNRIGHTSTATE) {
            stepTurn(Parameters.Direction.RIGHT);
            return;
        } else if (currentState == STATE.TURNLEFTSTATE) {
            stepTurn(Parameters.Direction.LEFT);
            return;
        } else if (currentState == STATE.SINK) {
            return;
        }
    }
    
    private void walk(boolean back) {

        Coordonnate newBotPosition = getNextPosition(back ? -1 : 1);
        for (IRadarResult r : detectRadar()) {
            if (newBotPosition.distance(getPositionByDirectionAndDistance(newBotPosition, r.getObjectDirection(),
            r.getObjectDistance())) < Parameters.teamAMainBotRadius * 2) {
                return;
            }
        }
        if (back) {
            moveBack();
        } else {
            move();
        }
        myPosition = newBotPosition;
    }

    // Méthode pour définir l'objectif
    public void setObjective(Coordonnate objective) {
        this.targetObjective = objective;
    }

    public void addObstacle(Coordonnate pos) {
        for (Coordonnate obtacle : obstaclesList) {
            if (Math.abs(obtacle.getX() - pos.getX()) < 1 && Math.abs(obtacle.getY() - pos.getY()) < 1)
                return;
        }
        obstaclesList.add(pos);
        one_two_three_go();
    }

    public void one_two_three_go() {
        System.out.println("I am going to the default path");
        int destinationX = botSide == SIDE.LEFT ? 2900 : 2000;
        int[] yCoordinates = { 600, 1200, 400, 1100, 1700 }; // y coordinates for the bots

        if (whoAmI == botName.BATTMAN1) {
            pathToFollow = PathFinder.findPath(myPosition, new Coordonnate(destinationX,
                    yCoordinates[2]), obstaclesList);
        } else if (whoAmI == botName.BATTMAN2) {
            pathToFollow = PathFinder.findPath(myPosition, new Coordonnate(destinationX,
                    yCoordinates[3]), obstaclesList);
        } else if (whoAmI == botName.BATTMAN3) {
            pathToFollow = PathFinder.findPath(myPosition, new Coordonnate(destinationX,
                    yCoordinates[4]), obstaclesList);
        }
    }

    private ArrayList<Coordonnate> findPath(Coordonnate coord) {
        System.out.println("find a path");
        Coordonnate upLeft = new Coordonnate(200, 200);
        Coordonnate underUpLeft = new Coordonnate(400, 1000);
        Coordonnate righterUpLeft = new Coordonnate(1200, 200);
        Coordonnate downRight = new Coordonnate(2600, 1800);
        Coordonnate upperDownRight = new Coordonnate(2800, 1000);
        ArrayList<Coordonnate> res = new ArrayList<>();

        res.addAll(PathFinder.findPath(coord, underUpLeft, obstaclesList));
        res.addAll(PathFinder.findPath(underUpLeft, upLeft, obstaclesList));
        res.addAll(PathFinder.findPath(upLeft, righterUpLeft, obstaclesList));
        res.addAll(PathFinder.findPath(righterUpLeft, downRight, obstaclesList));
        res.addAll(PathFinder.findPath(downRight, upperDownRight, obstaclesList));
        return res;
    }

    private void takePlaceForDéparture() {
        switch (whoAmI) {
            case BATTMAN1:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 400 : 2500, 500);
                break;
            case BATTMAN2:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 300 : 2700, 1100);
                break;
            case BATTMAN3:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 400 : 2500, 1700);
                break;
            default:
                break;
        }
    }

    private void nominateBot()
            throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
        int under = 0, on = 0;
        for (IRadarResult r : detectRadar()) {
            under += (r.getObjectDirection() == Parameters.SOUTH) ? 1 : 0;
            on += (r.getObjectDirection() == Parameters.NORTH) ? 1 : 0;
        }
        botSide = getHeading() == Parameters.EAST ? SIDE.LEFT : SIDE.RIGHT;
        int botNumber = (on == 0) ? 1 : ((on == 1 && under == 1) ? 2 : 3);
        String prefix = botSide == SIDE.LEFT ? "teamAMainBot" : "teamBMainBot";
        double initX = Parameters.class.getField(prefix + botNumber + "InitX").getDouble(null) + 50;
        double initY = Parameters.class.getField(prefix + botNumber + "InitY").getDouble(null) + 50;
        whoAmI = botName.valueOf("BATTMAN" + botNumber);
        myPosition = new Coordonnate(initX, initY);
        sendLogMessage("I am " + whoAmI + " at " + myPosition);
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

    private Coordonnate getNextPosition(double back) {
        return new Coordonnate(
                myPosition.getX() + back * Parameters.teamAMainBotSpeed * Math.cos(myGetHeading()),
                myPosition.getY() + back * Parameters.teamAMainBotSpeed * Math.sin(myGetHeading()));
    }

    private void processRadarResults(ArrayList<IRadarResult> radarResults) {
        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.OpponentMainBot
                    || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                fire(r.getObjectDirection());
                enemyDetected = r.getObjectDirection();
                return;
            }
            // if (r.getObjectType() == IRadarResult.Types.Wreck && r.getObjectDistance() <= 150) {
            //     currentState = STATE.TURNLEFTSTATE;
            //     sendLogMessage("Wreck detected at " + r.getObjectDistance() + "m");
            // }
            // if ((r.getObjectType() == IRadarResult.Types.TeamMainBot
            //         || r.getObjectType() == IRadarResult.Types.TeamSecondaryBot) &&
            //         r.getObjectDistance() <= 150) {
            //     currentState = STATE.TURNLEFTSTATE;
            //     sendLogMessage("Ally detected at " + r.getObjectDistance() + "m");
            // }
        }
    }

    private void  goToTarget() {
        currentObjectiveReached = false;
        PolarCoordinate polarInstance = CoordinateTransform.convertCartesianToPolar(myPosition, targetObjective);
        if (whoAmI == botName.BATTMAN1) System.out.println("Distance to target: " + polarInstance.getDist());
        if ( polarInstance.getDist() > 10) {
            if (isSameDirection(polarInstance.getAngle())) {
                currentState = STATE.MOVESTATE;
            }
            else if (isOppositeDirection(polarInstance.getAngle())) {
                currentState = STATE.MOVEBACKSTATE;
            }
            else {
                currentState = determineTurnDirection(polarInstance.getAngle());  
            }
        } else {
            if (whoAmI == botName.BATTMAN1) sendLogMessage("I have reached the target");
            currentObjectiveReached = true;
            currentState = STATE.SINK;  
        }

    }

    public boolean isSameDirection(double dir) {
        double cosDir = Math.cos(dir);
        double sinDir = Math.sin(dir);
        double cosBot = Math.cos(getHeading());
        double sinBot = Math.sin(getHeading());

        return Math.abs(cosDir - cosBot) < Math.cos(Parameters.teamBSecondaryBotStepTurnAngle)
                && Math.abs(sinDir - sinBot) < Math.sin(Parameters.teamBSecondaryBotStepTurnAngle);
    }

    public boolean isOppositeDirection(double dir) {
        double cosDir = Math.cos(dir);
        double sinDir = Math.sin(dir);
        double cosBot = - Math.cos(getHeading());
        double sinBot = - Math.sin(getHeading());

        return Math
                .abs(cosDir - cosBot) < Math
                        .cos(Parameters.teamBSecondaryBotStepTurnAngle)
                && Math.abs(sinDir - sinBot) < Math.sin(Parameters.teamBSecondaryBotStepTurnAngle);
    }

    private STATE determineTurnDirection(double dir) {
        double cos = Math.cos(getHeading() - dir);
        double sin = Math.sin(getHeading() - dir);
        if (cos > 0) {
            if (sin > 0) {
                return STATE.TURNRIGHTSTATE;
            } else {
                return STATE.TURNLEFTSTATE;
            }
        } else if (sin < 0) {
            return STATE.TURNLEFTSTATE;
        } else {
            return STATE.TURNRIGHTSTATE;
        }
    }

    public Coordonnate getPositionByDirectionAndDistance (Coordonnate position, double direction, double distance) {
        return new Coordonnate(position.getX() + distance * Math.cos(direction), position.getY() + distance * Math.sin(direction));
    }
}
