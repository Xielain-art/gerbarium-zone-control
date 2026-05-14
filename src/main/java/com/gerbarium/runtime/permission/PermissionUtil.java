package com.gerbarium.runtime.permission;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.lang.reflect.Method;

public final class PermissionUtil {
    private static final int FALLBACK_OP_LEVEL = 2;
    private static final String ADMIN_PERMISSION = "gerbarium.regions.admin";
    private static Boolean hasLuckPerms = null;
    private static Object luckPermsApi = null;
    private static Object luckPermsUserManager = null;

    private PermissionUtil() {
    }

    public static boolean hasAdminPermission(ServerCommandSource source) {
        return hasPermission(source, ADMIN_PERMISSION, FALLBACK_OP_LEVEL);
    }

    public static boolean hasPermission(ServerCommandSource source, String permission, int fallbackOpLevel) {
        Boolean apiResult = checkViaFabricPermissionsApi(source, permission, fallbackOpLevel);
        if (apiResult != null) {
            return apiResult;
        }

        Boolean luckPermsResult = checkViaLuckPerms(source, permission);
        if (luckPermsResult != null) {
            return luckPermsResult;
        }

        return source.hasPermissionLevel(fallbackOpLevel);
    }

    private static Boolean checkViaFabricPermissionsApi(ServerCommandSource source, String permission, int fallbackOpLevel) {
        try {
            Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");

            Boolean result = tryInvokeBoolean(
                    permissionsClass,
                    "check",
                    new Class<?>[]{ServerCommandSource.class, String.class, int.class},
                    new Object[]{source, permission, fallbackOpLevel}
            );
            if (result != null) {
                return result;
            }

            result = tryInvokeBoolean(
                    permissionsClass,
                    "check",
                    new Class<?>[]{ServerCommandSource.class, String.class, boolean.class},
                    new Object[]{source, permission, source.hasPermissionLevel(fallbackOpLevel)}
            );
            if (result != null) {
                return result;
            }

            for (Method method : permissionsClass.getMethods()) {
                if (!method.getName().equals("check")) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 3 || !params[1].equals(String.class) || !params[0].isAssignableFrom(source.getClass())) {
                    continue;
                }
                if (params[2].equals(int.class) || params[2].equals(Integer.class)) {
                    Object raw = method.invoke(null, source, permission, fallbackOpLevel);
                    if (raw instanceof Boolean allowed) {
                        return allowed;
                    }
                }
                if (params[2].equals(boolean.class) || params[2].equals(Boolean.class)) {
                    Object raw = method.invoke(null, source, permission, source.hasPermissionLevel(fallbackOpLevel));
                    if (raw instanceof Boolean allowed) {
                        return allowed;
                    }
                }
            }

            for (Method method : permissionsClass.getMethods()) {
                if (!method.getName().equals("check")) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 2 || !params[1].equals(String.class) || !params[0].isAssignableFrom(source.getClass())) {
                    continue;
                }
                Object raw = method.invoke(null, source, permission);
                if (raw instanceof Boolean allowed) {
                    return allowed;
                }
            }

            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Boolean checkViaLuckPerms(ServerCommandSource source, String permission) {
        try {
            ServerPlayerEntity player = source.getPlayer();
            if (player == null) {
                return null;
            }

            if (hasLuckPerms == Boolean.FALSE) {
                return null;
            }

            if (hasLuckPerms == null) {
                Class<?> luckPermsApiClass = Class.forName("net.luckperms.api.LuckPerms");
                Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                Method getProvider = providerClass.getMethod("get");
                luckPermsApi = getProvider.invoke(null);
                Method getUserManager = luckPermsApiClass.getMethod("getUserManager");
                luckPermsUserManager = getUserManager.invoke(luckPermsApi);
                hasLuckPerms = true;
            }

            Method getUser = luckPermsUserManager.getClass().getMethod("getUser", java.util.UUID.class);
            Object user = getUser.invoke(luckPermsUserManager, player.getUuid());
            if (user == null) {
                return null;
            }
            Method getCachedData = user.getClass().getMethod("getCachedData");
            Object cachedData = getCachedData.invoke(user);
            Method getPermissionData = cachedData.getClass().getMethod("getPermissionData");
            Object permissionData = getPermissionData.invoke(cachedData);
            Method checkPermission = permissionData.getClass().getMethod("checkPermission", String.class);
            Object triState = checkPermission.invoke(permissionData, permission);
            Method asBoolean = triState.getClass().getMethod("asBoolean");
            Object result = asBoolean.invoke(triState);
            return Boolean.TRUE.equals(result);
        } catch (ClassNotFoundException e) {
            hasLuckPerms = false;
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Boolean tryInvokeBoolean(Class<?> targetClass, String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {
            Method method = targetClass.getMethod(methodName, parameterTypes);
            Object raw = method.invoke(null, args);
            if (raw instanceof Boolean allowed) {
                return allowed;
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
