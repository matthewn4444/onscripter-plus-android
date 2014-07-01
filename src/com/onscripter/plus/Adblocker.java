package com.onscripter.plus;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class Adblocker {
    private final static String HostFile = "/etc/hosts";

    static public boolean check() {
        BufferedReader in = null;
        boolean detected = false;

        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(
                    HostFile)), 20000);
            String line;

            while ((line = in.readLine()) != null) {
                if (line.contains("admob") || line.contains("googleadservices")) {
                    detected = true;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return detected;
    }
}
