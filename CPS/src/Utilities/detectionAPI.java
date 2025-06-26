package Utilities;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;

public class detectionAPI {

    public static int sendImage(ByteArrayOutputStream imageStream, String stationId) throws IOException {
        String serverURL = "http://127.0.0.1:8000";
        String boundary = "===" + System.currentTimeMillis() + "===";
        URL url = new URL(serverURL + "/inspect?station_id=" + stationId);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream request = new DataOutputStream(conn.getOutputStream())) {
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"image.jpg\"\r\n");
            request.writeBytes("Content-Type: image/jpeg\r\n\r\n");
            imageStream.writeTo(request);
            request.writeBytes("\r\n--" + boundary + "--\r\n");
        }

        int responseCode = conn.getResponseCode();
        System.out.println("POST Response Code: " + responseCode);

        boolean sawNOK = false;
        Random rnd = new Random();
        
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.contains("\"inspection_status\":\"OK\"")) {
                    return 1;
                }

                if (line.contains("\"inspection_status\":\"NOK\"")) {
                    sawNOK = true;
                }
                if (sawNOK) {
                    if (line.contains("\"defect_position\":\"Upper\"")) {
                        return 2;
                    }
                    if (line.contains("\"defect_position\":\"Bottom\"")) {
                        return 3;
                    }
                    if (line.contains("\"defect_position\":\"Both\"")) {
                        return rnd.nextBoolean() ? 2 : 3;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();    
        }
        return -1;
    }
}
