package com.fishclient.client.cloud;

import com.fishclient.client.ui.FishClickScreen;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public final class CloudServer extends WebSocketServer {

    public static final String HOST = "127.0.0.1";
    public static final int PORT = 9157;

    private static CloudServer instance;
    private static boolean running;
    private static final Gson GSON = new GsonBuilder().create();

    private CloudServer(InetSocketAddress address) {
        super(address);
    }

    public static synchronized boolean startIfNeeded() {
        if (running) {
            return true;
        }
        try {
            instance = new CloudServer(new InetSocketAddress(HOST, PORT));
            instance.setDaemon(true);
            instance.setReuseAddr(true);
            instance.start();
            running = true;
            System.out.println("[FishClient] CloudServer started on " + endpoint());
        } catch (Exception exception) {
            System.err.println("[FishClient] CloudServer failed to start: " + exception.getMessage());
            running = false;
        }
        return running;
    }

    public static String endpoint() {
        return "ws://" + HOST + ":" + PORT;
    }

    public static synchronized boolean isRunning() {
        return running;
    }

    public static synchronized void broadcastState() {
        if (instance == null || instance.getConnections().isEmpty()) {
            return;
        }
        String json = buildStateJson();
        for (WebSocket connection : instance.getConnections()) {
            try {
                connection.send(json);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        try {
            connection.send(buildStateJson());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onMessage(WebSocket connection, String raw) {
        try {
            JsonObject msg = JsonParser.parseString(raw).getAsJsonObject();
            String action = msg.get("action").getAsString();

            switch (action) {
                case "toggle" -> {
                    String name = msg.get("module").getAsString();
                    for (FishClickScreen.ModuleEntry module : FishClickScreen.SHARED_MODULES) {
                        if (module.name.equals(name)) {
                            module.enabled = !module.enabled;
                            break;
                        }
                    }
                    broadcastState();
                }
                case "setSetting" -> {
                    String moduleName = msg.get("module").getAsString();
                    String settingName = msg.get("setting").getAsString();
                    JsonElement value = msg.get("value");

                    for (FishClickScreen.ModuleEntry module : FishClickScreen.SHARED_MODULES) {
                        if (!module.name.equals(moduleName)) {
                            continue;
                        }
                        for (FishClickScreen.SettingEntry setting : module.settings) {
                            if (!setting.label.equals(settingName)) {
                                continue;
                            }
                            switch (setting.type) {
                                case TOGGLE -> setting.boolValue = value.getAsBoolean();
                                case SLIDER -> setting.sliderValue = value.getAsDouble();
                                case SELECT -> setting.selectIndex = value.getAsInt();
                                case TEXT -> setting.textValue = value.getAsString();
                            }
                            break;
                        }
                        break;
                    }
                    broadcastState();
                }
                case "ping" -> connection.send("{\"action\":\"pong\"}");
                default -> {
                }
            }
        } catch (Exception exception) {
            System.err.println("[FishClient] CloudServer message error: " + exception.getMessage());
        }
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
    }

    @Override
    public void onError(WebSocket connection, Exception exception) {
        if (exception != null) {
            System.err.println("[FishClient] CloudServer error: " + exception.getMessage());
        }
    }

    @Override
    public void onStart() {
        System.out.println("[FishClient] CloudServer ready");
    }

    private static String buildStateJson() {
        JsonObject root = new JsonObject();
        root.addProperty("action", "state");
        JsonArray modules = new JsonArray();

        for (FishClickScreen.ModuleEntry module : FishClickScreen.SHARED_MODULES) {
            JsonObject moduleObj = new JsonObject();
            moduleObj.addProperty("name", module.name);
            moduleObj.addProperty("description", module.description);
            moduleObj.addProperty("category", module.category);
            moduleObj.addProperty("enabled", module.enabled);

            JsonArray settings = new JsonArray();
            for (FishClickScreen.SettingEntry setting : module.settings) {
                JsonObject settingObj = new JsonObject();
                settingObj.addProperty("label", setting.label);
                settingObj.addProperty("type", setting.type.name());
                switch (setting.type) {
                    case TOGGLE -> settingObj.addProperty("value", setting.boolValue);
                    case SLIDER -> {
                        settingObj.addProperty("value", setting.sliderValue);
                        settingObj.addProperty("min", setting.sliderMin);
                        settingObj.addProperty("max", setting.sliderMax);
                        settingObj.addProperty("step", setting.sliderStep);
                    }
                    case SELECT -> {
                        settingObj.addProperty("value", setting.selectIndex);
                        JsonArray options = new JsonArray();
                        if (setting.selectOptions != null) {
                            for (String option : setting.selectOptions) {
                                options.add(option);
                            }
                        }
                        settingObj.add("options", options);
                    }
                    case TEXT -> settingObj.addProperty("value", setting.textValue == null ? "" : setting.textValue);
                }
                settings.add(settingObj);
            }
            moduleObj.add("settings", settings);
            modules.add(moduleObj);
        }

        root.add("modules", modules);
        return GSON.toJson(root);
    }
}
