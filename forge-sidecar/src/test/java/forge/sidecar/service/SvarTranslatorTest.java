package forge.sidecar.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SvarTranslatorTest {

    @Test
    void parseSVarAcceptsSpacedAndUnspacedSeparators() {
        Map<String, String> params = SvarTranslator.parseSVar(
            "DB$Draw | Defined$ You | NumCards$ 2");

        assertThat(params).containsEntry("DB", "Draw");
        assertThat(params).containsEntry("Defined", "You");
        assertThat(params).containsEntry("NumCards", "2");
    }

    @Test
    void translatesCommonScalarEffects() {
        assertThat(SvarTranslator.parseAndTranslate(
            "DB$ Draw | Defined$ You | NumCards$ 2"))
            .containsEntry("type", "DRAW_CARD")
            .containsEntry("target", "SELF")
            .containsEntry("count", 2);

        assertThat(SvarTranslator.parseAndTranslate(
            "DB$ LoseLife | Defined$ Opponent | LifeAmount$ 3"))
            .containsEntry("type", "ADJUST_LIFE")
            .containsEntry("target", "OPPONENT")
            .containsEntry("delta", -3);
    }

    @Test
    void preservesVariableAmounts() {
        assertThat(SvarTranslator.parseAndTranslate(
            "DB$ Token | TokenScript$ c_a_treasure_sac | TokenAmount$ X"))
            .containsEntry("type", "CREATE_TOKEN")
            .containsEntry("tokenScript", "c_a_treasure_sac")
            .containsEntry("count", "X");

        assertThat(SvarTranslator.parseAndTranslate(
            "DB$ DealDamage | Defined$ You | NumDmg$ X"))
            .containsEntry("type", "ADJUST_LIFE")
            .containsEntry("target", "SELF")
            .containsEntry("delta", "-X");
    }

    @Test
    void translatesInteractivePermanentEffects() {
        assertThat(SvarTranslator.parseAndTranslate(
            "DB$ Destroy | ValidTgts$ Artifact"))
            .containsEntry("type", "DESTROY_PERMANENT")
            .containsEntry("instanceId", "TARGETED")
            .containsEntry("valid", "Artifact");

        assertThat(SvarTranslator.parseAndTranslate(
            "DB$ ChangeZone | Origin$ Graveyard | Destination$ Battlefield | Defined$ TriggeredNewCardLKICopy"))
            .containsEntry("type", "MOVE_CARD")
            .containsEntry("instanceId", "EVENT_CARD")
            .containsEntry("origin", "Graveyard")
            .containsEntry("destination", "Battlefield");
    }

    @Test
    void parseChainFollowsSubAbilities() {
        SvarTranslator.ChainResult result = SvarTranslator.parseChain(
            "DB$ Draw | SubAbility$ DBLose",
            name -> "DBLose".equals(name) ? "DB$ LoseLife | Defined$ You | LifeAmount$ 1" : null);

        assertThat(result.untranslatableSteps()).isZero();
        assertThat(result.effects()).hasSize(2);
        assertThat(result.effects().get(0)).containsEntry("type", "DRAW_CARD");
        assertThat(result.effects().get(1)).containsEntry("type", "ADJUST_LIFE");
    }

    @Test
    void parseChainReportsUnsupportedAndStopsCycles() {
        SvarTranslator.ChainResult unsupported = SvarTranslator.parseChain(
            "DB$ Unknown | SubAbility$ DBDraw",
            name -> "DB$ Draw");

        assertThat(unsupported.untranslatableSteps()).isEqualTo(1);
        assertThat(unsupported.effects()).hasSize(1);

        SvarTranslator.ChainResult cycle = SvarTranslator.parseChain(
            "DB$ Draw | SubAbility$ Loop",
            name -> "DB$ Draw | SubAbility$ Loop");

        assertThat(cycle.untranslatableSteps()).isEqualTo(1);
        assertThat(cycle.effects()).hasSize(1);
    }
}
