package forge.sidecar.service;

import forge.game.card.Card;

import java.util.*;
import java.util.function.Function;

/**
 * Parses Forge SVar ability text into VTT-compatible effect maps,
 * following SubAbility$ chains to capture multi-step effects.
 *
 * Forge SVar format: "DB$ Verb | Key$ Value | Key$ Value | ..."
 *
 * Abstract target tokens in emitted effects:
 *   "SELF"          - the watcher-card's controlling seat
 *   "TRIGGER_CARD"  - the watcher card's instanceId
 *   "EVENT_CARD"    - the card that caused the trigger
 *   "TARGETED"      - an interactive target chosen by the VTT
 *   "OPPONENT"      - the first non-eliminated opponent seat
 *
 * TypeScript resolves these tokens to concrete seatIds/instanceIds.
 */
public final class SvarTranslator {

    private static final int MAX_CHAIN_DEPTH = 5;

    private SvarTranslator() {}

    // ── Chain result ──────────────────────────────────────────────────────────

    /** Result of parseChain: translated effects plus a count of untranslatable steps. */
    public record ChainResult(List<Map<String, Object>> effects, int untranslatableSteps) {}

    // ── Parsing ───────────────────────────────────────────────────────────────

    /** Parse "DB$ Draw | Defined$ You | NumCards$ 1" into a parameter map. */
    public static Map<String, String> parseSVar(String svar) {
        Map<String, String> params = new LinkedHashMap<>();
        if (svar == null || svar.isBlank()) return params;
        for (String part : svar.split("\\s*\\|\\s*")) {
            int idx = part.indexOf('$');
            if (idx > 0) {
                params.put(part.substring(0, idx).trim(), part.substring(idx + 1).trim());
            }
        }
        return params;
    }

    /**
     * Translate a parsed SVar map to a VTT effect map.
     * Returns null when the DB type is unknown or untranslatable.
     */
    public static Map<String, Object> translate(Map<String, String> params) {
        String db = params.get("DB");
        if (db == null) return null;

        Map<String, Object> effect = new LinkedHashMap<>();

        switch (db) {
            case "Draw" -> {
                Object count = parseAmountParam(params, "NumCards", 1);
                effect.put("type",   "DRAW_CARD");
                effect.put("count",  count);
                effect.put("target", resolveDefinedOrTgts(params, "SELF"));
            }
            case "PutCounter" -> {
                String defined = params.getOrDefault("Defined", "Self");
                String ctype   = params.getOrDefault("CounterType", "P1P1").toLowerCase();
                int    amount  = parseIntParam(params, "CounterNum", 1);
                effect.put("type",        "ADD_COUNTER");
                effect.put("instanceId",  resolveTarget(defined));
                effect.put("counterType", ctype);
                effect.put("delta",       amount);
            }
            case "RemoveCounter" -> {
                String defined = params.getOrDefault("Defined", "Self");
                String ctype   = params.getOrDefault("CounterType", "P1P1").toLowerCase();
                int    amount  = parseIntParam(params, "CounterNum", 1);
                effect.put("type",        "ADD_COUNTER");
                effect.put("instanceId",  resolveTarget(defined));
                effect.put("counterType", ctype);
                effect.put("delta",       -amount);
            }
            case "GainLife" -> {
                Object amount = parseAmountParam(params, "LifeAmount", 1);
                effect.put("type",   "ADJUST_LIFE");
                effect.put("target", resolveDefinedOrTgts(params, "SELF"));
                effect.put("delta",  amount);
            }
            case "LoseLife" -> {
                Object amount = negateAmount(parseAmountParam(params, "LifeAmount", 1));
                // ValidTgts$ Player means interactive target; default to OPPONENT.
                String defaultTarget = params.containsKey("ValidTgts") ? "OPPONENT" : "SELF";
                effect.put("type",   "ADJUST_LIFE");
                effect.put("target", resolveDefinedOrTgts(params, defaultTarget));
                effect.put("delta",  amount);
            }
            case "DealDamage" -> {
                Object amount = negateAmount(parseAmountParam(params, "NumDmg", 1));
                String defaultTarget = params.containsKey("ValidTgts") ? "OPPONENT" : "SELF";
                effect.put("type",   "ADJUST_LIFE");
                effect.put("target", resolveDefinedOrTgts(params, defaultTarget));
                effect.put("delta",  amount);
            }
            case "Mill" -> {
                Object count = parseAmountParam(params, "NumCards", 1);
                effect.put("type",   "MILL");
                effect.put("count",  count);
                effect.put("target", resolveDefinedOrTgts(params, "SELF"));
            }
            case "Scry" -> {
                Object count = parseAmountParam(params, "ScryNum", 1);
                effect.put("type",  "SCRY");
                effect.put("count", count);
            }
            case "Discard" -> {
                Object count = parseAmountParam(params, "NumCards", 1);
                effect.put("type",   "DISCARD_CARD");
                effect.put("count",  count);
                effect.put("target", resolveDefinedOrTgts(params, "SELF"));
                effect.put("mode",   params.getOrDefault("Mode", "TgtChoose"));
            }
            case "Sacrifice" -> {
                Object count = parseAmountParam(params, "Amount", 1);
                effect.put("type",   "SACRIFICE_PERMANENT");
                effect.put("count",  count);
                effect.put("target", resolveDefinedOrTgts(params, "SELF"));
                if (params.containsKey("SacValid")) {
                    effect.put("valid", params.get("SacValid"));
                }
            }
            case "Destroy" -> {
                effect.put("type",       "DESTROY_PERMANENT");
                effect.put("instanceId", resolveDefinedOrTgts(params, "TARGETED"));
                if (params.containsKey("ValidTgts")) {
                    effect.put("valid", params.get("ValidTgts"));
                }
            }
            case "Token" -> {
                Object count = parseAmountParam(params, "TokenAmount", 1);
                effect.put("type",        "CREATE_TOKEN");
                effect.put("count",       count);
                effect.put("tokenScript", params.getOrDefault("TokenScript", ""));
                effect.put("target",      resolveTokenOwner(params));
                if ("True".equals(params.get("TokenTapped"))) {
                    effect.put("tapped", true);
                }
            }
            case "ChangeZone" -> {
                effect.put("type",        "MOVE_CARD");
                effect.put("instanceId",  resolveDefinedOrTgts(params, "TARGETED"));
                effect.put("origin",      params.getOrDefault("Origin", "All"));
                effect.put("destination", params.getOrDefault("Destination", "Battlefield"));
                if (params.containsKey("ChangeType")) {
                    effect.put("valid", params.get("ChangeType"));
                } else if (params.containsKey("ValidTgts")) {
                    effect.put("valid", params.get("ValidTgts"));
                }
                if ("True".equals(params.get("Tapped"))) {
                    effect.put("tapped", true);
                }
            }
            default -> {
                return null;
            }
        }

        return effect;
    }

