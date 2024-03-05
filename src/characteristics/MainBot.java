package characteristics;

import robotsimulator.Brain;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

import java.util.ArrayList;

public class MainBot  extends Brain{
    
    private static final int INITSTATE = 1;
    private static final int MOVEESTATE = 2;
    private static final int FIRESTATE = 3;
    private static final int TURNLEFTSTATE = 4;
    private static final int TURNRIGHTSTATE = 5;
    private static final int OPPONENTDETECTEDSTATE = 6;
    private static final int OPPONENTTURNEDSTATE = 7;


    // VARIABLES
    private int state;
    private double myX, myY;
    private boolean isMoving;
    private double backAngle;
    private double endDirection;
    private double lastDirection;
    private double endCounter;
    private int id;
    private static int idConter = 0;


    public MainBot() {
        super();
        id = idConter;
        idConter++;
    }

    public void activate() {
        state = INITSTATE;
    }

    public void step() {

        // switch (state) {
        //     case INITSTATE:
        //         System.out.println("INITSTATE");
        //         move();
        //         state = MOVEESTATE;
        //         break;
        //     case MOVEESTATE:
        //         if (id == 0) {
        //             OldAngle_0 = 0;
        //             FrontSensorResult radarResult = detectFront();
        //             if (radarResult.getObjectType() == IFrontSensorResult.Types.OpponentMainBot
        //                     || radarResult.getObjectType() == IFrontSensorResult.Types.OpponentSecondaryBot) {
        //                 System.out.println("MoveEstate: Opponent detected");
        //                 state = FIRESTATE;
        //                 return;
        //             }
        //             else if (radarResult.getObjectType() == IFrontSensorResult.Types.WALL) {
        //                 System.out.println("MoveEstate: WALL detected");
        //                 state = TURNLEFTSTATE;
        //                 return;
        //             }
        //             else {
        //                 move();
        //                 System.out.println("MoveEstate: No opponent detected");
        //                 return;
        //             }
        //         }
        //         break;
        //     case TURNLEFTSTATE:
        //         if (id == 0) {
        //             if (isHeading(turn90) && turnAngle_0 == 90) {
        //                 System.out.println("TurnLeftState: Heading 90");
        //                 state = MOVEESTATE;
        //                 turnAngle_0 = 0;
        //                 return;
        //             }
        //             if(isHeading(0) && turnAngle_0 == 0){
        //                 System.out.println("TurnLeftState: Heading 0");
        //                 turnAngle_0 = 90;
        //                 state = MOVEESTATE;
        //                 return;
        //             }
        //             else {
        //                 System.out.println("TurnLeftState: Heading not 90");
        //                 stepTurn(Parameters.Direction.LEFT);
        //                 return;
        //             }
        //         }
        //         break;
        //     case FIRESTATE:
        //         if (id == 0) {
        //             IFrontSensorResult radarResult = detectFront();
        //             if (radarResult.getObjectType() == IFrontSensorResult.Types.Wreck) {
        //                 System.out.println("FireState: Wreck detected");
        //                 state = TURNLEFTSTATE;
        //                 return;
        //             }
        //             else if (radarResult.getObjectType() == IFrontSensorResult.Types.NOTHING) {
        //                 System.out.println("FireState: No wreck detected");
        //                 state = MOVEESTATE;
        //                 return;
        //             }
        //             else {
        //                 System.out.println("FireState: No wreck detected");
        //                 fire(getHeading());
        //                 return;
        //             }
        //         }
        //         break;

        //     default:
        //         break;
        // }

    }

    private boolean isHeading(double dir) {
        double heading = Math.abs(getHeading() % (2 * Math.PI));
        return Math.abs(Math.sin(heading-dir))< 0.01;
    }
}
