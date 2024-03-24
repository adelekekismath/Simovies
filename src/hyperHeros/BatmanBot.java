package hyperHeros;

import characteristics.Parameters;
import robotsimulator.Brain;
import characteristics.IRadarResult;

import java.util.ArrayList;
import java.util.HashMap;

class RobotMessage {
    String type;
    double x, y;
    String by;
    double treatedTime;

    public RobotMessage(String type, double x, double y, String by) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.by = by;
    }

    public void isTreated() {
        treatedTime = System.currentTimeMillis();
    }

    public boolean isEquals(RobotMessage message) {
        if (type.equals("enemyPosition") && message.type.equals("enemyPosition")) {
            return x == message.x && y == message.y && System.currentTimeMillis() - treatedTime < 2000;
        }
        return x == message.x && y == message.y && type.equals(message.type);
    }
    
}

public class BatmanBot extends Brain {

    private ArrayList<RobotMessage> messageQueue = new ArrayList<>();

    private double enemyDirection = 0;
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
    private static HashMap<String, Coordonnate> allBotsPositions = new HashMap<>();
    private Coordonnate enemyPosition;
    private ArrayList<RobotMessage> historyTreatMessages = new ArrayList<>();

    public BatmanBot() {
        super();
    }

    public void activate() {
        try {
            nominateBot();
        } catch (Exception e) {
            e.printStackTrace();
        }
        takePlaceForDeparture();
        goToTheOtherSideOnDeparture = true;
        currentState = STATE.SINK;
    }

    public void step() {
        // Récupérer les messages des alliés
        handleAlliesMessage();
        // Traiter les résultats radar
        treatWhatIamSeeing(detectRadar());
        // Si mort, arrêter l'exécution
        if (getHealth() <= 0) {
            sendMessageToAllies(
                    "type:wreck;x:" + myPosition.getX() + ";y:" + myPosition.getY() + ";by:" + whoAmI);
            return;
        }
        // Se déplacer vers l'objectif
        moveToObjective();
        // Exécuter l'état courant
        executeCurrentState();
    }

