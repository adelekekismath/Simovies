package terminator;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

import java.util.ArrayList;
import java.util.Random;

public class MainBotBrain extends Brain {

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
    private ArrayList<Coordonnate> pathToFollow = new ArrayList<>();
    private Coordonnate targetObjective;
    private double heading;

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
        oldAngle = myGetHeading();
    }

    public void step() {

        if (currentState == STATE.DEPARTURESTATE) {
            
        }

        // Mettre à jour la myPosition actuelle du robot
        updateMyPosition();

        // Traiter les résultats radar
        processRadarResults(detectRadar());

        // Si gelé ou en mauvaise santé, arrêter l'exécution
        if (freeze || getHealth() <= 0)
            return;

        // Suivre le chemin si disponible
        if (!pathToFollow.isEmpty()) {
            followPath();
        } else {
            // Logique par défaut en l'absence d'un chemin à suivre
            defaultBehavior();
        }

        // double myX = myPosition.getX() + Parameters.teamAMainBotSpeed *
        // Math.cos(myGetHeading());
        // double myY = myPosition.getY() + Parameters.teamAMainBotSpeed *
        // Math.sin(myGetHeading());
        // myPosition = new Coordonnate(myX, myY);
        // ArrayList<IRadarResult> radarResults = detectRadar();
        // freeze = false;

        // if (getHealth() <= 0) {
        // addObstacle(myPosition);
        // sendLogMessage("Wreck detected at " + myPosition);
        // return;
        // }

        // for (IRadarResult r : radarResults) {
        // if (r.getObjectType() == IRadarResult.Types.OpponentMainBot
        // || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
        // fire(r.getObjectDirection());
        // return;
        // }
        // if (r.getObjectType() == IRadarResult.Types.Wreck && r.getObjectDistance() <=
        // 150) {
        // //currentState = STATE.TURNLEFTSTATE;
        // stepTurn(Parameters.Direction.LEFT);
        // sendLogMessage("Wreck detected at " + r.getObjectDistance() + "m");
        // }
        // if ((r.getObjectType() == IRadarResult.Types.TeamMainBot
        // || r.getObjectType() == IRadarResult.Types.TeamSecondaryBot) &&
        // r.getObjectDistance() <= 150){
        // // currentState = STATE.TURNLEFTSTATE;
        // stepTurn(Parameters.Direction.LEFT);
        // sendLogMessage("Ally detected at " + r.getObjectDistance() + "m");

        // }
        // if (r.getObjectDistance() <= 150 &&
        // !isRoughlySameDirection(r.getObjectDirection(), getHeading())
        // && r.getObjectType() != IRadarResult.Types.BULLET) {
        // freeze = true;
        // }
        // }

        // if (freeze) {
        // return;
        // }

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
            if (!(isSameDirection(getHeading(), oldAngle +
                    Parameters.RIGHTTURNFULLANGLE))) {
                stepTurn(Parameters.Direction.RIGHT);
            } else {
                currentState = STATE.MOVESTATE;
                move();
                return;
            }
            return;
        } else if (currentState == STATE.TURNLEFTSTATE) {
            if (!(isSameDirection(getHeading(), oldAngle +
                    Parameters.LEFTTURNFULLANGLE))) {
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

    // Méthode pour définir l'objectif
    public void setObjective(Coordonnate objective) {
        this.targetObjective = objective;
    }

    // Méthode pour mettre à jour et exécuter l'action basée sur l'objectif
    public void updateAction() {
        if (targetObjective != null) {
            double angleToObject = calculateAngleToObject(targetObjective);
            double distanceToObject = calculateDistanceToObject(targetObjective);

            if (distanceToObject > 200) { // Supposons que 10 est le seuil de proximité
                if (isFacingTarget(angleToObject)) {
                    move(); // Avance vers la cible
                } else {
                    // Décider de tourner à gauche ou à droite
                    double direction = determineTurnDirection(angleToObject);
                    if (direction < 0) {
                        stepTurn(Parameters.Direction.LEFT);
                    } else {
                        stepTurn(Parameters.Direction.RIGHT);
                    }
                }
            }
        }
    }

    // Calculer l'angle vers l'objectif
    private double calculateAngleToObject(Coordonnate objective) {
        double deltaX = objective.getX() - myPosition.getX();
        double deltaY = objective.getY() - myPosition.getY();
        return Math.atan2(deltaY, deltaX);
    }

    // Calculer la distance vers l'objectif
    private double calculateDistanceToObject(Coordonnate objective) {
        double deltaX = objective.getX() - myPosition.getX();
        double deltaY = objective.getY() - myPosition.getY();
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    // Vérifier si le robot fait face à l'objectif
    private boolean isFacingTarget(double targetAngle) {
        double heading = myGetHeading(); // Assurez-vous que myGetHeading() retourne l'angle actuel du robot
        double angleDifference = normalizeAngle(targetAngle - heading);
        final double SOME_THRESHOLD = Math.PI / 18; // Définissez un seuil approprié, par exemple 10 degrés
        return Math.abs(angleDifference) < SOME_THRESHOLD;
    }

    private double normalizeAngle(double angle) {
        while (angle > Math.PI)
            angle -= 2 * Math.PI;
        while (angle < -Math.PI)
            angle += 2 * Math.PI;
        return angle;
    }

    // Déterminer la direction de rotation (négatif pour gauche, positif pour
    // droite)
    private double determineTurnDirection(double angleToObject) {
        double angleDifference = angleToObject - heading;
        // Ici, retournez un nombre négatif ou positif basé sur la direction
        return angleDifference; // Simplifié, nécessite une logique pour choisir entre gauche et droite
    }

    public void addObstacle(Coordonnate pos) {
        for (Coordonnate obtacle : obstaclesList) {
            if (Math.abs(obtacle.getX() - pos.getX()) < 1 && Math.abs(obtacle.getY() - pos.getY()) < 1)
                return;
        }
        obstaclesList.add(pos);
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

    private void takePlaceForDéparture() {
        switch (whoAmI) {
            case BATTMAN1:
                pathToFollow = PathFinder.findPath(myPosition, 
                        new Coordonnate(botSide == SIDE.LEFT ? 350 : 2650, 450), obstaclesList);
                break;
            case BATTMAN2:
                pathToFollow = PathFinder.findPath(myPosition, 
                        new Coordonnate(botSide == SIDE.LEFT ? 100 : 2900, 1050), obstaclesList);
                break;
            case BATTMAN3:
                pathToFollow = PathFinder.findPath(myPosition, 
                        new Coordonnate(botSide == SIDE.LEFT ? 350 : 2650, 1650), obstaclesList);
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
        botSide = getHeading() == Parameters.WEST ? SIDE.LEFT : SIDE.RIGHT;
        int botNumber = (on == 0) ? 1 : ((on == 1 && under == 1) ? 2 : 3);
        String prefix = botSide == SIDE.LEFT ? "teamAMainBot" : "teamBMainBot";
        double initX = Parameters.class.getField(prefix + botNumber + "InitX").getDouble(null)
                * Math.cos(myGetHeading());
        double initY = Parameters.class.getField(prefix + botNumber + "InitY").getDouble(null)
                * Math.sin(myGetHeading());

        whoAmI = botName.valueOf("BATTMAN" + botNumber);
        myPosition = new Coordonnate(initX, initY);
        sendLogMessage("I am " + whoAmI + " at " + myPosition);
    }

    private boolean seeWall() {
        return wallIsDetected(myPosition);
    }

    private boolean isRoughlySameDirection(double dir1, double dir2) {
        return Math.abs(normalizeRadian(dir1) - normalizeRadian(dir2)) < FIREANGLEPRECISION;
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

    private void updateMyPosition() {
        myPosition = new Coordonnate(
                myPosition.getX() + Parameters.teamAMainBotSpeed * Math.cos(myGetHeading()),
                myPosition.getY() + Parameters.teamAMainBotSpeed * Math.sin(myGetHeading()));
    }

    private void processRadarResults(ArrayList<IRadarResult> radarResults) {
        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.OpponentMainBot
                    || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                fire(r.getObjectDirection());
                return;
            }
            if (r.getObjectType() == IRadarResult.Types.Wreck && r.getObjectDistance() <= 150) {
                // currentState = STATE.TURNLEFTSTATE;
                stepTurn(Parameters.Direction.LEFT);
                sendLogMessage("Wreck detected at " + r.getObjectDistance() + "m");
            }
            if ((r.getObjectType() == IRadarResult.Types.TeamMainBot
                    || r.getObjectType() == IRadarResult.Types.TeamSecondaryBot) &&
                    r.getObjectDistance() <= 150) {
                // currentState = STATE.TURNLEFTSTATE;
                stepTurn(Parameters.Direction.LEFT);
                sendLogMessage("Ally detected at " + r.getObjectDistance() + "m");
            }
        }
    }

    private void followPath() {
        Coordonnate nextPoint = pathToFollow.get(0);
        double angleToPoint = Math.atan2(nextPoint.getY() - myPosition.getY(), nextPoint.getX() - myPosition.getX());

        if (!isRoughlySameDirection(angleToPoint, myGetHeading())) {
            // Tourner vers le prochain point si pas déjà aligné
            turnTowardsPoint(angleToPoint);
        } else {
            // Déplacer vers le prochain point si aligné
            move();
            // Retirer le point atteint
            if (isCloseTo(nextPoint)) {
                pathToFollow.remove(0);
            }
        }
    }

    private boolean isCloseTo(Coordonnate point) {
        return myPosition.distance(point) < Parameters.teamAMainBotSpeed;
    }

    private void turnTowardsPoint(double targetAngle) {
        double angleDifference = normalizeRadian(targetAngle - myGetHeading());

        if (angleDifference > 0) {
            stepTurn(Parameters.Direction.RIGHT);
        } else {
            stepTurn(Parameters.Direction.LEFT);
        }
    }

    private void defaultBehavior() {
        // Votre logique par défaut lorsque aucun chemin n'est à suivre ou en cas
        // d'autres conditions
        move();
    }
}

// import java.util.ArrayList;
// import java.util.Random;

// enum WHOAMI {
// MARIO,
// ROCKY,
// RAMBO
// }

// enum SIDE {
// LEFT,
// RIGHT
// }

// public class MainBotBrain extends Brain {

// private WHOAMI name;
// private SIDE teamSide;

// private static final int MOVESTATE = 0;
// private static final int TURNRIGHTSTATE = 1;
// private static final int TURNLEFTSTATE = 2;
// private static final int FIRESTATE = 3;

// private int currentState;
// private static final double PRECISION = 0.01;
// private static final double moveSpeed = Parameters.teamAMainBotSpeed;

// private Coordonnate myPosition;
// private ArrayList<Coordonnate> wreckList = new ArrayList<Coordonnate>();

// public MainBotBrain() {
// super();
// myPosition = new Coordonnate(Parameters.teamAMainBot1InitX,
// Parameters.teamAMainBot1InitY);
// }

// public void activate() {
// currentState = MOVESTATE;
// }

// public void step() {
// ArrayList<IRadarResult> radarResults = detectRadar();
// switch (currentState) {
// case MOVESTATE:
// if (seeWall()) {
// currentState = TURNRIGHTSTATE;
// return;
// } else if (seeAllies(radarResults)) {
// currentState = TURNLEFTSTATE;
// return;
// } else {
// move();
// return;
// }
// case TURNRIGHTSTATE:
// stepTurn(Parameters.Direction.RIGHT);
// currentState = MOVESTATE;
// return;
// case TURNLEFTSTATE:
// stepTurn(Parameters.Direction.LEFT);
// currentState = MOVESTATE;
// return;
// case FIRESTATE:
// handleFire(radarResults);
// return;
// default:
// // Logique pour les états supplémentaires
// break;
// }
// }

// private void handleFire(ArrayList<IRadarResult> radarResults) {
// for (IRadarResult r : radarResults) {
// if (r.getObjectType() == IRadarResult.Types.OpponentMainBot ||
// r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot){
// fire(r.getObjectDirection());
// return;
// }
// }
// }

// private boolean seeWall() {
// return wallIsDetected(myPosition);
// }

// private boolean seeAllies(ArrayList<IRadarResult> detectedObjects) {
// for (IRadarResult res : detectedObjects) {
// if (res.getObjectType() == IRadarResult.Types.TeamMainBot ||
// res.getObjectType() == IRadarResult.Types.TeamSecondaryBot) {
// return true;
// }
// }
// return false;
// }

// private boolean directionSouth() {
// return (Math.abs(getHeading() - Parameters.SOUTH) < PRECISION);
// }

// private boolean directionEast() {
// return (Math.abs(getHeading() - Parameters.EAST) < PRECISION);
// }

// private boolean directionNorth() {
// return (Math.abs(getHeading() - Parameters.NORTH) < PRECISION);
// }

// public static boolean wallIsDetected(Coordonnate pos) {
// return (pos.x <= 100.0 || pos.x >= 3000.0 || pos.y <= 100.0 || pos.y >=
// 2000.0);
// }

// public static Coordonnate polToCart(Coordonnate orig, double angle, double
// dist) {
// double x = orig.x + (dist * Math.cos(angle));
// x = (x > 3000) ? 3000 : x;
// x = (x < 0) ? 0 : x;
// double y = orig.y + (dist * Math.sin(angle));
// y = (y > 2000) ? 2000 : y;
// y = (y < 0) ? 0 : y;
// return new Coordonnate(x, y);
// }

// public void move(boolean back) {
// double speed = back ? -moveSpeed : moveSpeed;
// boolean collision = false;
// Coordonnate newCartCoordinate = polToCart(myPosition, getHeading(), speed);
// boolean bull = false;
// for (IRadarResult r : detectRadar()) {
// switch (r.getObjectType()) {
// case OpponentMainBot:
// case OpponentSecondaryBot:
// case TeamMainBot:
// case TeamSecondaryBot:
// case Wreck:
// break;
// case BULLET:
// bull = true;
// break;
// }

// Coordonnate botPos = polToCart(myPosition, r.getObjectDirection(),
// r.getObjectDistance());
// if (!bull)
// if (!collision)
// collision = (newCartCoordinate.getX() - botPos.getX()) *
// (newCartCoordinate.getX() - botPos
// .getX()) + (newCartCoordinate.getY()
// - botPos
// .getY())
// * (newCartCoordinate.getY() - botPos
// .getY()) < (Parameters.teamAMainBotRadius + 50)
// * (Parameters.teamAMainBotRadius
// + 50);
// }

// if (!wallIsDetected(newCartCoordinate)) {
// if (!collision && getHealth() > 0) {
// myPosition = newCartCoordinate;
// }
// }

// if (back)
// super.moveBack();
// else
// super.move();

// }

// private void addWreck(Coordonnate pos) {
// for (Coordonnate wreck : wreckList) {
// if (Math.abs(wreck.getX() - pos.getX()) < 1 && Math.abs(wreck.getY() -
// pos.getY()) < 1)
// return;
// }
// wreckList.add(pos);
// }

// private void getTurnDirection(Coordonnate pos) {
// double angle = Math.atan2(pos.getY() - myPosition.getY(), pos.getX() -
// myPosition.getX());
// if (angle < 0)
// angle += 2 * Math.PI;
// if (angle < getHeading())
// stepTurn(Direction.LEFT);
// else
// stepTurn(Direction.RIGHT);
// }

// @Override
// public void move() {
// move(false);
// }

// @Override
// public void moveBack() {
// move(true);
// }

// public boolean isSameDirection(Direction left) {
// return (Math.abs(getHeading() - left) < PRECISION);
// }

// }
