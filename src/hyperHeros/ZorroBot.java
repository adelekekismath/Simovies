package hyperHeros;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

import java.util.ArrayList;
import java.util.HashMap;

public class ZorroBot extends Brain {
    private STATE currentState;
    private Coordonnate myPosition;
    private SIDE botSide;
    private static ArrayList<Coordonnate> obstaclesList = new ArrayList<>();
    private botName whoAmI;
    private ArrayList<Coordonnate> pathToFollow = new ArrayList<>();
    private boolean currentObjectiveReached = false;
    private Coordonnate targetObjective;
    private boolean goToTheOtherSideOnDeparture = false;
    private ArrayList<String> allMessagesFromAllies = new ArrayList<>();
    private ArrayList<String> historySendMessages = new ArrayList<>();
    private boolean runThereAreBullets;
    private double enemyDetected = 0;
    private long lastMessageTime = 0; // Dernier moment où un message a été envoyé
    private final long messageCooldown = 1000; // Temps minimum en millisecondes entre les messages
    private long lastEnemyReportTime = 0;
    private final long reportInterval = 3000; // 5 secondes

    public ZorroBot() {
        super();
    }

    public void activate() {
        try {
            nominateBot();
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            e.printStackTrace();
        }
        takePlaceForDeparture();
        goToTheOtherSideOnDeparture = true;
        currentState = STATE.SINK;
    }

