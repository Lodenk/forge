package forge.sidecar.controller;

import forge.sidecar.model.CardRef;
import forge.sidecar.model.OracleResponse;
import forge.sidecar.service.ForgeCardLoader;
import forge.game.card.Card;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerType;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/trigger-preview")
public class TriggerController {

    private static final Set<String> ALLOWED_EVENTS = Set.of("permanentEntered", "permanentDied");

    private final ForgeCardLoader loader;

    public TriggerController(ForgeCardLoader loader) {
        this.loader = loader;
    }

    public record TriggerRequest(
        CardRef trigger,
        String event,
        CardRef entered,   // the card that entered/died, nullable for some events
        Map<String, String> context
    ) {}

    @PostMapping
    public OracleResponse triggerPreview(@RequestBody TriggerRequest req) {
        if (!ALLOWED_EVENTS.contains(req.event())) {
            return OracleResponse.unsupported("Event type not in allowlist: " + req.event());
        }

        try {
            loader.clearBattlefield();
            Card card = loader.load(req.trigger());
            if (card == null) {
                return OracleResponse.unsupported("Trigger card not found: " + req.trigger().name());
            }

            TriggerType targetType = switch (req.event()) {
                case "permanentEntered" -> TriggerType.ChangesZone;
                case "permanentDied"    -> TriggerType.ChangesZone;
                default -> null;
            };

            List<Map<String, Object>> effects = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            boolean anyMatched = false;

            for (Trigger t : card.getTriggers()) {
                if (t.getMode() != targetType) continue;

                // For zone-change triggers, check Destination=Battlefield (ETB) or Origin=Battlefield (death)
                String dest   = t.hasParam("Destination") ? t.getParam("Destination") : "";
                String origin = t.hasParam("Origin")      ? t.getParam("Origin")      : "";

                boolean isEtb   = "permanentEntered".equals(req.event()) && "Battlefield".equals(dest);
                boolean isDeath = "permanentDied".equals(req.event())    && "Battlefield".equals(origin);

                if (!isEtb && !isDeath) continue;
                anyMatched = true;

                // Build a description-level effect. This is the spike's key test point:
                // can we get semantic effect data, or just a human-readable description?
                Map<String, Object> effect = new LinkedHashMap<>();
                effect.put("type", "TRIGGER_FIRED");
                effect.put("triggerCard", req.trigger().name());
                effect.put("event", req.event());
                effect.put("triggerDescription", t.toString());

                // Attempt to extract the Execute SVar for more detail
                if (t.hasParam("Execute")) {
                    String svarName = t.getParam("Execute");
                    String svarText = card.getSVar(svarName);
                    effect.put("executeAbility", svarText != null ? svarText : svarName);
                }

                effects.add(effect);
            }

            if (!anyMatched) {
                return OracleResponse.unsupported("No matching " + req.event() + " trigger found on: " + req.trigger().name());
            }

            // Mark partial: we can confirm triggers fire and describe them, but
            // translating Execute SVars to VttActionProposals requires more work.
            warnings.add("Trigger descriptions are informational; VTT action translation not yet implemented.");
            return OracleResponse.forge("partial", effects, warnings);

        } catch (Exception e) {
            return OracleResponse.unsupported("Error reading triggers: " + e.getMessage());
        } finally {
            loader.clearBattlefield();
        }
    }
}
