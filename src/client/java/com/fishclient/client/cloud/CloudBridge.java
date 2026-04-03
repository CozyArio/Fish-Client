package com.fishclient.client.cloud;

/**
 * Runtime-safe bridge for optional CloudServer support.
 * Some launchers do not include bundled websocket classes; this keeps Fish Client stable.
 */
public final class CloudBridge {

    private static final String CLOUD_SERVER_CLASS = "com.fishclient.client.cloud.CloudServer";
    private static volatile boolean unavailableLogged;

    private CloudBridge() {
    }

    public static boolean startIfNeeded() {
        Object result = invoke("startIfNeeded");
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        return false;
    }

    public static String endpoint() {
        Object result = invoke("endpoint");
        if (result instanceof String) {
            return (String) result;
        }
        return "ws://localhost:9157";
    }

    public static void broadcastState() {
        invoke("broadcastState");
    }

    public static boolean isAvailable() {
        return getCloudServerClass() != null;
    }

    private static Object invoke(String methodName) {
        Class<?> cloudClass = getCloudServerClass();
        if (cloudClass == null) {
            return null;
        }

        try {
            java.lang.reflect.Method method = cloudClass.getDeclaredMethod(methodName);
            return method.invoke(null);
        } catch (Throwable throwable) {
            logUnavailable(throwable);
            return null;
        }
    }

    private static Class<?> getCloudServerClass() {
        try {
            return Class.forName(CLOUD_SERVER_CLASS);
        } catch (Throwable throwable) {
            logUnavailable(throwable);
            return null;
        }
    }

    private static void logUnavailable(Throwable throwable) {
        if (unavailableLogged) {
            return;
        }
        unavailableLogged = true;

        String type = throwable == null ? "Unknown" : throwable.getClass().getSimpleName();
        String message = throwable == null ? "" : throwable.getMessage();
        if (message == null) {
            message = "";
        }

        System.err.println("[FishClient] Cloud features disabled: " + type + " " + message);
    }
}
