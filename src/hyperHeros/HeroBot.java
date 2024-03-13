
package hyperHeros;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IRadarResult;
import java.util.ArrayList;
import java.util.HashMap;

public abstract class HeroBot extends Brain {
    protected STATE currentState;
    protected Coordonnate myPosition;
    protected SIDE botSide;
    protected static ArrayList<Coordonnate> obstaclesList = new ArrayList<>();
    protected botName whoAmI;
    protected ArrayList<Coordonnate> pathToFollow = new ArrayList<>();
    protected boolean currentObjectiveReached = false;
    protected Coordonnate targetObjective;
    protected boolean goToTheOtherSideOnDeparture = false;
    protected ArrayList<String> allMessagesFromAllies = new ArrayList<>();
    protected ArrayList<String> historySendMessages = new ArrayList<>();
    protected long lastMessageTime = 0; // Dernier moment où un message a été envoyé
    protected final long messageCooldown = 1000; // Temps minimum en millisecondes entre les messages

    public HeroBot() {
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

    protected abstract void takePlaceForDeparture();

    protected abstract void goToTheOtherSide();

    protected abstract void nominateBot() throws Exception;

    public abstract void step();

    protected void sendMessageToAllies(String message) {
        if (System.currentTimeMillis() - lastMessageTime > messageCooldown) {
            if (!historySendMessages.contains(message)) {
                broadcast(message);
                historySendMessages.add(message);
                lastMessageTime = System.currentTimeMillis();
            }
        }
    }

    protected Coordonnate getNextPosition(double back) {
        return new Coordonnate(
                myPosition.getX() + back * Parameters.teamAMainBotSpeed * Math.cos(myGetHeading()),
                myPosition.getY() + back * Parameters.teamAMainBotSpeed * Math.sin(myGetHeading()));
    }

    protected double myGetHeading() {
        return normalizeRadian(getHeading());
    }

    protected double normalizeRadian(double angle) {
        double result = angle;
        while (result < 0)
            result += 2 * Math.PI;
        while (result >= 2 * Math.PI)
            result -= 2 * Math.PI;
        return result;
    }

    protected HashMap<String, String> decomposeMessage(String message) {
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

    public void addObstacle(Coordonnate pos) {
        for (Coordonnate obtacle : obstaclesList) {
            if (Math.abs(obtacle.getX() - pos.getX()) < 1 && Math.abs(obtacle.getY() - pos.getY()) < 1)
                return;
        }
        obstaclesList.add(pos);
    }
}
