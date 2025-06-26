/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Libraries;

import Utilities.Constants;
import coppelia.CharWA;
import coppelia.IntW;
import coppelia.remoteApi;
import jade.core.Agent;
import java.util.logging.Level;
import java.util.logging.Logger;
import coppelia.IntWA;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Random;

import javax.imageio.ImageIO;

import Utilities.detectionAPI;

/**
 *
 * @author Ricardo Silva Peres <ricardo.peres@uninova.pt>
 */
public class SimResourceLibrary implements IResource {

    public remoteApi sim;
    public int clientID = -1;
    Agent myAgent;
    final long timeout = 30000;

    @Override
    public void init(Agent a) {
        this.myAgent = a;
        if (sim == null)
            sim = new remoteApi();
        sim = new remoteApi();
        int port = 0;
        switch (myAgent.getLocalName()) {
            case "GlueStation1":
                port = 19997;
                break;
            case "GlueStation2":
                port = 19998;
                break;
            case "QualityControlStation1":
                port = 19999;
                break;
            case "QualityControlStation2":
                port = 20000;
                break;
            case "Operator":
                port = 20001;
                break;
        }
        clientID = sim.simxStart("127.0.0.1", port, true, true, 5000, 5);
        if (clientID != -1) {
            System.out.println(this.myAgent.getAID().getLocalName() + " initialized communication with the simulation.");
        }
    }

    @Override
    public String[] getSkills() {
        String[] skills;
        switch (myAgent.getLocalName()) {
            case "GlueStation1":
                skills = new String[2];
                skills[0] = Utilities.Constants.SK_GLUE_TYPE_A;
                skills[1] = Utilities.Constants.SK_GLUE_TYPE_B;
                return skills;
            case "GlueStation2":
                skills = new String[2];
                skills[0] = Utilities.Constants.SK_GLUE_TYPE_A;
                skills[1] = Utilities.Constants.SK_GLUE_TYPE_C;
                return skills;
            case "QualityControlStation1":
                skills = new String[1];
                skills[0] = Utilities.Constants.SK_QUALITY_CHECK;
                return skills;
            case "QualityControlStation2":
                skills = new String[1];
                skills[0] = Utilities.Constants.SK_QUALITY_CHECK;
                return skills;
            case "Operator":
                skills = new String[2];
                skills[0] = Utilities.Constants.SK_PICK_UP;
                skills[1] = Utilities.Constants.SK_DROP;
                return skills;
        }
        return null;
    }

    @Override
    public int executeSkill(String skillID, boolean useCoppelia) {
        sim.simxSetStringSignal(clientID, myAgent.getLocalName(), new CharWA(skillID), sim.simx_opmode_blocking);
        IntW opRes = new IntW(-1);
        long startTime = System.currentTimeMillis();
        while ((opRes.getValue() != 1) && (System.currentTimeMillis() - startTime < timeout)) {
            sim.simxGetIntegerSignal(clientID, myAgent.getLocalName(), opRes, sim.simx_opmode_blocking);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(SimResourceLibrary.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        sim.simxClearIntegerSignal(clientID, myAgent.getLocalName(), sim.simx_opmode_blocking);

        // QUALITY CHECK
        if(skillID.equalsIgnoreCase(Constants.SK_QUALITY_CHECK)) {
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ByteArrayOutputStream imageStream = null;
            int visionSensorHandle = -1;

            String cameraName = myAgent.getLocalName().equals("QualityControlStation1") ? "QC1QualityInspection" : "QC2QualityInspection";

            if (useCoppelia) {
                IntW handle = new IntW(-1);
                int res = sim.simxGetObjectHandle(clientID, cameraName, handle, sim.simx_opmode_blocking);
                if (res == remoteApi.simx_return_ok) {
                    visionSensorHandle = handle.getValue();
                } else {
                    System.err.println("Failed to get handle for camera '" + cameraName + "': " + res);
                }
                imageStream = getImageFromCoppelia(visionSensorHandle);
            } else {
                imageStream = getImageFromDisk("dataset/images"); //image path
            }

            if (imageStream != null) {
                try {
                    String stationId = myAgent.getLocalName();
                    int inspectionResult = detectionAPI.sendImage(imageStream, stationId);
                    Thread.sleep(1000);

                    System.out.println("Inspection result: " + inspectionResult);
                    return inspectionResult;

                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.err.println("Image retrieval failed.");
            }

            return -1;

        }

        return opRes.getValue();
    }

    public ByteArrayOutputStream getImageFromCoppelia(int visionSensorHandle) {
        IntWA resolution = new IntWA(2);
        CharWA image = new CharWA(0);

        int result = sim.simxGetVisionSensorImage(clientID, visionSensorHandle, resolution, image, 0,
                sim.simx_opmode_blocking);

        if (result == remoteApi.simx_return_ok) {
            int width = resolution.getArray()[0];
            int height = resolution.getArray()[1];
            char[] rawChars = image.getArray();
            byte[] rgbData = new byte[rawChars.length];
            for (int i = 0; i < rawChars.length; i++) {
                rgbData[i] = (byte) rawChars[i];
            }

            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
            int index = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int r = rgbData[index++] & 0xFF;
                    int g = rgbData[index++] & 0xFF;
                    int b = rgbData[index++] & 0xFF;
                    int rgb = (r << 16) | (g << 8) | b;
                    img.setRGB(x, height - y - 1, rgb);
                }
            }

            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "jpg", baos);
                return baos;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        else {
            System.err.println("Failed to get image from Coppelia: " + result + " Using Local Image");
            return getImageFromDisk("dataset/images");
        }
    }

    public ByteArrayOutputStream getImageFromDisk(String folderPath) {
    File folder = new File(folderPath);
    File[] files = folder.listFiles((dir, name) -> {
        String nameLower = name.toLowerCase();
        return nameLower.endsWith(".jpg");
    });

    if (files == null || files.length == 0) {
        System.err.println("No image files found in folder: " + folderPath);
        return null;
    }

    File randomImage = files[new Random().nextInt(files.length)];
    System.out.println("Using local image: " + randomImage.getName());

    try {
        BufferedImage img = ImageIO.read(randomImage);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos;
    } catch (IOException e) {
        e.printStackTrace();
        return null;
    }
}

    public boolean launchProduct(String productID) {
        return false;
    }

    public boolean finishProduct(String productID) {
        return false;
    }

}
