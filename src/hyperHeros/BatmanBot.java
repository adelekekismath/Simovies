package hyperHeros;

import characteristics.Parameters;
import robotsimulator.Brain;
import characteristics.IRadarResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.PriorityQueue;

class RobotMessage {
    String type;
    double x, y;
    String by;
    double direction;

    public RobotMessage(String type, double x, double y, String by) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.by = by;
    }

    public RobotMessage( String type, double x, double y, String by, double direction) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.by = by;
        this.direction = direction;
    }
}

public class BatmanBot extends Brain {

    private static final double ANGLEPRECISION = 0.01;
    private ArrayList<RobotMessage> messageQueue = new ArrayList<>();
    private boolean canStart = false;

    private double enemyDetected = 0;
    private STATE currentState;
    private Coordonnate myPosition;
    private SIDE botSide;
    private ArrayList<Coordonnate> obstaclesList = new ArrayList<>();
    private botName whoAmI;
    private ArrayList<Coordonnate> pathToFollow = new ArrayList<>();
    private boolean currentObjectiveReached = false;
    private Coordonnate targetObjective;
    private boolean goToTheOtherSideOnDeparture = false;
    private ArrayList<String> historySendMessages = new ArrayList<>();
    private long lastMessageTime = 0; // Dernier moment où un message a été envoyé
    private static final long messageCooldown = 500; // Temps minimum en millisecondes entre les messages
    private static HashMap<String, Coordonnate> allBotsPositions = new HashMap<>();

    public BatmanBot() {
        super();
    }

    public void step() {
        handleAlliesMessage();
        
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
            sendMessageToAllies("type:someOneIsDead;x:" + myPosition.getX() + ";y:" + myPosition.getY() + ";by:" + whoAmI);
            return;
        }

        if (getHealth() < 50 && currentState == STATE.FIRESTATE) {
            sendMessageToAllies(
                    "type:needSomeHelp;x:" + targetObjective.getX() + ";y:" + targetObjective.getY() + ";by:" + whoAmI);
        }

        if (canStart) {
            if (currentObjectiveReached) {
                if (!pathToFollow.isEmpty()) {
                    setObjective(pathToFollow.get(0));
                    pathToFollow.remove(0);
                } else {
                    if (goToTheOtherSideOnDeparture) {
                        goToTheOtherSideOnDeparture = false;
                        goToTheOtherSide();
                    } else
                        pathToFollow = findPath(myPosition);
                }
            }
            goToTarget();
        }

