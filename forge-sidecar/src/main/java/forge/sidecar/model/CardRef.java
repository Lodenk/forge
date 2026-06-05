package forge.sidecar.model;

public record CardRef(
    String scryfallId,
    String name,
    String layout,       // "normal" | "transform" | "modal_dfc" | "adventure" | "split"
    String faceName,     // active face for MDFCs/split/adventure, nullable
    String oracleId,     // nullable
    String instanceId,   // VTT instance identifier, passed through opaquely
    String seatId,       // owning player seat, nullable
    Integer basePower,
    Integer baseToughness
) {}
