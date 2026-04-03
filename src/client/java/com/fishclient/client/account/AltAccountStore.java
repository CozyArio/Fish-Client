package com.fishclient.client.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AltAccountStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ALT_FILE = FabricLoader.getInstance().getConfigDir().resolve("fishclient-alts.json");

    private AltAccountStore() {
    }

    public static AltState load() {
        if (!Files.exists(ALT_FILE)) {
            return new AltState();
        }

        try (Reader reader = Files.newBufferedReader(ALT_FILE, StandardCharsets.UTF_8)) {
            AltState state = GSON.fromJson(reader, AltState.class);
            return sanitizeState(state);
        } catch (IOException exception) {
            return new AltState();
        }
    }

    public static void save(AltState state) {
        AltState safe = sanitizeState(state);
        try {
            Files.createDirectories(ALT_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(ALT_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(safe, writer);
            }
        } catch (IOException ignored) {
            // Best effort persistence.
        }
    }

    public static AltState ensureDefaults(Minecraft minecraft, AltState state) {
        AltState safe = sanitizeState(state);
        if (!safe.alts.isEmpty()) {
            if (safe.selectedAltId == null || safe.selectedAltId.isBlank()) {
                safe.selectedAltId = safe.alts.get(0).id;
            }
            return safe;
        }

        String username = "Player";
        if (minecraft != null && minecraft.getUser() != null && minecraft.getUser().getName() != null) {
            username = sanitizeUsername(minecraft.getUser().getName());
        }

        AltAccount current = new AltAccount();
        current.id = "alt-current";
        current.username = username;
        current.type = "offline";
        current.note = "Current session";
        safe.alts.add(current);
        safe.selectedAltId = current.id;
        return safe;
    }

    public static boolean applyAltToSession(Minecraft minecraft, AltAccount alt) {
        if (minecraft == null || alt == null) {
            return false;
        }

        String username = sanitizeUsername(alt.username);
        if (username.isBlank()) {
            return false;
        }

        try {
            User currentUser = minecraft.getUser();
            if (currentUser == null) {
                return false;
            }

            if (tryMutateCurrentUser(currentUser, username)) {
                return true;
            }

            User replacement = tryConstructReplacement(currentUser, username);
            if (replacement == null) {
                return false;
            }

            Field sessionField = findUserField();
            if (sessionField == null) {
                return false;
            }
            sessionField.setAccessible(true);
            sessionField.set(minecraft, replacement);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static boolean applyMicrosoftSession(Minecraft minecraft, String username, UUID uuid, String accessToken, String xuid) {
        if (minecraft == null || username == null || username.isBlank() || uuid == null || accessToken == null || accessToken.isBlank()) {
            return false;
        }

        try {
            User currentUser = minecraft.getUser();
            if (currentUser == null) {
                return false;
            }

            User replacement = tryConstructMicrosoftUser(currentUser, sanitizeUsername(username), uuid, accessToken, xuid);
            if (replacement == null) {
                return false;
            }

            Field sessionField = findUserField();
            if (sessionField == null) {
                return false;
            }
            sessionField.setAccessible(true);
            sessionField.set(minecraft, replacement);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean tryMutateCurrentUser(User currentUser, String username) throws IllegalAccessException {
        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        boolean changedName = false;
        boolean changedUuid = false;

        for (Field field : User.class.getDeclaredFields()) {
            field.setAccessible(true);
            if (field.getType() == String.class && field.getName().toLowerCase().contains("name")) {
                field.set(currentUser, username);
                changedName = true;
            }
            if (field.getType() == UUID.class) {
                field.set(currentUser, offlineUuid);
                changedUuid = true;
            }
        }

        return changedName || changedUuid;
    }

    @SuppressWarnings("unchecked")
    private static User tryConstructReplacement(User currentUser, String username) throws ReflectiveOperationException {
        UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        String accessToken = getAccessToken(currentUser);

        for (Constructor<?> ctor : User.class.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length < 2 || params[0] != String.class || params[1] != UUID.class) {
                continue;
            }

            Object[] args = new Object[params.length];
            args[0] = username;
            args[1] = offlineUuid;
            for (int i = 2; i < params.length; i++) {
                Class<?> type = params[i];
                if (type == String.class) {
                    args[i] = accessToken;
                } else if (type == UUID.class) {
                    args[i] = offlineUuid;
                } else if (Optional.class.isAssignableFrom(type)) {
                    args[i] = Optional.empty();
                } else {
                    args[i] = findAssignableFieldValue(currentUser, type);
                    if (args[i] == null && type.isPrimitive()) {
                        args[i] = primitiveDefault(type);
                    }
                }
            }

            ctor.setAccessible(true);
            Object created = ctor.newInstance(args);
            if (created instanceof User user) {
                return user;
            }
        }
        return null;
    }

    private static User tryConstructMicrosoftUser(User currentUser, String username, UUID uuid, String accessToken, String xuid) throws ReflectiveOperationException {
        for (Constructor<?> ctor : User.class.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length < 2 || params[0] != String.class || params[1] != UUID.class) {
                continue;
            }

            Object[] args = new Object[params.length];
            args[0] = username;
            args[1] = uuid;
            int optionalIndex = 0;
            for (int i = 2; i < params.length; i++) {
                Class<?> type = params[i];
                if (type == String.class) {
                    args[i] = accessToken;
                } else if (type == UUID.class) {
                    args[i] = uuid;
                } else if (Optional.class.isAssignableFrom(type)) {
                    if (optionalIndex == 0 && xuid != null && !xuid.isBlank()) {
                        args[i] = Optional.of(xuid);
                    } else {
                        args[i] = Optional.empty();
                    }
                    optionalIndex++;
                } else if (type.isEnum()) {
                    args[i] = enumByPreferredName(type, "MSA", "MICROSOFT", "MOJANG", "LEGACY");
                } else {
                    args[i] = findAssignableFieldValue(currentUser, type);
                    if (args[i] == null && type.isPrimitive()) {
                        args[i] = primitiveDefault(type);
                    }
                }
            }

            ctor.setAccessible(true);
            Object created = ctor.newInstance(args);
            if (created instanceof User user) {
                return user;
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumByPreferredName(Class<?> enumType, String... names) {
        if (enumType == null || !enumType.isEnum()) {
            return null;
        }
        Object[] constants = enumType.getEnumConstants();
        if (constants == null || constants.length == 0) {
            return null;
        }
        for (String wanted : names) {
            for (Object constant : constants) {
                if (constant instanceof Enum e && e.name().equalsIgnoreCase(wanted)) {
                    return constant;
                }
            }
        }
        return constants[0];
    }

    private static String getAccessToken(User currentUser) {
        try {
            Method method = User.class.getMethod("getAccessToken");
            Object value = method.invoke(currentUser);
            return value == null ? "0" : value.toString();
        } catch (ReflectiveOperationException ignored) {
            return "0";
        }
    }

    private static Object findAssignableFieldValue(User currentUser, Class<?> targetType) throws IllegalAccessException {
        for (Field field : User.class.getDeclaredFields()) {
            if (!targetType.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(currentUser);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object primitiveDefault(Class<?> primitive) {
        if (primitive == boolean.class) return false;
        if (primitive == byte.class) return (byte) 0;
        if (primitive == short.class) return (short) 0;
        if (primitive == int.class) return 0;
        if (primitive == long.class) return 0L;
        if (primitive == float.class) return 0f;
        if (primitive == double.class) return 0d;
        if (primitive == char.class) return '\0';
        return null;
    }

    private static Field findUserField() {
        try {
            return Minecraft.class.getDeclaredField("user");
        } catch (NoSuchFieldException ignored) {
            // Fallback for mapping/version field changes.
        }

        for (Field field : Minecraft.class.getDeclaredFields()) {
            if (field.getType() == User.class) {
                return field;
            }
        }
        return null;
    }

    private static AltState sanitizeState(AltState state) {
        AltState safe = state == null ? new AltState() : state;
        if (safe.alts == null) {
            safe.alts = new ArrayList<>();
        }

        List<AltAccount> normalized = new ArrayList<>();
        for (AltAccount alt : safe.alts) {
            if (alt == null) {
                continue;
            }
            String username = sanitizeUsername(alt.username);
            if (username.isBlank()) {
                continue;
            }
            AltAccount clean = new AltAccount();
            clean.id = (alt.id == null || alt.id.isBlank()) ? ("alt-" + username.toLowerCase()) : alt.id;
            clean.username = username;
            if ("microsoft".equalsIgnoreCase(alt.type)) {
                clean.type = "microsoft";
            } else if ("alias".equalsIgnoreCase(alt.type) || "login".equalsIgnoreCase(alt.type)) {
                clean.type = "alias";
            } else {
                clean.type = "offline";
            }
            clean.note = alt.note == null ? "" : alt.note;
            clean.accessToken = alt.accessToken == null ? "" : alt.accessToken;
            clean.refreshToken = alt.refreshToken == null ? "" : alt.refreshToken;
            clean.xuid = alt.xuid == null ? "" : alt.xuid;
            clean.uuid = alt.uuid == null ? "" : alt.uuid;
            normalized.add(clean);
        }

        safe.alts = normalized;
        if (safe.selectedAltId == null) {
            safe.selectedAltId = "";
        }
        if (!safe.selectedAltId.isBlank() && safe.alts.stream().noneMatch(alt -> alt.id.equals(safe.selectedAltId))) {
            safe.selectedAltId = "";
        }
        return safe;
    }

    public static String sanitizeUsername(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.length() > 16) {
            cleaned = cleaned.substring(0, 16);
        }
        return cleaned;
    }

    public static final class AltState {
        public String selectedAltId = "";
        public List<AltAccount> alts = new ArrayList<>();
    }

    public static final class AltAccount {
        public String id;
        public String username;
        public String type;
        public String note;
        public String accessToken = "";
        public String refreshToken = "";
        public String xuid = "";
        public String uuid = "";
    }
}