        if (currentState == STATE.MOVESTATE) {
            walk(false);
            return;
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
    
    private void handleAlliesMessage() {
        // COMMUNICATION
        ArrayList<String> rawMessages = fetchAllMessages();
        for (String msg : rawMessages) {
            HashMap<String, String> messageMap = decomposeMessage(msg);
            RobotMessage robotMessage = new RobotMessage(messageMap.get("type"),
                    Double.parseDouble(messageMap.get("x")),
                    Double.parseDouble(messageMap.get("y")),
                    messageMap.get("by"),
                    messageMap.get("direction") != null ? Double.parseDouble(messageMap.get("direction")) : 0.0);
            messageQueue.add(robotMessage);
        }

        for (RobotMessage message : messageQueue) {
            if (message.type.equals("position")) {
                allBotsPositions.put(message.by, new Coordonnate(message.x, message.y));
            }
        }
        messageQueue.removeIf(message -> message.type.equals("position"));
        if (!messageQueue.isEmpty()) {
            RobotMessage message = messageQueue.remove(0);
            sendLogMessage("by: " + message.by + "for" + message.type + "x:" + message.x);
            if (!message.by.equals(whoAmI.toString())) {
                if ("enemyPosition".equals(message.type)) {
                    if (myPosition.distance(new Coordonnate(message.x, message.y)) < 800) {
                        enemyDetected = message.direction;
                    }
                }
                if ("someOneIsDead".equals(message.type)) {
                    addObstacle(new Coordonnate(message.x, message.y));
                }
                if ("youCanStartBot1".equals(message.type) && whoAmI == botName.BATTMAN1) {
                    canStart = true;
                    goToTheOtherSideOnDeparture = true;
                }
                if ("youCanStartBot3".equals(message.type)
                        && (whoAmI == botName.BATTMAN3 || whoAmI == botName.BATTMAN2)) {
                    canStart = true;
                    goToTheOtherSideOnDeparture = true;
                }
                if ("needSomeHelp".equals(message.type)) {
                    if (myPosition.distance(new Coordonnate(message.x, message.y)) < 400) {
                        enemyDetected = Math.atan2(message.y - myPosition.getY(), message.x - myPosition.getX());
                    }
                }
            }
        }
    }


    public void goToTheOtherSide() {
        int destinationX = botSide == SIDE.LEFT ? 2900 : 2000;

        if (whoAmI == botName.BATTMAN1) {
            pathToFollow = PathFinder.findPath(myPosition, new Coordonnate(destinationX,
                    400), obstaclesList);
        } else if (whoAmI == botName.BATTMAN2) {
            pathToFollow = PathFinder.findPath(myPosition, new Coordonnate(destinationX,
                    1100), obstaclesList);
        } else if (whoAmI == botName.BATTMAN3) {
            pathToFollow = PathFinder.findPath(myPosition, new Coordonnate(destinationX,
                    1800), obstaclesList);
        }
    }


    public void nominateBot()
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

    private void processRadarResults(ArrayList<IRadarResult> radarResults) {
        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.OpponentMainBot
                    || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                fire(r.getObjectDirection());
                enemyDetected = r.getObjectDirection();
                sendMessageToAllies("type:enemyPosition;x:" + myPosition.getX() + ";y:" + myPosition.getY() + ";by:"
                        + whoAmI + ";direction:" + r.getObjectDirection());
                return;
            } else if (r.getObjectType() == IRadarResult.Types.Wreck) {
                Coordonnate wreckPosition = getPositionByDirectionAndDistance(myPosition, r.getObjectDirection(),
                        r.getObjectDistance());
                addObstacle(wreckPosition);
                sendMessageToAllies("type:someOneIsDead;x:" + wreckPosition.getX() + ";y:" + wreckPosition.getY() + ";by:"
                        + whoAmI);
            }
        }
    }

    public void takePlaceForDeparture() {
        switch (whoAmI) {
            case BATTMAN1:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 500 : 2200,
                        Parameters.teamAMainBot1InitY + 100);
                break;
            case BATTMAN2:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 800 : 1900, Parameters.teamAMainBot2InitY);
                break;
            case BATTMAN3:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 500 : 2200,
                        Parameters.teamAMainBot3InitY + 100);
                break;
            default:
                break;
        }
    }

    public void activate() {
        try {
            nominateBot();
        } catch (Exception e) {
            e.printStackTrace();
        }
        takePlaceForDeparture();
        if (whoAmI == botName.ZORRO1 || whoAmI == botName.ZORRO2) {
            goToTheOtherSideOnDeparture = true;
        }
        currentState = STATE.SINK;
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
        sendMessageToAllies("type:position;x:" + myPosition.getX() + ";y:" + myPosition.getY() + ";by:" + whoAmI);
    }

    private ArrayList<Coordonnate> findPath(Coordonnate coord) {
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

    private Coordonnate getPositionByDirectionAndDistance(Coordonnate position, double direction, double distance) {
        return new Coordonnate(position.getX() + distance * Math.cos(direction),
                position.getY() + distance * Math.sin(direction));
    }

    private void sendMessageToAllies(String message) {
        if (System.currentTimeMillis() - lastMessageTime > messageCooldown) {
            if (!historySendMessages.contains(message)) {
                broadcast(message);
                historySendMessages.add(message);
                lastMessageTime = System.currentTimeMillis();
            }
        }
    }

    private Coordonnate getNextPosition(double back) {
        return new Coordonnate(
                myPosition.getX() + (back * Parameters.teamAMainBotSpeed) * Math.cos(myGetHeading()),
                myPosition.getY() + (back * Parameters.teamAMainBotSpeed) * Math.sin(myGetHeading()));
    }

    private void goToTarget() {
        currentObjectiveReached = false;
        PolarCoordinate polarInstance = CoordinateTransform.convertCartesianToPolar(myPosition, targetObjective);
        if (polarInstance.getDist() > 10) {
            if (isSameDirection(polarInstance.getAngle())) {
                currentState = STATE.MOVESTATE;
            } else if (isOppositeDirection(polarInstance.getAngle())) {
                currentState = STATE.MOVEBACKSTATE;
            } else {
                currentState = determineTurnDirection(polarInstance.getAngle());
            }
        } else {
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
        double cosBot = -Math.cos(getHeading());
        double sinBot = -Math.sin(getHeading());

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

    private double myGetHeading() {
        return normalizeRadian(getHeading());
    }

    private double normalizeRadian(double angle) {
        double result = angle;
        while (result < 0)
            result += 2 * Math.PI;
        while (result >= 2 * Math.PI)
            result -= 2 * Math.PI;
        return result;
    }

    // Méthode pour définir l'objectif
    private void setObjective(Coordonnate objective) {
        this.targetObjective = objective;
    }

    private HashMap<String, String> decomposeMessage(String message) {
        HashMap<String, String> details = new HashMap<>();
        String[] parts = message.split(";");

        for (String part : parts) {
            String[] keyValue = part.split(":");
            if (keyValue.length == 2) {
                details.put(keyValue[0], keyValue[1]);
            }
        }
        return details;
    }

    private void addObstacle(Coordonnate pos) {
        for (Coordonnate obtacle : obstaclesList) {
            if (Math.abs(obtacle.getX() - pos.getX()) < 1 && Math.abs(obtacle.getY() - pos.getY()) < 1)
                return;
        }
        obstaclesList.add(pos);
        goToTheOtherSide();
    }

    private void fireTarget(double direction) {
        ArrayList<Coordonnate> targets = new ArrayList<>();
        targets.addAll(allBotsPositions.values());
        targets.addAll(obstaclesList);

        for (Coordonnate target : targets) {
            PolarCoordinate[] corners = new PolarCoordinate[4];
            corners[0] = CoordinateTransform.convertCartesianToPolar(myPosition, new Coordonnate(target.getX() + 60, target.getY() + 60));
            corners[1] = CoordinateTransform.convertCartesianToPolar(myPosition, new Coordonnate(target.getX() - 60, target.getY() - 60));
            corners[2] = CoordinateTransform.convertCartesianToPolar(myPosition, new Coordonnate(target.getX() - 60, target.getY() + 60));
            corners[3] = CoordinateTransform.convertCartesianToPolar(myPosition, new Coordonnate(target.getX() + 60, target.getY() - 60));

            // Vérifie si l'angle est entre n'importe quel couple de coins opposés.
            if (isBetweenAngle(direction, corners[1].getAngle(), corners[0].getAngle())
                    || isBetweenAngle(direction, corners[2].getAngle(), corners[3].getAngle())) {
                return;
            }
        }
        fire(direction);
    }
    
    private boolean isBetweenAngle(double angle, double a, double b) {
        a -= angle;
        b -= angle;
        normalizeRadian(a);
        normalizeRadian(b);
        if (a * b >= 0) {
            return false;
        } else
            return Math.abs(a - b) < Math.PI;
    }



}
