package forge.sidecar.controller;

import forge.sidecar.model.CardRef;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerControllerTest {

    private static final CardRef OWN_CARD = new CardRef(
        null, "Grizzly Bears", null, null, null, "bear-1", "seat-a", null, null);

    private static final CardRef OPPONENT_CARD = new CardRef(
        null, "Grizzly Bears", null, null, null, "bear-2", "seat-b", null, null);

    @Test
    void controllerFilterAllowsOwnCardsForYouCtrl() {
        assertThat(TriggerController.matchesControllerFilter(
            "Creature.YouCtrl", OWN_CARD, Map.of("selfSeatId", "seat-a")))
            .isTrue();
    }

    @Test
    void controllerFilterRejectsOpponentCardsForYouCtrl() {
        assertThat(TriggerController.matchesControllerFilter(
            "Creature.YouCtrl", OPPONENT_CARD, Map.of("selfSeatId", "seat-a")))
            .isFalse();
    }

    @Test
    void controllerFilterAllowsOpponentCardsForOppCtrl() {
        assertThat(TriggerController.matchesControllerFilter(
            "Creature.OppCtrl", OPPONENT_CARD, Map.of("selfSeatId", "seat-a")))
            .isTrue();
    }

    @Test
    void controllerFilterRejectsOwnCardsForOppCtrl() {
        assertThat(TriggerController.matchesControllerFilter(
            "Creature.OppCtrl", OWN_CARD, Map.of("selfSeatId", "seat-a")))
            .isFalse();
    }

    @Test
    void controllerFilterFailsOpenWhenContextIsIncomplete() {
        assertThat(TriggerController.matchesControllerFilter("Creature.YouCtrl", OWN_CARD, Map.of()))
            .isTrue();
        assertThat(TriggerController.matchesControllerFilter(null, OWN_CARD, Map.of("selfSeatId", "seat-a")))
            .isTrue();
    }
}
