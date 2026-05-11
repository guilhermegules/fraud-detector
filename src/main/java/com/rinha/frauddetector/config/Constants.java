package com.rinha.frauddetector.config;

public class Constants {
    public static final float[] WEIGHTS = new float[16];
    static {
        for (int i = 0; i < 16; i++) WEIGHTS[i] = 1.0f;
        WEIGHTS[11] = (float) Math.sqrt(2.0);
        WEIGHTS[12] = (float) Math.sqrt(1.5);
    }
}
