package hyperHeros;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IRadarResult;

import java.util.ArrayList;
import java.util.HashMap;

public class BatmanBot extends Brain {

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
    private ArrayList<String> allMessagesFromAllies = new ArrayList<>();
    private ArrayList<String> historySendMessages = new ArrayList<>();
    private long lastMessageTime = 0; // Dernier moment où un message a été envoyé
    private final long messageCooldown = 1000; // Temps minimum en millisecondes entre les messages

    public BatmanBot() {
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
            sendLogMessage("by: " + messageMap.get("by") + "for" + messageMap.get("type") + "x:"
                    + messageMap.get("x"));
            if (!messageMap.get("by").equals(whoAmI.toString())) {
                // if ("enemyPosition".equals(messageMap.get("type"))) {
                //     if (myPosition.distance(new Coordonnate(Double.parseDouble(messageMap.get("x")),
                //             Double.parseDouble(messageMap.get("y")))) < 1000) {
                //         targetObjective = new Coordonnate(Double.parseDouble(messageMap.get("x")) - 100,
                //                 Double.parseDouble(messageMap.get("y")) - 100);
                //     }
                // } 
                if ("someOneIsDead".equals(messageMap.get("type"))) {
                    addObstacle(new Coordonnate(Double.parseDouble(messageMap.get("x")),
                            Double.parseDouble(messageMap.get("y"))));
                }
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
            sendMessageToAllies(
                    "type:someOneIsDead;x:" + myPosition.getX() + ";y:" + myPosition.getY() + ";by:" + whoAmI);
            return;
        }
        // Suivre le chemin si disponible
        if (currentObjectiveReached) {
            if (!pathToFollow.isEmpty()) {
                setObjective(pathToFollow.get(0));
                pathToFollow.remove(0);
            } else {
                if (goToTheOtherSideOnDeparture)
                {
                    goToTheOtherSideOnDeparture = false;
                    goToTheOtherSide();
                }
                else pathToFollow = findPath(myPosition);
            }
        }
        goToTarget();

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
        goToTheOtherSide();
    }

    public void goToTheOtherSide() {
        int destinationX = botSide == SIDE.LEFT ? 2900 : 2000;

        if (whoAmI == botName.BATTMAN1) {
            pathToFollow = BreadthFirstSearch.findPath(myPosition, new Coordonnate(destinationX,
                    400), obstaclesList);
        } else if (whoAmI == botName.BATTMAN2) {
            pathToFollow = BreadthFirstSearch.findPath(myPosition, new Coordonnate(destinationX,
                    1100), obstaclesList);
        } else if (whoAmI == botName.BATTMAN3) {
            pathToFollow = BreadthFirstSearch.findPath(myPosition, new Coordonnate(destinationX,
                    1800), obstaclesList);
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

    private void takePlaceForDeparture() {
        switch (whoAmI) {
            case BATTMAN1:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 500 : 2500, 400);
                break;
            case BATTMAN2:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 400 : 2600, 1000);
                break;
            case BATTMAN3:
                targetObjective = new Coordonnate(botSide == SIDE.LEFT ? 500 : 2500, 1600);
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
            } else if (r.getObjectType() == IRadarResult.Types.Wreck) {
                Coordonnate wreckPosition = getPositionByDirectionAndDistance(myPosition, r.getObjectDirection(),
                        r.getObjectDistance());
                addObstacle(wreckPosition);
                sendMessageToAllies("type:someOneIsDead;x:" + wreckPosition.getX() + ";y:" + wreckPosition.getY() + ";by:"
                        + whoAmI);
            }
        }
    }
    
    private void sendMessageToAllies(String message) {
        if (System.currentTimeMillis() - lastMessageTime > messageCooldown) {
            if(!historySendMessages.contains(message)) {
                broadcast(message);
                historySendMessages.add(message);
            }
            lastMessageTime = System.currentTimeMillis();
        }
    }

    private void  goToTarget() {
        currentObjectiveReached = false;
        PolarCoordinate polarInstance = CoordinateTransform.convertCartesianToPolar(myPosition, targetObjective);
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

    public Coordonnate getPositionByDirectionAndDistance(Coordonnate position, double direction, double distance) {
        return new Coordonnate(position.getX() + distance * Math.cos(direction),
                position.getY() + distance * Math.sin(direction));
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
