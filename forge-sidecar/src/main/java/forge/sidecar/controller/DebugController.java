package forge.sidecar.controller;

import forge.sidecar.model.CardRef;
import forge.sidecar.service.ForgeCardLoader;
import forge.game.card.Card;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityLayer;
import forge.game.staticability.StaticAbilityMode;
import forge.game.zone.ZoneType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/v1/debug")
@ConditionalOnProperty(name = "forge.sidecar.debug.enabled", havingValue = "true")
public class DebugController {

    private final ForgeCardLoader loader;

    public DebugController(ForgeCardLoader loader) {
        this.loader = loader;
    }

    @GetMapping("/card")
    public Map<String, Object> debugCard(@RequestParam("name") String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            loader.clearBattlefield();

            CardRef ref = new CardRef(null, name, null, null, null, "dbg", null, null, null);
            Card card = loader.load(ref);
            if (card == null) {
                result.put("error", "Card not found: " + name);
                return result;
            }

            result.put("name", card.getName());
            result.put("isInPlay", card.isInPlay());
            result.put("zone", card.getZone() != null ? card.getZone().getZoneType().toString() : "null");
            result.put("basePower", card.getBasePower());
            result.put("isCreature", card.isCreature());

            result.put("battlefieldCount", loader.getGame().getCardsIn(ZoneType.Battlefield).size());

            List<Map<String, Object>> abilities = new ArrayList<>();
            for (StaticAbility stAb : card.getStaticAbilities()) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("mode", stAb.getParam("Mode"));
                a.put("zonesCheck", stAb.zonesCheck());
                a.put("layers", stAb.getLayers().toString());
                a.put("checkModeContContinuous", stAb.checkMode(StaticAbilityMode.Continuous));
                a.put("isSuppressed", stAb.isSuppressed());
                a.put("hasParam_Affected", stAb.hasParam("Affected"));
                a.put("Affected", stAb.getParam("Affected"));

                // Check shouldApplyContinuousAbility manually
                boolean layersContainMODIFYPT = stAb.getLayers().contains(StaticAbilityLayer.MODIFYPT);
                boolean checkCondsCont = stAb.checkMode(StaticAbilityMode.Continuous) && stAb.zonesCheck();
                boolean inHostCardAbilities = stAb.getHostCard().getStaticAbilities().contains(stAb);
                boolean inHostCardHidden = stAb.getHostCard().getHiddenStaticAbilities().contains(stAb);
                a.put("layersContain_MODIFYPT", layersContainMODIFYPT);
                a.put("checkCondsCont", checkCondsCont);
                a.put("inHostCardStaticAbilities", inHostCardAbilities);
                a.put("inHostCardHiddenAbilities", inHostCardHidden);
                a.put("shouldApply_MODIFYPT", layersContainMODIFYPT && checkCondsCont && (inHostCardAbilities || inHostCardHidden));
                abilities.add(a);
            }
            result.put("staticAbilities", abilities);

            // Test isValid directly for "Creature.Sliver"
            result.put("isValid_Creature", card.isValid("Creature", card.getController(), card, null));
            result.put("isValid_Sliver", card.isValid("Sliver", card.getController(), card, null));
            result.put("isValid_Creature.Sliver", card.isValid("Creature.Sliver", card.getController(), card, null));
            result.put("cardType", card.getType().toString());

            // Test what getCardsIn returns
            List<String> bfNames = new ArrayList<>();
            for (Card bfCard : loader.getGame().getCardsIn(ZoneType.Battlefield)) {
                bfNames.add(bfCard.getName());
            }
            result.put("cardsInBattlefield", bfNames);

            result.put("netPower_before", card.getNetPower());
            result.put("tempPowerBoost_before", card.getTempPowerBoost());

            loader.recalculateStaticEffects();

            result.put("netPower_after", card.getNetPower());
            result.put("tempPowerBoost_after", card.getTempPowerBoost());
        } catch (Exception e) {
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            loader.clearBattlefield();
        }
        return result;
    }
}