    /**
     * Follow the SubAbility$ chain starting from svarText, translating each step.
     * Tracks how many steps were untranslatable so callers can set confidence accurately.
     *
     * @param svarText  the text of the first SVar (the trigger's Execute target)
     * @param card      the Forge Card to resolve SubAbility$ SVar names
     */
    public static ChainResult parseChain(String svarText, Card card) {
        Function<String, String> resolver = card == null ? name -> null : card::getSVar;
        return parseChain(svarText, resolver);
    }

    static ChainResult parseChain(String svarText, Function<String, String> svarResolver) {
        List<Map<String, Object>> results = new ArrayList<>();
        int untranslatable = 0;
        String current = svarText;
        int depth = 0;
        Set<String> seen = new HashSet<>();

        while (current != null && !current.isBlank()) {
            if (depth++ >= MAX_CHAIN_DEPTH || !seen.add(current)) {
                untranslatable++;
                break;
            }
            Map<String, String> params = parseSVar(current);
            String subAbility = params.get("SubAbility");

            Map<String, Object> translated = translate(params);
            if (translated != null) {
                results.add(translated);
            } else {
                untranslatable++;
            }

            if (subAbility != null && svarResolver != null) {
                current = svarResolver.apply(subAbility);
            } else {
                break;
            }
        }

        return new ChainResult(results, untranslatable);
    }

    /** Convenience single-step translate (no chain, no Card reference). */
    public static Map<String, Object> parseAndTranslate(String svar) {
        if (svar == null || svar.isBlank()) return null;
        return translate(parseSVar(svar));
    }

    // ── Target resolution ────────────────────────────────────────────────────

    /**
     * Resolve from Defined$ or ValidTgts$ params.
     * @param fallback  default token when neither key is present
     */
    private static String resolveDefinedOrTgts(Map<String, String> params, String fallback) {
        String defined = params.get("Defined");
        if (defined != null) return resolveTarget(defined);
        String validTgts = params.get("ValidTgts");
        if (validTgts != null) return resolveTargetFromValidTgts(validTgts);
        return fallback;
    }

    private static String resolveTarget(String defined) {
        if (defined == null) return "SELF";
        if (defined.contains("Opponent") || defined.contains("NotYou")) return "OPPONENT";
        if (defined.contains("Targeted")) return "TARGETED";
        if (defined.contains("TriggeredCard") || defined.contains("TriggeredNewCard")) return "EVENT_CARD";
        if (defined.contains("Remembered")) return "REMEMBERED";
        return switch (defined) {
            case "Self"   -> "TRIGGER_CARD";
            case "You"    -> "SELF";
            case "Player" -> "SELF";
            default       -> "SELF";
        };
    }

    private static String resolveTargetFromValidTgts(String validTgts) {
        if (validTgts == null) return "SELF";
        if (validTgts.contains("Opponent")) return "OPPONENT";
        if (validTgts.contains("Player"))   return "OPPONENT";
        return "TARGETED";
    }

    private static String resolveTokenOwner(Map<String, String> params) {
        String owner = params.get("TokenOwner");
        if (owner == null || owner.equals("You")) return "SELF";
        return resolveTarget(owner);
    }

    // Utility

    private static int parseIntParam(Map<String, String> params, String key, int defaultVal) {
        String val = params.get(key);
        if (val == null) return defaultVal;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static Object parseAmountParam(Map<String, String> params, String key, int defaultVal) {
        String val = params.get(key);
        if (val == null || val.isBlank()) return defaultVal;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return val.trim();
        }
    }

    private static Object negateAmount(Object amount) {
        if (amount instanceof Integer i) {
            return -i;
        }
        return "-" + amount;
    }
}
