package hyperHeros;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

import java.util.ArrayList;
import java.util.HashMap;

public class HyperHerosSecondary  extends Brain{

    boolean runThereIsDanger = false;

    private STATE currentState;
    private Coordonnate myPosition;
    private SIDE botSide;
    private ArrayList<Coordonnate> obstaclesList = new ArrayList<>();
    private botName whoAmI;
    private ArrayList<Coordonnate> pathToFollow = new ArrayList<>();
    private boolean currentObjectiveReached = false;
    private Coordonnate targetObjective;
    private static HashMap<botName, Coordonnate> allAlliesPositions = new HashMap<>();
    private boolean goToTheOtherSideOnDeparture = false;
    private ORDER currentOrder;

    private enum ORDER {
        WAITINGTOGO, GOING
    }

    public HyperHerosSecondary() {
        super();
    }

    public void activate() {
        try {
            nominateBot();
        } catch (Exception e) {
            e.printStackTrace();
        }
        takePlaceForDeparture();
        currentOrder = ORDER.WAITINGTOGO;
        currentState = STATE.SINK;
    }


    public void step() {
        currentState = STATE.SINK;
        if (getHealth() > 0)
            processAlliesMessages();
        runThereIsDanger = false;
        treatWhatIamSeeing();
        if (getHealth() <= 0) {
            broadcast("type:wreck;x:" + myPosition.getX() + ";y:" + myPosition.getY() + ";by:" + whoAmI);
            return;
        }

        if (runThereIsDanger) {
            // En cas de danger, le robot recule d'un pas
            currentState = STATE.SINK;
        } else {
            // Suivre le chemin si disponible
            if (currentObjectiveReached) {
                if (!pathToFollow.isEmpty()) {
                    targetObjective = pathToFollow.get(0);
                    pathToFollow.remove(0);
                } else {
                    if (goToTheOtherSideOnDeparture) {
                        goToTheOtherSideOnDeparture = false;
                        currentOrder = ORDER.GOING;
                        goToTheOtherSide();
                    } else if(currentOrder == ORDER.GOING)
                        pathToFollow = findPath(myPosition);
                }
            }
            goToTarget();
        }

        if (currentState == STATE.MOVESTATE) {
            walk(false);
        } else if (currentState == STATE.MOVEBACKSTATE) {
            walk(true);
        } else if (currentState == STATE.TURNRIGHTSTATE) {
            stepTurn(Parameters.Direction.RIGHT);
        } else if (currentState == STATE.TURNLEFTSTATE) {
            stepTurn(Parameters.Direction.LEFT);
        } else if (currentState == STATE.SINK) {
            // Do nothing
        }
    }
    