    private void executeCurrentState() {
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
        } else if (currentState == STATE.FIRESTATE) {
            fire(enemyDirection);
            enemyDirection = 0;
            return;
        } else if (currentState == STATE.SINK) {
            // sendLogMessage("I am sinking");
            return;
        }
    }

    private void moveToObjective() {
        if (enemyDirection == 0) {
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
    }
    
    private boolean canFireEnemy() {
        ArrayList<Coordonnate> targets = new ArrayList<>();
        targets.addAll(allBotsPositions.values());
        targets.addAll(obstaclesList);

        if (enemyPosition.getX() < 0 || enemyPosition.getY() < 0)
            return false;

        for (Coordonnate target : targets) {
            double angle = CoordinateTransform.convertCartesianToPolar(myPosition, target).getAngle();
            if (isRoughlySameDirection(angle, enemyDirection)
                    && myPosition.distance(enemyPosition) > myPosition.distance(target)) {
                currentState = STATE.MOVESTATE;
                return false;
            }
        }
        return true;
    }
    
    private void handleAlliesMessage() {
        // COMMUNICATION
        ArrayList<String> rawMessages = fetchAllMessages();
        for (String msg : rawMessages) {
            HashMap<String, String> messageMap = decomposeMessage(msg);
            RobotMessage robotMessage = new RobotMessage(messageMap.get("type"),
                    Double.parseDouble(messageMap.get("x")),
                    Double.parseDouble(messageMap.get("y")),
                    messageMap.get("by"));
            messageQueue.add(robotMessage);
        }
        processWreckMessages();
        processPositionMessages();
        processOtherMessages();
    }

    private boolean isAlreadTreated(RobotMessage message) {
        for (RobotMessage m : historyTreatMessages) {
            if (m.isEquals(message)) {
                return true;
            }
        }
        return false;
    }

    private void processWreckMessages() {
        for (RobotMessage message : messageQueue) {
            if (message.type.equals("wreck") && !isAlreadTreated(message) ) {
                addObstacle(new Coordonnate(message.x, message.y));
                message.isTreated();
                historyTreatMessages.add(message);
            }
        }
        messageQueue.removeIf(message -> message.type.equals("wreck"));
    }

    private void processPositionMessages() {
        for (RobotMessage message : messageQueue) {
            if (!message.by.equals(whoAmI.toString()) && message.type.equals("position")) {
                allBotsPositions.put(message.by, new Coordonnate(message.x, message.y));
            }
        }
        messageQueue.removeIf(message -> message.type.equals("position"));
    }

    private void processOtherMessages() {
        if (!messageQueue.isEmpty()) {
            RobotMessage message = messageQueue.remove(0);
            sendLogMessage(
                    "by: " + message.by + " for " + message.type + " at " + new Coordonnate(message.x, message.y));
            if (!message.by.equals(whoAmI.toString()) && !isAlreadTreated(message)) {
                processEnemyPositionMessage(message);
            }
        }
    }

    private void processEnemyPositionMessage(RobotMessage message) {
        if ("enemyPosition".equals(message.type) && myPosition.distance(new Coordonnate(message.x, message.y)) < 1000) {
            enemyDirection = CoordinateTransform.convertCartesianToPolar(myPosition, new Coordonnate(message.x, message.y)).getAngle();
            enemyPosition = new Coordonnate(message.x, message.y);
            if (canFireEnemy()) {
                currentState = STATE.FIRESTATE;
            } else {
                enemyDirection = 0;
            }
            message.isTreated();
            historyTreatMessages.add(message);
        }
    }

    public void goToTheOtherSide() {
        int destinationX = botSide == SIDE.LEFT ? 2900 : 100;

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

    private void treatWhatIamSeeing(ArrayList<IRadarResult> radarResults) {
        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.OpponentMainBot
                    || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                enemyDirection = r.getObjectDirection();
                enemyPosition = getPositionByDirectionAndDistance(myPosition, r.getObjectDirection(),
                        r.getObjectDistance());
                sendMessageToAllies("type:enemyPosition;x:" + enemyPosition.getX() + ";y:" + enemyPosition.getY() + ";by:"
                        + whoAmI);
                sendLogMessage("I see an enemy at " + enemyPosition);
                if (canFireEnemy()) {
                    currentState = STATE.FIRESTATE;
                } else
                    enemyDirection = 0;
                break;
            } else if (r.getObjectType() == IRadarResult.Types.Wreck) {
                Coordonnate wreckPosition = getPositionByDirectionAndDistance(myPosition, r.getObjectDirection(),
                        r.getObjectDistance());
                
                if (addObstacle(wreckPosition))
                    sendMessageToAllies("type:wreck;x:" + wreckPosition.getX() + ";y:" + wreckPosition.getY() + ";by:"
                        + whoAmI);
            }
        }
    }

    public void takePlaceForDeparture() {
        switch (whoAmI) {
            case BATTMAN1:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 500 : 2500, Parameters.teamAMainBot1InitY + 100);
                break;
            case BATTMAN2:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 800 : 2200, Parameters.teamAMainBot2InitY);
                break;
            case BATTMAN3:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 500 : 2500, Parameters.teamAMainBot3InitY + 100);
                break;
            default:
                break;
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
        if (!historySendMessages.contains(message)) {
            broadcast(message);
            historySendMessages.add(message);
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

    private boolean isRoughlySameDirection(double dir1, double dir2) {
        double difference = Math.abs(dir1 - dir2);
        if (difference > Math.PI) {
            difference = 2 * Math.PI - difference;
        }
        return difference < Math.PI / 23;
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

    private boolean addObstacle(Coordonnate pos) {
        for (Coordonnate obtacle : obstaclesList) {
            if (Math.abs(obtacle.getX() - pos.getX()) < 1 && Math.abs(obtacle.getY() - pos.getY()) < 1)
                return false;
        }
        obstaclesList.add(pos);
        goToTheOtherSide();
        return true;
    }

    private void fireTarget(double enemyDirection, Coordonnate enemyPosition) {
        ArrayList<Coordonnate> targets = new ArrayList<>();
        targets.addAll(allBotsPositions.values());
        targets.addAll(obstaclesList);

        for(Coordonnate target : targets) {
            double angle = CoordinateTransform.convertCartesianToPolar(myPosition, target).getAngle();
            if (isRoughlySameDirection(angle, enemyDirection)
                    && myPosition.distance(enemyPosition) > myPosition.distance(target)) {
                currentState = STATE.MOVESTATE;
                return;
            }
        }
        fire(enemyDirection);
    }
}
