package forge.sidecar.controller;

import forge.sidecar.model.CardRef;
import forge.sidecar.model.OracleResponse;
import forge.sidecar.service.ForgeCardLoader;
import forge.game.card.Card;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/continuous")
public class ContinuousController {

    private final ForgeCardLoader loader;

    public ContinuousController(ForgeCardLoader loader) {
        this.loader = loader;
    }

    public record StatsRequest(List<CardRef> permanents) {}

    @PostMapping("/creature-stats")
    public OracleResponse creatureStats(@RequestBody StatsRequest req) {
        try {
            loader.clearBattlefield();

            // Load all permanents so continuous effects from each can apply to the others.
            // Track pairs so we don't misalign refs when some cards are missing.
            List<Map.Entry<Card, CardRef>> pairs = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            for (CardRef ref : req.permanents()) {
                Card c = loader.load(ref);
                if (c == null) {
                    warnings.add("Card not found, skipped: " + ref.name());
                } else {
                    pairs.add(Map.entry(c, ref));
                }
            }

            loader.recalculateStaticEffects();

            List<Map<String, Object>> effects = new ArrayList<>();
            for (var pair : pairs) {
                Card c = pair.getKey();
                CardRef ref = pair.getValue();
                if (!c.isCreature()) continue;

                List<String> keywords = new ArrayList<>();
                for (var kw : c.getKeywords()) {
                    keywords.add(kw.toString());
                }

                effects.add(Map.of(
                    "type", "EFFECTIVE_CREATURE_STATS",
                    "instanceId", ref.instanceId() != null ? ref.instanceId() : ref.name(),
                    "power", c.getNetPower(),
                    "toughness", c.getNetToughness(),
                    "keywords", keywords
                ));
            }

            return OracleResponse.forge("exact", effects, warnings);
        } catch (Exception e) {
            return OracleResponse.unsupported("Error computing creature stats: " + e.getMessage());
        } finally {
            loader.clearBattlefield();
        }
    }
}
