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
    private static HashMap<String, Coordonnate> allBotsPositions = new HashMap<>();
    private Coordonnate enemyPosition;
    private ORDER currentOrder;
    private boolean readyToGo = false;
    private int countBattManBotReady = 0;

    private enum ORDER {
        WAITINGTOGO, GOING
    }

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
        //goToTheOtherSideOnDeparture = true;
        currentOrder = ORDER.WAITINGTOGO;
        currentState = STATE.SINK;
    }

    public void step() {
        // Récupérer les messages des alliés
            handleAlliesMessage();
            // Traiter les résultats radar
            treatWhatIamSeeing(detectRadar());
            
            // Se déplacer vers l'objectif
            moveToObjective();

        if (getHealth() <= 0) {
            // Si mort, arrêter l'exécution
            broadcast(
                    "type:wreck;x:" + myPosition.getX() + ";y:" + myPosition.getY() + ";by:" + whoAmI);
            return;
        }
        
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
                if (currentOrder == ORDER.WAITINGTOGO && !readyToGo) {
                    broadcast("type:readyToGo;by:" + whoAmI + ";x:" + myPosition.getX() + ";y:" + myPosition.getY());
                    readyToGo = true;
                } else {
                    if (!pathToFollow.isEmpty()) {
                        setObjective(pathToFollow.get(0));
                        pathToFollow.remove(0);
                    } else {
                        if (goToTheOtherSideOnDeparture) {
                            goToTheOtherSideOnDeparture = false;
                            if (whoAmI == botName.BATTMAN1 || whoAmI == botName.BATTMAN3) {
                                String messageType = whoAmI == botName.BATTMAN1 ? "youCanStartZorro1"
                                        : "youCanStartZorro2";
                                broadcast("type:" + messageType + ";by:" + whoAmI + ";x:" + myPosition.getX() + ";y:"
                                        + myPosition.getY());
                            }
                            currentOrder = ORDER.GOING;
                            goToTheOtherSide();
                            setObjective(pathToFollow.get(0));
                        } else if (currentOrder == ORDER.GOING)
                            pathToFollow = findPath(myPosition);
                    }
                }
            }
            goToTarget();
        }
    }
    
    private boolean canFireEnemy() {
        ArrayList<Coordonnate> targets = new ArrayList<>();
        targets.addAll(allBotsPositions.values());
        targets.addAll(obstaclesList);

        for (Coordonnate target : targets) {
            double angle = CoordinateTransform.convertCartesianToPolar(myPosition, target).getAngle();
            if (isRoughlySameDirection(angle, enemyDirection)) {
                sendLogMessage("Can't fire enemy at"+ enemyDirection );
                currentState = STATE.MOVESTATE;
                return false;
            }
        }
        return true;
    }

    private Coordonnate predictFuturePosition(Coordonnate currentPosition, double direction, int speed) {
        // Cette fonction devrait estimer la position future basée sur la direction et
        double futureX = currentPosition.getX() + Math.cos(direction) * speed;
        double futureY = currentPosition.getY() + Math.sin(direction) * speed;
        return new Coordonnate(futureX, futureY);
    }
    
    private boolean isLineOfSightClear(Coordonnate from, Coordonnate to) {
        // Distance totale à parcourir
        double totalDistance = from.distance(to);
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();

        // Nombre de vérifications basé sur la distance entre les points
        int steps = (int) (totalDistance / 10); // Choisissez un pas de vérification approprié

        // Vérifier chaque point le long de la ligne pour les obstacles
        for (int i = 1; i <= steps; i++) {
            double progressRatio = (double) i / steps;

            // Calculer la position intermédiaire
            double checkX = from.getX() + dx * progressRatio;
            double checkY = from.getY() + dy * progressRatio;

            // Vérifier si cette position intermédiaire touche un obstacle
            if (touchesObstacle(new Coordonnate(checkX, checkY))) {
                return false; // La ligne de vue est bloquée
            }
        }
        return true; // La ligne de vue est claire
    }

    private boolean touchesObstacle(Coordonnate position) {
        for (Coordonnate obstacle : obstaclesList) {
            if (position.distance(obstacle) < Parameters.teamAMainBotRadius * 2) {
                return true; // La position touche un obstacle
            }
        }
        return false;
    }
    
    private void handleAlliesMessage() {
        // COMMUNICATION
        ArrayList<RobotMessage> messageQueue = new ArrayList<>();
        ArrayList<String> rawMessages = fetchAllMessages();
        for (String msg : rawMessages) {
            HashMap<String, String> messageMap = decomposeMessage(msg);
            RobotMessage robotMessage = new RobotMessage(messageMap.get("type"),
                    Double.parseDouble(messageMap.get("x")),
                    Double.parseDouble(messageMap.get("y")),
                    messageMap.get("by"));
            if (!robotMessage.by.equals(whoAmI.toString())) {
                messageQueue.add(robotMessage);
            }
        }
        for (RobotMessage message : messageQueue) {
            if (message.type.equals("wreck")) {
                addObstacle(new Coordonnate(message.x, message.y));
            }
            if (message.type.equals("position")) {
                allBotsPositions.put(message.by, new Coordonnate(message.x, message.y));
            }
            if (message.type.equals("readyToGo")) {
                countBattManBotReady++;
                if (countBattManBotReady == 2) {
                    goToTheOtherSideOnDeparture = true;
                }
            }
            
        }
        messageQueue.removeIf(message -> !message.type.equals("enemyPosition") );
        processEnnemyDetectedMessages(messageQueue);
        
    }


    private void processEnnemyDetectedMessages(ArrayList<RobotMessage> messageQueue) {
        if (!messageQueue.isEmpty()) {
            RobotMessage message = messageQueue.remove(0);
            sendLogMessage(
                    "by: " + message.by + " for " + message.type + " at " + new Coordonnate(message.x, message.y));
            if (!message.by.equals(whoAmI.toString())) {
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
                    1700), obstaclesList);
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
                broadcast("type:enemyPosition;x:" + enemyPosition.getX() + ";y:" + enemyPosition.getY() + ";by:"
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
                
                if (addObstacle(wreckPosition)) {
                    if(isSameDirection(r.getObjectDirection())) {
                        goToTheOtherSide();
                    }
                    broadcast("type:wreck;x:" + wreckPosition.getX() + ";y:" + wreckPosition.getY() + ";by:"
                        + whoAmI);
                }
            }
        }
    }

    public void takePlaceForDeparture() {
        switch (whoAmI) {
            case BATTMAN1:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 350 : 2650, 450);
                break;
            case BATTMAN2:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 100 : 2900, 1050);
                break;
            case BATTMAN3:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 350 : 2650, 1650);
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
        broadcast("type:position;x:" + myPosition.getX() + ";y:" + myPosition.getY() + ";by:" + whoAmI);
    }

    private ArrayList<Coordonnate> findPath(Coordonnate coord) {
        Coordonnate upLeft = new Coordonnate(400, 400);
        Coordonnate underUpLeft = new Coordonnate(400, 1400);
        Coordonnate righterUpLeft = new Coordonnate(2600, 400);
        Coordonnate downRight = new Coordonnate(2600, 1400);
        ArrayList<Coordonnate> res = new ArrayList<>();

        res.addAll(PathFinder.findPath(coord, underUpLeft, obstaclesList));
        res.addAll(PathFinder.findPath(underUpLeft, upLeft, obstaclesList));
        res.addAll(PathFinder.findPath(upLeft, righterUpLeft, obstaclesList));
        res.addAll(PathFinder.findPath(righterUpLeft, downRight, obstaclesList));
        return res;
    }

    private Coordonnate getPositionByDirectionAndDistance(Coordonnate position, double direction, double distance) {
        return new Coordonnate(position.getX() + distance * Math.cos(direction),
                position.getY() + distance * Math.sin(direction));
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
        return Math.abs(normalizeRadian(dir1) - normalizeRadian(dir2)) < Math.PI / 23;
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
        return true;
    }

    private void fireTarget(double enemyDirection, int enemyVelocity) {
        // enemyPosition = predictFuturePosition(enemyPosition, enemyDirection, enemyVelocity);
        // enemyDirection = CoordinateTransform.convertCartesianToPolar(myPosition, enemyPosition).getAngle();

        // Vérifier si la ligne de tir est claire
        if ( canFireEnemy()) {
            sendLogMessage("Firing at " + enemyDirection);
            fire(enemyDirection);
        }
    }
    
    private IRadarResult getClosestOpponentRadar(ArrayList<IRadarResult> radarResults) {
        IRadarResult closestOpponent = null;
        double minDistance = Double.MAX_VALUE;
        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.OpponentMainBot) {
                if (r.getObjectDistance() < minDistance) {
                    minDistance = r.getObjectDistance();
                    closestOpponent = r;
                }
            }
        }
        minDistance = Double.MAX_VALUE;
        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                if (r.getObjectDistance() < minDistance) {
                    minDistance = r.getObjectDistance();
                    closestOpponent = r;
                }
            }

        }
        return closestOpponent;
    }

    private RobotMessage getClosestOpponentMessage(ArrayList<RobotMessage> messages) {
        RobotMessage closestOpponent = null;
        double minDistance = Double.MAX_VALUE;
        for (RobotMessage m : messages) {
            if (m.type.equals("enemyPosition")) {
                Coordonnate enemyPos = new Coordonnate(m.x, m.y);
                if (myPosition.distance(enemyPos) < minDistance) {
                    minDistance = myPosition.distance(enemyPos);
                    closestOpponent = m;
                }
            }
        }
        return closestOpponent;
    }
}
