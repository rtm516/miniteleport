package dev.luxmiyu.miniteleport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
    public static final String MOD_ID = "miniteleport";
    public static final String MOD_NAME = "MiniTeleport";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
}
