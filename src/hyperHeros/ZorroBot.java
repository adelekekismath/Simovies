package hyperHeros;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

import java.util.ArrayList;
import java.util.HashMap;

public class ZorroBot  extends Brain{

    boolean runThereIsDanger = false;

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
    private static HashMap<botName, Coordonnate> allAlliesPositions = new HashMap<>();

    public ZorroBot() {
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
        if (getHealth() > 0)
            processAlliesMessages();

        treatWhatIamSeeing();

        runThereIsDanger = false;
        if (getHealth() <= 0) {
            sendMessageToAllies("type:someOneIsDead;x:" + myPosition.getX() + ";y:" + myPosition.getY() + ";by:" + whoAmI);
            return;
        }

        if (!runThereIsDanger) {
            // Suivre le chemin si disponible
            if (currentObjectiveReached || targetObjective == null) {
                if (!pathToFollow.isEmpty()) {
                    targetObjective = pathToFollow.get(0);
                    pathToFollow.remove(0);
                } else {
                    goToTheOtherSide();
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
                if (addObstacle(wreckPosition))
                    sendMessageToAllies("type:someOneIsDead;x:" + wreckPosition.getX() + ";y:" + wreckPosition.getY()
                            + ";by:" + whoAmI);
                break;
            }
            if (r.getObjectType() == IRadarResult.Types.BULLET
                    || r.getObjectType() == IRadarResult.Types.OpponentMainBot
                    || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                if (r.getObjectType() != IRadarResult.Types.OpponentSecondaryBot) {
                    runThereIsDanger = true;
                    currentState = currentState == STATE.MOVEBACKSTATE ? STATE.MOVESTATE : STATE.MOVEBACKSTATE;
                }
                Coordonnate enemyPosition = getPositionByDirectionAndDistance(myPosition, r.getObjectDirection(), r.getObjectDistance());
                sendMessageToAllies("type:enemyPosition;x:" + enemyPosition.getX() + 50 + ";y:" + enemyPosition.getY() + 50 + ";by:" + whoAmI + ";direction:" + r.getObjectDirection());
                break;
            }
        }
    }

    private void processAlliesMessages(){
        ArrayList<String> messages = fetchAllMessages();
        ArrayList<String> allMessagesFromAllies = new ArrayList<>();
        if (!messages.isEmpty())
            allMessagesFromAllies.addAll(messages);
        if (!allMessagesFromAllies.isEmpty()) {
            String msg = allMessagesFromAllies.remove(0);
            HashMap<String, String> messageMap = decomposeMessage(msg);
            if (!messageMap.get("by").equals(whoAmI.toString())) {
                sendLogMessage("Received: " + "by: " + messageMap.get("by") + " for " + messageMap.get("type") + " y:"
                        + messageMap.get("y"));
                if (!messageMap.get("by").equals(whoAmI.toString()) && "someOneIsDead".equals(messageMap.get("type"))) {
                    addObstacle(new Coordonnate(Double.parseDouble(messageMap.get("x")),
                            Double.parseDouble(messageMap.get("y"))));
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
        sendMessageToAllies("type:position;x:" + myPosition.getX() + ";y:" + myPosition.getY() + ";by:" + whoAmI);
    }

    private boolean outOfBounds(Coordonnate position) {
        return position.getX() <= 100 || position.getX() >= 3000 || position.getY() <= 100 || position.getY() >= 2000;
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
                if (botSide == SIDE.LEFT) {
                    targetObjective = new Coordonnate(Parameters.teamASecondaryBot1InitX, 600);
                } else {
                    targetObjective = new Coordonnate(Parameters.teamBSecondaryBot1InitX, 600);
                }
                break;
            case ZORRO2:
                if (botSide == SIDE.LEFT) {
                    targetObjective = new Coordonnate(Parameters.teamASecondaryBot2InitX, 1500);
                } else {
                    targetObjective = new Coordonnate(Parameters.teamBSecondaryBot2InitX, 1500);
                }
                break;
            default:
                break;
        }
    }

    public void goToTheOtherSide() {
        int destinationX = myPosition.getX() < 1400 ? 2800 : 200;

        if (whoAmI == botName.ZORRO1) {
            pathToFollow = PathFinder.findPath(myPosition, new Coordonnate(destinationX, 600), obstaclesList);
        } else if (whoAmI == botName.ZORRO2) {
            pathToFollow = PathFinder.findPath(myPosition, new Coordonnate(destinationX, 1600), obstaclesList);
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