    public void step() {
        // COMMUNICATION
        ArrayList<String> messages = fetchAllMessages();
        if (!messages.isEmpty()) allMessagesFromAllies.addAll(messages);
        if (!allMessagesFromAllies.isEmpty()) {
            String msg = allMessagesFromAllies.remove(0);
            HashMap<String, String> messageMap = decomposeMessage(msg);
            sendLogMessage("Received: " + "by: " + messageMap.get("by") + " for " + messageMap.get("type")+ " x:" + messageMap.get("x"));
            if (!messageMap.get("by").equals(whoAmI.toString()) && "someOneIsDead".equals(messageMap.get("type"))) {
                addObstacle(new Coordonnate(Double.parseDouble(messageMap.get("x")) , Double.parseDouble(messageMap.get("y"))));
            }
        }
        runThereAreBullets = false;
        ArrayList<IRadarResult> radarResults = detectRadar();

        if (getHealth() <= 0) {
            sendMessageToAllies("type:someOneIsDead;x:" + myPosition.getX() + ";y:" + myPosition.getY() + ";by:" + whoAmI);
            return;
        }
            
        for (IRadarResult r : radarResults) {
            if (r.getObjectType() == IRadarResult.Types.BULLET  ) {
                currentState = STATE.MOVEBACKSTATE;
                runThereAreBullets = true;
                sendLogMessage("I see a bullet, I'm moving back");
                return;
            }
            if (r.getObjectType() == IRadarResult.Types.OpponentMainBot || r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                Coordonnate enemyPosition = getPositionByDirectionAndDistance(myPosition, r.getObjectDirection(),r.getObjectDistance());
                sendMessageToAllies("type:enemyPosition;x:" + enemyPosition.getX() + ";y:" + enemyPosition.getY() + ";by:"
                        + whoAmI);
            }
            if (r.getObjectType() == IRadarResult.Types.Wreck) {
                Coordonnate wreckPosition = getPositionByDirectionAndDistance(myPosition, r.getObjectDirection(), r.getObjectDistance());
                addObstacle(wreckPosition);
                sendMessageToAllies("type:someOneIsDead;x:" + wreckPosition.getX() + ";y:" + wreckPosition.getY() + ";by:" + whoAmI);
            }
        }

        if ( !runThereAreBullets) {
            // Suivre le chemin si disponible
            if (currentObjectiveReached) {
                if (!pathToFollow.isEmpty()) {
                    targetObjective = pathToFollow.get(0);
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

    private void takePlaceForDeparture() {
        switch (whoAmI) {
            case ZORRO1:
                if (botSide == SIDE.LEFT) {
                    targetObjective = new Coordonnate(500, 700);
                } else {
                    targetObjective = new Coordonnate(2500, 700);
                }
                break;
            case ZORRO2:
                if (botSide == SIDE.LEFT) {
                    targetObjective = new Coordonnate(500, 1300);
                } else {
                    targetObjective = new Coordonnate(2500, 1300);
                }
                break;
            default:
                break;
        }
    }

    private void walk(boolean back) {

        Coordonnate newBotPosition = getNextPosition(back ? -1 : 1);
        for (IRadarResult r : detectRadar()) {
            if (newBotPosition.distance(getPositionByDirectionAndDistance(newBotPosition, r.getObjectDirection(),
                    r.getObjectDistance())) < Parameters.teamASecondaryBotRadius * 2) {
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

    private void sendMessageToAllies(String message) {
        if (System.currentTimeMillis() - lastMessageTime > messageCooldown) {
            if (!historySendMessages.contains(message)) {
                broadcast(message);
                historySendMessages.add(message);
            }
            lastMessageTime = System.currentTimeMillis();
        }
    }

    public Coordonnate getPositionByDirectionAndDistance(Coordonnate position, double direction, double distance) {
        return new Coordonnate(position.getX() + distance * Math.cos(direction),
                position.getY() + distance * Math.sin(direction));
    }

    private Coordonnate getNextPosition(double back) {
        return new Coordonnate(
                myPosition.getX() + back * Parameters.teamAMainBotSpeed * Math.cos(myGetHeading()),
                myPosition.getY() + back * Parameters.teamAMainBotSpeed * Math.sin(myGetHeading()));
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

    private ArrayList<Coordonnate> findPath(Coordonnate coord) {
        Coordonnate upLeft = new Coordonnate(200, 200);
        Coordonnate underUpLeft = new Coordonnate(400, 1000);
        Coordonnate righterUpLeft = new Coordonnate(1200, 200);
        Coordonnate downRight = new Coordonnate(2600, 1800);
        Coordonnate upperDownRight = new Coordonnate(2800, 1000);
        ArrayList<Coordonnate> res = new ArrayList<>();

        res.addAll(BreadthFirstSearch.findPath(coord, underUpLeft, obstaclesList));
        res.addAll(BreadthFirstSearch.findPath(underUpLeft, upLeft, obstaclesList));
        res.addAll(BreadthFirstSearch.findPath(upLeft, righterUpLeft, obstaclesList));
        res.addAll(BreadthFirstSearch.findPath(righterUpLeft, downRight, obstaclesList));
        res.addAll(BreadthFirstSearch.findPath(downRight, upperDownRight, obstaclesList));
        return res;
    }

    public void goToTheOtherSide() {
        int destinationX = botSide == SIDE.LEFT ? 2900 : 2000;

        if (whoAmI == botName.ZORRO1) {
            pathToFollow = BreadthFirstSearch.findPath(myPosition, new Coordonnate(destinationX, 600), obstaclesList);
        } else if (whoAmI == botName.ZORRO2) {
            pathToFollow = BreadthFirstSearch.findPath(myPosition, new Coordonnate(destinationX, 1200), obstaclesList);
        }
    }

    public void addObstacle(Coordonnate pos) {
        for (Coordonnate obtacle : obstaclesList) {
            if (Math.abs(obtacle.getX() - pos.getX()) < 1 && Math.abs(obtacle.getY() - pos.getY()) < 1)
                return;
        }
        obstaclesList.add(pos);
        goToTheOtherSide();
    }

    private void nominateBot()
            throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
        boolean up = false, down = false;
        for (IRadarResult r : detectRadar()) {
            up |= (r.getObjectDirection() == Parameters.NORTH);
            down |= (r.getObjectDirection() == Parameters.SOUTH);
        }
        botSide = getHeading() == Parameters.EAST ? SIDE.LEFT : SIDE.RIGHT;
        String botNumber = up ? "1" : down ? "2" : "";
        String prefix = botSide == SIDE.LEFT ? "teamASecondaryBot" : "teamBSecondaryBot";
        double initX = Parameters.class.getField(prefix + botNumber + "InitX").getDouble(null) + 50;
        double initY = Parameters.class.getField(prefix + botNumber + "InitY").getDouble(null) + 50;

        whoAmI = botName.valueOf("ZORRO" + botNumber);
        myPosition = new Coordonnate(initX, initY);
        sendLogMessage("I am " + whoAmI + " at " + myPosition);
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

    public static boolean wallIsDetected(Coordonnate pos) {
        return (pos.x <= 100.0 || pos.x >= 3000.0 || pos.y <= 100.0 || pos.y >= 2000.0);
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
}