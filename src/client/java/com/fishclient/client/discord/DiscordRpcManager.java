package com.fishclient.client.discord;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class DiscordRpcManager {

    private static final String APP_ID = System.getProperty("fishclient.discord.appId", "1489534390815166565");
    private static final long START_TS = System.currentTimeMillis() / 1000L;
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;

    private static RandomAccessFile pipe;
    private static boolean initialized;
    private static boolean connected;
    private static long lastConnectAttemptMs;
    private static long lastPresenceUpdateMs;
    private static long lastCallbackPollMs;

    private DiscordRpcManager() {
    }

    public static void initialize() {
        initialized = true;
        tryConnect();
    }

    public static void tick(Minecraft client) {
        if (!initialized) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!connected) {
            if (now - lastConnectAttemptMs > 5000L) {
                tryConnect();
            }
            return;
        }

        if (now - lastCallbackPollMs > 1000L) {
            pollPipeClose();
            lastCallbackPollMs = now;
        }
        if (!connected) {
            return;
        }

        if (now - lastPresenceUpdateMs > 1500L) {
            try {
                sendActivity(resolveState(client));
                lastPresenceUpdateMs = now;
            } catch (IOException e) {
                disconnectSilently();
            }
        }
    }

    public static void shutdown() {
        initialized = false;
        disconnectSilently();
    }

    private static void tryConnect() {
        lastConnectAttemptMs = System.currentTimeMillis();
        if (!isWindows()) {
            return;
        }

        for (int i = 0; i < 10; i++) {
            String pipePath = "\\\\.\\pipe\\discord-ipc-" + i;
            try {
                pipe = new RandomAccessFile(pipePath, "rw");
                sendHandshake();
                connected = true;
                sendActivity("In menu");
                lastPresenceUpdateMs = 0L;
                System.out.println("[FishClient] Discord RPC connected (" + pipePath + ")");
                return;
            } catch (IOException ignored) {
                disconnectSilently();
            }
        }
    }

    private static void sendHandshake() throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        payload.addProperty("client_id", APP_ID);
        writePayload(OP_HANDSHAKE, payload.toString());
    }

    private static void sendActivity(String state) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("cmd", "SET_ACTIVITY");
        root.addProperty("nonce", UUID.randomUUID().toString());

        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());

        JsonObject activity = new JsonObject();
        activity.addProperty("details", "Playing Fish Client - Dev");
        activity.addProperty("state", state + " | 1.21.11");

        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", START_TS);
        activity.add("timestamps", timestamps);

        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "fish_logo");
        assets.addProperty("large_text", "Fish Client");
        assets.addProperty("small_image", "fish_small");
        assets.addProperty("small_text", "Dev Build");
        activity.add("assets", assets);

        args.add("activity", activity);
        root.add("args", args);
        writePayload(OP_FRAME, root.toString());
    }

    private static void writePayload(int op, String json) throws IOException {
        if (pipe == null) {
            throw new IOException("Discord pipe not connected");
        }
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        pipe.write(intToLittleEndian(op));
        pipe.write(intToLittleEndian(payload.length));
        pipe.write(payload);
    }

    private static byte[] intToLittleEndian(int value) {
        return new byte[] {
            (byte) (value & 0xFF),
            (byte) ((value >> 8) & 0xFF),
            (byte) ((value >> 16) & 0xFF),
            (byte) ((value >> 24) & 0xFF)
        };
    }

    private static void pollPipeClose() {
        if (pipe == null) {
            connected = false;
            return;
        }
        try {
            if (pipe.length() < 0) {
                connected = false;
            }
        } catch (IOException e) {
            disconnectSilently();
        }
    }

    private static String resolveState(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return "In menu";
        }
        if (client.hasSingleplayerServer()) {
            return "Singleplayer";
        }
        if (client.getCurrentServer() != null) {
            return "Multiplayer";
        }
        return "In world";
    }

    private static void disconnectSilently() {
        connected = false;
        if (pipe != null) {
            try {
                try {
                    writePayload(OP_CLOSE, "{}");
                } catch (IOException ignored) {
                }
                pipe.close();
            } catch (IOException ignored) {
            } finally {
                pipe = null;
            }
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("win");
    }
}
