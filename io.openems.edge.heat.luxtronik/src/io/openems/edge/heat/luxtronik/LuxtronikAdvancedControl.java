package io.openems.edge.heat.luxtronik;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.openems.edge.heat.luxtronik.LuxtronikControlPolicy.Mode;

/**
 * A complete, explicitly configured control surface for every writable SHI
 * register. The empty plan is safe and does not request any register writes.
 *
 * <p>
 * Example: {"HOT_WATER_SETPOINT":{"normal":500,"cheap":520},
 * "MIXER1_HEATING_OFFSET":{"normal":0,"pv":5}}.
 *
 * Values use documented raw SHI units. Only configured registers are touched.
 */
public final class LuxtronikAdvancedControl {

    public record Profile(Integer normal, Integer cheap, Integer expensive, Integer pv) {
    }

    private LuxtronikAdvancedControl() {
    }

    /**
     * Parses configuration and rejects unknown registers, keys or invalid values.
     *
     * @param json JSON register profiles
     * @return immutable map
     */
    public static Map<ShiWriteRegister, Profile> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("SHI control plan must be a JSON object");
        }
        var parsed = new EnumMap<ShiWriteRegister, Profile>(ShiWriteRegister.class);
        for (var entry : root.getAsJsonObject().entrySet()) {
            ShiWriteRegister register;
            try {
                register = ShiWriteRegister.valueOf(entry.getKey());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown SHI register: " + entry.getKey(), e);
            }
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException(register + " needs a JSON object profile");
            }
            var profile = entry.getValue().getAsJsonObject();
            for (var key : profile.keySet()) {
                if (!key.equals("normal") && !key.equals("cheap")
                        && !key.equals("expensive") && !key.equals("pv")) {
                    throw new IllegalArgumentException(register + ": unsupported profile key " + key);
                }
            }
            Integer normal = read(profile, "normal", register);
            Integer cheap = read(profile, "cheap", register);
            Integer expensive = read(profile, "expensive", register);
            Integer pv = read(profile, "pv", register);
            if (normal == null && cheap == null && expensive == null && pv == null) {
                throw new IllegalArgumentException(register + ": no target configured");
            }
            parsed.put(register, new Profile(normal, cheap, expensive, pv));
        }
        return Collections.unmodifiableMap(parsed);
    }

    private static Integer read(JsonObject obj, String name, ShiWriteRegister register) {
        if (!obj.has(name)) {
            return null;
        }
        JsonElement element = obj.get(name);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(register + ": " + name + " must be an integer");
        }
        int value;
        try {
            String raw = element.getAsString();
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(register + ": " + name + " must be an integer", e);
        }
        return register.validate(value);
    }

    /**
     * Resolves a single register target. Missing source data fails closed:
     * no price/PV override is selected without its corresponding input.
     *
     * @param profile configured register profile
     * @param mode current controller mode
     * @param price validated EUR/MWh price, possibly null
     * @param gridWatts measured grid exchange, possibly null
     * @param cheapPrice cheap threshold EUR/MWh
     * @param expensivePrice expensive threshold EUR/MWh
     * @param exportThreshold minimum export W
     * @return configured target, or null if no action was requested
     */
    public static Integer target(Profile profile, Mode mode, Double price, Integer gridWatts,
            int cheapPrice, int expensivePrice, int exportThreshold) {
        if (mode == Mode.MONITOR) {
            return null;
        }
        boolean validPrice = price != null && Double.isFinite(price) && price >= 0;
        boolean exporting = gridWatts != null && gridWatts <= -exportThreshold;
        boolean usePrice = mode == Mode.PRICE || mode == Mode.PRICE_OR_PV;
        boolean usePv = mode == Mode.PV_SURPLUS || mode == Mode.PRICE_OR_PV;
        if (usePv && exporting && profile.pv() != null) {
            return profile.pv();
        }
        if (usePrice && validPrice && price <= cheapPrice && profile.cheap() != null) {
            return profile.cheap();
        }
        if (usePrice && validPrice && price >= expensivePrice && profile.expensive() != null) {
            return profile.expensive();
        }
        return profile.normal();
    }
}
