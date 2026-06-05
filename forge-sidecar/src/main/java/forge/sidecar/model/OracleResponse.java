package forge.sidecar.model;

import java.util.List;
import java.util.Map;

public record OracleResponse(
    String source,          // "forge" | "local" | "unsupported"
    String confidence,      // "exact" | "partial" | "unsupported"
    List<Map<String, Object>> effects,
    List<String> warnings
) {
    public static OracleResponse unsupported(String reason) {
        return new OracleResponse("unsupported", "unsupported", List.of(), List.of(reason));
    }

    public static OracleResponse forge(String confidence, List<Map<String, Object>> effects, List<String> warnings) {
        return new OracleResponse("forge", confidence, effects, warnings);
    }
}
