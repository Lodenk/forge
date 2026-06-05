package forge.sidecar.controller;

import forge.sidecar.model.CardRef;
import forge.sidecar.model.OracleResponse;
import forge.sidecar.service.ForgeCardLoader;
import forge.sidecar.service.SvarTranslator;
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
        CardRef entered,
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

            TriggerType targetType = TriggerType.ChangesZone;

            List<Map<String, Object>> effects  = new ArrayList<>();
            List<String>             warnings  = new ArrayList<>();
            boolean anyTriggersMatched = false;
            boolean anyTranslated      = false;
            boolean anyUntranslated    = false;

            for (Trigger t : card.getTriggers()) {
                if (t.getMode() != targetType) continue;

                String dest   = t.hasParam("Destination") ? t.getParam("Destination") : "";
                String origin = t.hasParam("Origin")      ? t.getParam("Origin")      : "";

                boolean isEtb   = "permanentEntered".equals(req.event()) && "Battlefield".equals(dest);
                boolean isDeath = "permanentDied".equals(req.event())    && "Battlefield".equals(origin);

                if (!isEtb && !isDeath) continue;

                if (!matchesControllerFilter(
                        t.hasParam("ValidCard") ? t.getParam("ValidCard") : null,
                        req.entered(),
                        req.context())) {
                    continue;
                }

                anyTriggersMatched = true;

                if (!t.hasParam("Execute")) {
                    warnings.add("Trigger has no Execute SVar: " + t);
                    anyUntranslated = true;
                    continue;
                }

                String svarName = t.getParam("Execute");
                String svarText = card.getSVar(svarName);
                if (svarText == null || svarText.isBlank()) {
                    warnings.add("Execute SVar not found: " + svarName);
                    anyUntranslated = true;
                    continue;
                }

                SvarTranslator.ChainResult chain = SvarTranslator.parseChain(svarText, card);
                if (!chain.effects().isEmpty()) {
                    effects.addAll(chain.effects());
                    anyTranslated = true;
                }
                if (chain.untranslatableSteps() > 0) {
                    // Some steps in the chain couldn't be translated — mark partial so the
                    // caller knows the effect list may be incomplete.
                    anyUntranslated = true;
                    warnings.add("Partial translation for " + svarName + ": "
                        + chain.untranslatableSteps() + " step(s) not translatable");
                }
                if (chain.effects().isEmpty() && chain.untranslatableSteps() == 0) {
                    // Empty chain — no Execute SVar body or fully empty
                    warnings.add("Empty SVar chain for: " + svarName);
                    anyUntranslated = true;
                }
            }

            if (!anyTriggersMatched) {
                return OracleResponse.unsupported(
                    "No matching " + req.event() + " trigger on: " + req.trigger().name());
            }

            // Confidence: exact if all triggers translated; partial if mixed or none translated
            // (partial still tells the caller that the trigger DOES fire, just may be incomplete).
            String confidence = (anyTranslated && !anyUntranslated) ? "exact" : "partial";
            return OracleResponse.forge(confidence, effects, warnings);

        } catch (Exception e) {
            return OracleResponse.unsupported("Error reading triggers: " + e.getMessage());
        } finally {
            loader.clearBattlefield();
        }
    }

    static boolean matchesControllerFilter(String validCard, CardRef entered, Map<String, String> context) {
        if (validCard == null || entered == null || context == null) {
            return true;
        }

        String enteredSeatId = entered.seatId();
        String selfSeatId = context.get("selfSeatId");
        if (enteredSeatId == null || selfSeatId == null) {
            return true;
        }

        if (validCard.contains("YouCtrl") && !enteredSeatId.equals(selfSeatId)) {
            return false;
        }
        if (validCard.contains("OppCtrl") && enteredSeatId.equals(selfSeatId)) {
            return false;
        }
        return true;
    }
}
