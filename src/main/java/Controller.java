import org.apache.commons.io.FileUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Controller {
    private double roomDistance = 250;
    private double rightThreshDistance = 120;
    private double leftThreshDistance = 120;
    private double leftDistanceLand = 40;

    private double frontThreshDistance = 120;

    private double backThreshDistance = 120;

    private double distanceVariance = 20;

    private double groundDistanceVariance = 10;

    private double landingThreshold = 20;
    //TODO Implement necessary libraries/dependencies to control the flight controller

    private double xVel;
    private double xVelThreshPos = 20;
    private double yVel;
    private double yVelThreshPos = 20;
    private double zVel;
    private double zVelThreshPos = 20;
    private boolean finishedRotating = false;

    public Controller(UltraSens sensor) {
        //TODO init if needed
        mySensor = sensor;
        for (int i = 0; i < nrOfSamples; i++) {
            timeTable[i] = 0;
            for (int j = 0; j < 5; j++) {
                sensorData[j][i] = 0;
            }
        }
        Main.logger.info("entering liftoff");
    }

    private UltraSens mySensor;

    private myStage currStage = myStage.LIFTOFF;

    private boolean inflight = true;

    private final int nrOfSamples = 20;

    private int[][] sensorData = new int[5][nrOfSamples]; // Bottom, Front, Right, Back, Left

    private long[] timeTable = new long[nrOfSamples];

    private double motorLow = 1400;

    private double motorRest = 1500;

    private double motorHigh = 1600;

    private double thrustHigh = 1900;

    private double thrustRest = 1700;

    private double thrustLow = 1400;

    private double groundThresh = 40;

    private double rightPassedRoomDistance = 310;


    public enum myStage {
        LIFTOFF,
        FLYIN1,
        FLYIN2,
        ROTATE,
        FLYOUT,
        LANDING
    }


    public void startFlight() {
        Process timelapse = null;
        long captureTime = 0;

        while (inflight) {
            //propagate old values in array
            for (int i = nrOfSamples - 1; i > 0; i--) {
                for (int j = 0; j < 5; j++) {
                    sensorData[j][i] = sensorData[j][i - 1];
                }
                timeTable[i] = timeTable[i - 1];
            }

            //TODO maybe in parallel
            //TODO clip/ignore false data

            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            int bottomVal = mySensor.measureBottom();
            int frontVal = mySensor.measureFront();
            int rightVal = mySensor.measureRight();
            int backVal = mySensor.measureBack();
            int leftVal = mySensor.measureLeft();

            if (bottomVal != -1) {
                sensorData[0][0] = bottomVal;
            }
            if (frontVal != -1) {
                sensorData[1][0] = frontVal;
            }
            if (rightVal != -1) {
                sensorData[2][0] = rightVal;
            }
            if (backVal != -1) {
                sensorData[3][0] = backVal;
            }
            if (leftVal != -1) {
                sensorData[4][0] = leftVal;
            }

            /*
            Main.logger.info("SensorDown = " + String.valueOf(sensorData[0][0]));
            Main.logger.info("SensorFront = " + String.valueOf(sensorData[1][0]));
            Main.logger.info("SensorRight = " + String.valueOf(sensorData[2][0]));
            Main.logger.info("SensorBack = " + String.valueOf(sensorData[3][0]));
            Main.logger.info("SensorLeft = " + String.valueOf(sensorData[4][0]));*/


            xVel = ((sensorData[1][1] - sensorData[1][0]) + (sensorData[3][0] - sensorData[3][1])) / 2;
            yVel = ((sensorData[2][1] - sensorData[2][0]) + (sensorData[4][0] - sensorData[4][1])) / 2;
            zVel = ((sensorData[0][1] - sensorData[0][0]));

            timeTable[0] = System.currentTimeMillis();

            xVel = ((double) (sensorData[1][1] - sensorData[1][0]) + (sensorData[3][0] - sensorData[3][1])) / (double) (2 * (timeTable[0] - timeTable[1]) * 1000);
            yVel = ((double) (sensorData[2][1] - sensorData[2][0]) + (sensorData[4][0] - sensorData[4][1])) / (double) (2 * (timeTable[0] - timeTable[1]) * 1000);
            zVel = ((double) (sensorData[0][1] - sensorData[0][0]) / (double) ((timeTable[0] - timeTable[1]) * 1000));

            switch (currStage) {
                case LIFTOFF:
                    liftOff();

                    //TODO Calculate velocities && implement isCentered
                    if (isCentered(false) && isStable()) {
                        currStage = myStage.FLYIN1;
                        Main.logger.info("----------------");
                        Main.logger.info("Entering flying1");
                        Main.logger.info("----------------");
                    }

                    break;
                case FLYIN1:
                    flyIn1();

                    if (sensorData[2][0] > rightPassedRoomDistance) {
                        currStage = myStage.FLYIN2;
                        Main.logger.info("----------------");
                        Main.logger.info("Entering flying2");
                        Main.logger.info("----------------");
                    }

                    break;
                case FLYIN2:
                    flyIn2();

                    if (isStable() && isCentered(true)) {
                        currStage = myStage.ROTATE;
                        Main.logger.info("-----------------");
                        Main.logger.info("Entering rotation");
                        Main.logger.info("-----------------");

                        // Take timelapse
                        try {
                            FileUtils.cleanDirectory(new File("images"));
                            ProcessBuilder processBuilder = new ProcessBuilder("raspistill", "-t", "0", "-tl", "1000", "-w", "1640", "-h", "922", "-q", "10", "-o", "images/image%02d.jpg");
                            timelapse = processBuilder.start();
                            captureTime = System.currentTimeMillis();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case ROTATE:
                    if ((sensorData[2][0] > rightPassedRoomDistance && System.currentTimeMillis() > captureTime + 7000) || System.currentTimeMillis() > captureTime + 15000) {
                        if (timelapse != null) {
                            timelapse.destroy();
                        }

                        FlightController.setYaw(1600);
                        currStage = myStage.FLYOUT;
                        Main.logger.info("---------------");
                        Main.logger.info("Entering flyout");
                        Main.logger.info("---------------");
                    }
                    break;
                case FLYOUT:
                    flyIn2();

                    ArrayList<BufferedImage> images = new ArrayList<>();
                    try {
                        File dir = new File("images");
                        File[] directoryListing = dir.listFiles();
                        if (directoryListing != null) {
                            for (File child : directoryListing) {
                                images.add(ImageIO.read(child));
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Process images
                    int[] ints = ImageProcessing.getPosition(images);
                    Main.logger.info("It seems that engine " + ints[0] + " is broken.");
                    BufferedImage bufferedImage = images.get(ints[1]);
                    File outputfile = new File("broken.jpg");
                    try {
                        ImageIO.write(bufferedImage, "jpg", outputfile);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (isStable() && isInLandingPos()) {
                        currStage = myStage.LANDING;
                        Main.logger.info("----------------");
                        Main.logger.info("Entering landing");
                        Main.logger.info("----------------");
                    }
                    break;
                case LANDING:
                    FlightController.setThrust(1400);
                    if (sensorData[0][0] < landingThreshold) {
                        inflight = false;
                        FlightController.setThrust(1000);
                        FlightController.setRoll(1500);
                        FlightController.setPitch(1500);
                        FlightController.setYaw(1500);
                    }
                    break;
                default:
                    break;
            }
        }

        Main.logger.info("Landing successful!");
    }

    private boolean isStable() {
        return Math.abs(xVel) < xVelThreshPos && Math.abs(yVel) < yVelThreshPos && Math.abs(zVel) < zVelThreshPos;
    }

    private void liftOff() {
        stabilizeHeight();
        stabilizeCenterFontBack();
        stabilizeRight();

    }

    private void flyIn1() {
        stabilizeHeight();
        stabilizeCenterFontBack();
        FlightController.setRoll((int) ((motorRest - motorLow) / 2 + motorLow));

    }

    private void flyIn2() {
        Camera.startCamera();
        stabilizeHeight();
        stabilizeCenterFontBack();
        stabilizeLeft();

    }

    private void landingOrientate() {
        //TODO DO
    }

    private void land() {

        FlightController.setThrust(1450);
        stabilizeCenterFontBack();
        stabilizeRight();

    }

    private void stabilizeHeight() {
        int currHeight = sensorData[0][0];

        if (currHeight < groundThresh) {
            FlightController.setThrust((int) thrustHigh);
        } else if (currHeight > groundThresh + groundDistanceVariance) {
            FlightController.setThrust((int) thrustLow);
        } else {
            FlightController.setThrust((int) thrustRest);
        }
    }

    private void stabilizeCenterFontBack() {
        int currBack = sensorData[3][0];
        int currFront = sensorData[1][0];

        //TODO CAST DOUBLE TO GET GOOD MULTIPLIER
        int frontBackThrust = (int) ((((currFront - currBack) + roomDistance) / (2 * roomDistance)) * (motorHigh - motorLow) + motorLow);

        FlightController.setPitch(frontBackThrust);

    }

    private void stabilizeRight() {
        int currRight = sensorData[2][0];

        int sideThrust = (int) Math.min(thrustHigh, ((((currRight - rightThreshDistance) + roomDistance / 2) / (roomDistance)) * (motorHigh - motorLow) + motorLow));

        FlightController.setRoll(sideThrust);

    }

    private void stabilizeLeft() {
        int currLeft = sensorData[4][0];

        int sideThrust = (int) Math.max(thrustLow, ((1 - (((currLeft - leftThreshDistance) + roomDistance / 2) / (roomDistance))) * (motorHigh - motorLow) + motorLow));

        FlightController.setRoll(sideThrust);

    }

    //bullshitcode do not use!
    private void stabilizeLeftLand() {
        int currLeft = sensorData[4][0];

        int sideThrust = (int) ((((leftDistanceLand - currLeft) + roomDistance / 2) / (roomDistance)) * (motorHigh - motorLow) + motorLow);

        FlightController.setRoll(sideThrust);

    }

    //bullshitcode do not use!
    private void stabilizeCenterFontBackLand() {
        int currBack = sensorData[3][0];
        int currFront = sensorData[1][0];

        int frontBackThrust = (int) ((((currFront - currBack) + roomDistance) / (2 * roomDistance)) * (motorHigh - motorLow) + motorLow);

        FlightController.setPitch(frontBackThrust);

    }

    private boolean isCentered(boolean isLeft) {
        int frontDistance = sensorData[1][0];
        int backDistance = sensorData[3][0];
        int sideDistance;
        if (isLeft) {
            sideDistance = sensorData[2][0];
        } else {
            sideDistance = sensorData[4][0];
        }

        return Math.abs(frontDistance - frontThreshDistance) < distanceVariance &&
                Math.abs(backDistance - backThreshDistance) < distanceVariance &&
                Math.abs(sideDistance - rightThreshDistance) < distanceVariance;
    }

    private boolean isInLandingPos() {
        //todo implement
        return true;
    }


}