    private void treatWhatIamSeeing() {
        ArrayList<IRadarResult> radarResults = detectRadar();
        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.Wreck) {
                Coordonnate wreckPosition = getPositionByDirectionAndDistance(myPosition, r.getObjectDirection(),
                        r.getObjectDistance());
                if (addObstacle(wreckPosition)) {
                    broadcast("type:wreck;x:" + wreckPosition.getX() + ";y:" + wreckPosition.getY()
                            + ";by:" + whoAmI);
                }
            }
            if (r.getObjectType() == IRadarResult.Types.BULLET
                    || r.getObjectType() == IRadarResult.Types.OpponentMainBot
                    || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                if (r.getObjectType() != IRadarResult.Types.BULLET) {
                    runThereIsDanger = true;
                }
                if (r.getObjectType() == IRadarResult.Types.OpponentMainBot
                        || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                    Coordonnate enemyPosition = getPositionByDirectionAndDistance(myPosition, r.getObjectDirection(),
                            r.getObjectDistance());
                    sendLogMessage("Detected enemy at " + enemyPosition);
                    broadcast("type:enemyPosition;x:" + enemyPosition.getX() + ";y:" + enemyPosition.getY()
                            + ";by:" + whoAmI + ";direction:" + r.getObjectDirection() + ";enemyType:"+ r.getObjectType().toString());
                }
                break;
            }
        }
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

    private void processAlliesMessages(){
        ArrayList<String> messages = fetchAllMessages();
        ArrayList<String> allMessagesFromAllies = new ArrayList<>();
        if (!messages.isEmpty())
            allMessagesFromAllies.addAll(messages);
        for (String msg : allMessagesFromAllies) {
                HashMap<String, String> messageMap = decomposeMessage(msg);
                if (!messageMap.get("by").equals(whoAmI.toString())) {
                    sendLogMessage(
                            "Received: " + "by: " + messageMap.get("by") + " for " + messageMap.get("type") + " at "
                                    + new Coordonnate(Double.parseDouble(messageMap.get("x")),
                                            Double.parseDouble(messageMap.get("y"))));
                    if ("wreck".equals(messageMap.get("type"))) {
                        addObstacle(new Coordonnate(Double.parseDouble(messageMap.get("x")),
                                Double.parseDouble(messageMap.get("y"))));
                    }
                    if (whoAmI == botName.ZORRO1 && "youCanStartZorro1".equals(messageMap.get("type"))) {
                        sendLogMessage(msg);
                        goToTheOtherSideOnDeparture = true;
                    }
                    if (whoAmI == botName.ZORRO2 && "youCanStartZorro2".equals(messageMap.get("type"))) {
                        sendLogMessage(msg);
                        goToTheOtherSideOnDeparture = true;
                    }
                }
        }
    }

    private void walk(boolean back) {
        Coordonnate newBotPosition = getNextPosition(back ? -1 : 1);
        for (IRadarResult r : detectRadar()) {
            if (newBotPosition.distance(getPositionByDirectionAndDistance(newBotPosition, r.getObjectDirection(),
                    r.getObjectDistance())) < Parameters.teamAMainBotRadius * 2 || outOfBounds(newBotPosition)) {
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

    private boolean outOfBounds(Coordonnate position) {
        return position.getX() <= 200 || position.getX() >= 3000 || position.getY() <= 200 || position.getY() >= 2000;
    }

    private Coordonnate getPositionByDirectionAndDistance(Coordonnate position, double direction, double distance) {
        return new Coordonnate(position.getX() + distance * Math.cos(direction),
                position.getY() + distance * Math.sin(direction));
    }

    private Coordonnate getNextPosition(double back) {
        return new Coordonnate(
                myPosition.getX() + (back * Parameters.teamASecondaryBotSpeed) * Math.cos(myGetHeading()),
                myPosition.getY() + (back * Parameters.teamASecondaryBotSpeed) * Math.sin(myGetHeading()));
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

    public void takePlaceForDeparture() {
        switch (whoAmI) {
            case ZORRO1:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 400 : 2600, 650);
                break;
            case ZORRO2:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 400 : 2600, 1450);
                break;
            default:
                break;
        }
    }

    public void goToTheOtherSide() {
        int destinationX = botSide == SIDE.LEFT ? 2600 : 400;

        if (whoAmI == botName.ZORRO1) {
            pathToFollow = PathFinder.findPath(myPosition, new Coordonnate(destinationX, 500), obstaclesList);
        } else if (whoAmI == botName.ZORRO2) {
            pathToFollow = PathFinder.findPath(myPosition, new Coordonnate(destinationX, 1500), obstaclesList);
        }
    }

    public void nominateBot()
            throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
        boolean up = false, down = false;
        for (IRadarResult r : detectRadar()) {
            if (r.getObjectType() == IRadarResult.Types.TeamSecondaryBot) {
                up |= (r.getObjectDirection() == Parameters.NORTH);
                down |= (r.getObjectDirection() == Parameters.SOUTH);
            }
        }
        botSide = getHeading() == Parameters.EAST ? SIDE.LEFT : SIDE.RIGHT;
        String botNumber = up ? "2" : down ? "1" : "";
        String prefix = botSide == SIDE.LEFT ? "teamASecondaryBot" : "teamBSecondaryBot";
        double initX = Parameters.class.getField(prefix + botNumber + "InitX").getDouble(null) + 50;
        double initY = Parameters.class.getField(prefix + botNumber + "InitY").getDouble(null) + 50;

        whoAmI = botName.valueOf("ZORRO" + botNumber);
        myPosition = new Coordonnate(initX, initY);
        sendLogMessage("I am " + whoAmI + " at " + myPosition);

        for (botName bot : botName.values()) {
            if (bot != whoAmI) {
                allAlliesPositions.put(bot, null);
            }
        }
    }
}