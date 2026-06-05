package forge.sidecar.controller;

import forge.sidecar.model.CardRef;
import forge.sidecar.model.OracleResponse;
import forge.sidecar.service.ForgeCardLoader;
import forge.game.card.Card;
import forge.game.combat.CombatUtil;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/combat")
public class CombatController {

    private final ForgeCardLoader loader;

    public CombatController(ForgeCardLoader loader) {
        this.loader = loader;
    }

    public record BlockerRequest(CardRef attacker, List<CardRef> potentialBlockers) {}

    @PostMapping("/eligible-blockers")
    public OracleResponse eligibleBlockers(@RequestBody BlockerRequest req) {
        try {
            loader.clearBattlefield();

            Card attacker = loader.load(req.attacker());
            if (attacker == null) {
                return OracleResponse.unsupported("Attacker card not found: " + req.attacker().name());
            }

            List<Map<String, Object>> effects = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            for (CardRef blockerRef : req.potentialBlockers()) {
                Card blocker = loader.load(blockerRef);
                if (blocker == null) {
                    warnings.add("Blocker card not found, skipped: " + blockerRef.name());
                    continue;
                }
                boolean canBlock = CombatUtil.canBlock(attacker, blocker);
                effects.add(Map.of(
                    "type", "BLOCKER_ELIGIBILITY",
                    "instanceId", blockerRef.instanceId() != null ? blockerRef.instanceId() : blockerRef.name(),
                    "eligible", canBlock
                ));
            }

            return OracleResponse.forge("exact", effects, warnings);
        } catch (Exception e) {
            return OracleResponse.unsupported("Error evaluating blockers: " + e.getMessage());
        } finally {
            loader.clearBattlefield();
        }
    }
}
