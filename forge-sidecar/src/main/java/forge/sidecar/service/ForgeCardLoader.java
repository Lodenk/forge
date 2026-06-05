package forge.sidecar.service;

import forge.sidecar.model.CardRef;
import forge.CardStorageReader;
import forge.StaticData;
import forge.deck.Deck;
import forge.ImageKeys;
import forge.util.Lang;
import forge.util.Localizer;
import forge.ai.AIOption;
import forge.ai.LobbyPlayerAi;
import forge.card.CardDb;
import forge.card.CardType;
import forge.util.FileSection;
import forge.util.FileUtil;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages a warm Forge Game instance for oracle queries.
 * Cards are loaded by name via StaticData / CardDb.
 * The Game object is a shared singleton — callers must clean up via clearBattlefield()
 * after each request.
 */
@Service
public class ForgeCardLoader {

    @Value("${forge.res.path:D:/ForgeOracle/forge-gui/res}")
    private String resPath;

    private StaticData staticData;
    private Game sharedGame;
    private Player playerA;

    private final Map<String, PaperCard> cardCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Localizer.getInstance().initialize("en-US", resPath + "/languages");
        Lang.createInstance("en-US");
        ImageKeys.initializeDirs("", java.util.Collections.emptyMap(), "", "", "", "", "", "", "");

        String cardsFolder   = resPath + "/cardsfolder";
        String editionsFolder = resPath + "/editions";
        String blockFolder   = resPath + "/blockdata";
        String tokensFolder  = resPath + "/tokenscripts";

        CardStorageReader cardReader  = new CardStorageReader(cardsFolder,  CardStorageReader.ProgressObserver.emptyObserver, false);
        CardStorageReader tokenReader = new CardStorageReader(tokensFolder, CardStorageReader.ProgressObserver.emptyObserver, false);

        staticData = new StaticData(
            cardReader, tokenReader,
            null, null,
            editionsFolder, editionsFolder,
            blockFolder, "",
            "latest-art-all-editions",
            false, false, false, false
        );

        // Load creature/land/etc. subtype lists so CardType.isACreatureType("Sliver") works.
        // FModel.loadDynamicGamedata() normally does this but isn't available without forge-gui.
        if (!CardType.Constant.LOADED.isSet()) {
            Map<String, List<String>> typeSections = FileSection.parseSections(
                FileUtil.readFile(resPath + "/lists/TypeLists.txt"));
            for (Map.Entry<String, List<String>> e : typeSections.entrySet()) {
                CardType.Helper.parseTypes(e.getKey(), e.getValue());
            }
            CardType.Constant.LOADED.set();
        }

        GameRules rules = new GameRules(GameType.Constructed);

        LobbyPlayerAi lobbyA = new LobbyPlayerAi("OracleA", EnumSet.noneOf(AIOption.class));
        LobbyPlayerAi lobbyB = new LobbyPlayerAi("OracleB", EnumSet.noneOf(AIOption.class));

        RegisteredPlayer rp1 = new RegisteredPlayer(new Deck("oracle-a")).setPlayer(lobbyA);
        RegisteredPlayer rp2 = new RegisteredPlayer(new Deck("oracle-b")).setPlayer(lobbyB);

        Match match = new Match(rules, List.of(rp1, rp2), "oracle");
        sharedGame = new Game(List.of(rp1, rp2), rules, match);

        playerA = sharedGame.getPlayers().get(0);
    }

    /**
     * Resolve a CardRef to a Forge PaperCard by canonical name (or faceName for multi-face).
     */
    public PaperCard lookupPaper(CardRef ref) {
        String lookupName = (ref.faceName() != null && !ref.faceName().isBlank()) ? ref.faceName() : ref.name();
        return cardCache.computeIfAbsent(lookupName, name -> {
            CardDb db = staticData.getCommonCards();
            return db.getCard(name);
        });
    }

    /**
     * Load a CardRef into a live Forge Card object on playerA's battlefield.
     * Returns null if the card name can't be resolved.
     */
    public Card load(CardRef ref) {
        PaperCard paper = lookupPaper(ref);
        if (paper == null) return null;

        Card card = CardFactory.getCard(paper, playerA, sharedGame);
        playerA.getZone(ZoneType.Battlefield).add(card);
        return card;
    }

    /**
     * After loading all cards, run a full static-effect pass so continuous
     * abilities (Sliver buffs, Glorious Anthem, etc.) apply across all loaded cards.
     */
    public void recalculateStaticEffects() {
        sharedGame.getAction().checkStaticAbilities(false);
    }

    /**
     * Remove all cards added during a request so the shared game doesn't accumulate state.
     */
    public Game getGame() {
        return sharedGame;
    }

    public void clearBattlefield() {
        playerA.getZone(ZoneType.Battlefield).removeAllCards(true);
        sharedGame.getPlayers().get(1).getZone(ZoneType.Battlefield).removeAllCards(true);
    }
}
