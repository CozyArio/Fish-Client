package com.fishclient.client.account;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class MicrosoftAuthService {

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private MicrosoftAuthService() {
    }

    public static DeviceCodeStart beginDeviceCode(String clientId) throws IOException, InterruptedException {
        String body = form(
            "client_id", clientId,
            "scope", "XboxLive.signin offline_access"
        );

        JsonObject json = postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", body);
        String deviceCode = getRequired(json, "device_code");
        String userCode = getRequired(json, "user_code");
        String verificationUri = getRequired(json, "verification_uri");
        int expiresIn = getInt(json, "expires_in", 900);
        int interval = Math.max(2, getInt(json, "interval", 5));
        String message = getOptional(json, "message", "Open the link and enter the code.");

        return new DeviceCodeStart(deviceCode, userCode, verificationUri, expiresIn, interval, message);
    }

    public static MinecraftAuth pollForMinecraftAuth(String clientId, DeviceCodeStart deviceCode, Consumer<String> statusCallback) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + (long) deviceCode.expiresIn * 1000L;

        String microsoftAccessToken = null;
        String microsoftRefreshToken = "";

        while (System.currentTimeMillis() < deadline) {
            String body = form(
                "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                "client_id", clientId,
                "device_code", deviceCode.deviceCode
            );
            JsonObject tokenResponse = postFormAllowErrors("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", body);
            if (tokenResponse.has("access_token")) {
                microsoftAccessToken = getRequired(tokenResponse, "access_token");
                microsoftRefreshToken = getOptional(tokenResponse, "refresh_token", "");
                break;
            }

            String error = getOptional(tokenResponse, "error", "unknown_error");
            if ("authorization_pending".equalsIgnoreCase(error)) {
                if (statusCallback != null) {
                    statusCallback.accept("Waiting for Microsoft confirmation...");
                }
                Thread.sleep((long) deviceCode.interval * 1000L);
                continue;
            }
            if ("slow_down".equalsIgnoreCase(error)) {
                if (statusCallback != null) {
                    statusCallback.accept("Microsoft asked to slow down, retrying...");
                }
                Thread.sleep((long) (deviceCode.interval + 3) * 1000L);
                continue;
            }
            if ("authorization_declined".equalsIgnoreCase(error)) {
                throw new IOException("Microsoft login was declined.");
            }
            if ("expired_token".equalsIgnoreCase(error)) {
                throw new IOException("Microsoft device code expired.");
            }
            throw new IOException("Microsoft token error: " + error);
        }

        if (microsoftAccessToken == null || microsoftAccessToken.isBlank()) {
            throw new IOException("Microsoft login timed out.");
        }

        if (statusCallback != null) {
            statusCallback.accept("Microsoft confirmed. Authorizing Xbox...");
        }

        JsonObject xblReq = new JsonObject();
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", "d=" + microsoftAccessToken);
        xblReq.add("Properties", xblProps);
        xblReq.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblReq.addProperty("TokenType", "JWT");
        JsonObject xblRes = postJson("https://user.auth.xboxlive.com/user/authenticate", xblReq);
        String xblToken = getRequired(xblRes, "Token");
        String uhs = extractUhs(xblRes);

        JsonObject xstsReq = new JsonObject();
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        xstsProps.add("UserTokens", userTokens);
        xstsReq.add("Properties", xstsProps);
        xstsReq.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsReq.addProperty("TokenType", "JWT");
        JsonObject xstsRes = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsReq);
        String xstsToken = getRequired(xstsRes, "Token");

        if (statusCallback != null) {
            statusCallback.accept("Authorizing Minecraft profile...");
        }

        JsonObject mcReq = new JsonObject();
        mcReq.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JsonObject mcLoginRes = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcReq);
        String mcAccessToken = getRequired(mcLoginRes, "access_token");

        JsonObject profile = getJson("https://api.minecraftservices.com/minecraft/profile", mcAccessToken);
        String name = getRequired(profile, "name");
        String rawId = getRequired(profile, "id");
        UUID uuid = parseUndashedUuid(rawId);

        return new MinecraftAuth(name, uuid, mcAccessToken, microsoftRefreshToken, uhs);
    }

    private static String extractUhs(JsonObject response) throws IOException {
        JsonObject claims = response.has("DisplayClaims") && response.get("DisplayClaims").isJsonObject()
            ? response.getAsJsonObject("DisplayClaims")
            : null;
        if (claims == null || !claims.has("xui") || !claims.get("xui").isJsonArray()) {
            throw new IOException("Xbox response missing user hash.");
        }
        JsonArray xui = claims.getAsJsonArray("xui");
        if (xui.isEmpty() || !xui.get(0).isJsonObject()) {
            throw new IOException("Xbox response missing xui.");
        }
        JsonObject entry = xui.get(0).getAsJsonObject();
        return getRequired(entry, "uhs");
    }

    private static JsonObject postForm(String url, String body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IOException("HTTP " + res.statusCode() + " from " + url + ": " + shortBody(res.body()));
        }
        return parseObject(res.body());
    }

    private static JsonObject postFormAllowErrors(String url, String body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 200) {
            return parseObject(res.body());
        }
        try {
            return parseObject(res.body());
        } catch (Exception ignored) {
            JsonObject out = new JsonObject();
            out.addProperty("error", "http_" + res.statusCode());
            out.addProperty("error_description", shortBody(res.body()));
            return out;
        }
    }

    private static JsonObject postJson(String url, JsonObject payload) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IOException("HTTP " + res.statusCode() + " from " + url + ": " + shortBody(res.body()));
        }
        return parseObject(res.body());
    }

    private static JsonObject getJson(String url, String bearerToken) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + bearerToken)
            .GET()
            .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IOException("HTTP " + res.statusCode() + " from " + url + ": " + shortBody(res.body()));
        }
        return parseObject(res.body());
    }

    private static JsonObject parseObject(String raw) throws IOException {
        JsonElement root = JsonParser.parseString(raw == null ? "" : raw);
        if (!root.isJsonObject()) {
            throw new IOException("Unexpected JSON response.");
        }
        return root.getAsJsonObject();
    }

    private static String getRequired(JsonObject json, String key) throws IOException {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            throw new IOException("Missing field: " + key);
        }
        return json.get(key).getAsString();
    }

    private static String getOptional(JsonObject json, String key, String fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String shortBody(String body) {
        if (body == null) {
            return "";
        }
        String compact = body.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.length() <= 220) {
            return compact;
        }
        return compact.substring(0, 220) + "...";
    }

    private static String form(String... kv) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out.add(encode(kv[i]) + "=" + encode(kv[i + 1]));
        }
        return String.join("&", out);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static UUID parseUndashedUuid(String raw32) throws IOException {
        if (raw32 == null) {
            throw new IOException("Missing profile id.");
        }
        String hex = raw32.trim().toLowerCase();
        if (!hex.matches("^[0-9a-f]{32}$")) {
            throw new IOException("Invalid profile id: " + raw32);
        }
        String dashed = hex.substring(0, 8) + "-"
            + hex.substring(8, 12) + "-"
            + hex.substring(12, 16) + "-"
            + hex.substring(16, 20) + "-"
            + hex.substring(20);
        return UUID.fromString(dashed);
    }

    public static final class DeviceCodeStart {
        public final String deviceCode;
        public final String userCode;
        public final String verificationUri;
        public final int expiresIn;
        public final int interval;
        public final String message;

        public DeviceCodeStart(String deviceCode, String userCode, String verificationUri, int expiresIn, int interval, String message) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.expiresIn = expiresIn;
            this.interval = interval;
            this.message = message;
        }
    }

    public static final class MinecraftAuth {
        public final String username;
        public final UUID uuid;
        public final String minecraftAccessToken;
        public final String microsoftRefreshToken;
        public final String xuid;

        public MinecraftAuth(String username, UUID uuid, String minecraftAccessToken, String microsoftRefreshToken, String xuid) {
            this.username = username;
            this.uuid = uuid;
            this.minecraftAccessToken = minecraftAccessToken;
            this.microsoftRefreshToken = microsoftRefreshToken;
            this.xuid = xuid;
        }
    }
}
