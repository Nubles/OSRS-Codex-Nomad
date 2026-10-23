package com.codex.nomad;

import com.codex.nomad.core.Costume;
import com.codex.nomad.core.Death;
import com.codex.nomad.core.Fetch;
import com.codex.nomad.core.Items;
import com.codex.nomad.core.Kind;
import com.codex.nomad.core.Nomad;
import com.codex.nomad.core.Route;
import com.codex.nomad.core.StashBook;
import com.codex.nomad.core.Store;
import com.codex.nomad.core.Trip;
import com.codex.nomad.overlay.Marker;
import com.codex.nomad.overlay.Plaque;
import com.codex.nomad.ui.Arrivals;
import com.codex.nomad.ui.Format;
import com.codex.nomad.ui.ItemIcons;
import com.codex.nomad.ui.NomadPanel;
import com.codex.nomad.ui.Sigil;
import com.codex.nomad.ui.Theme;
import com.codex.nomad.ui.View;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.cluescrolls.clues.emote.STASHUnit;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Codex Nomad",
	description = "The bank an ultimate ironman doesn't have: everything you carry and keep, deathpile timers, packing lists, a noting atlas and the route to Prifddinas",
	tags = {"uim", "ultimate", "ironman", "deathpile", "deathbank", "stash", "looting bag", "storage", "noting", "prifddinas", "codex"}
)
public class CodexNomadPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(CodexNomadPlugin.class);
	private static final String SAVE_KEY = "nomad";
	private static final String DAMAGED_KEY = "nomadDamaged";
	/** What the account holds, for the rest of the suite: {version,savedAt,items{id:qty},truncated}. */
	static final String HOLDINGS_KEY = "holdingsV1";
	static final String STEWARD_GROUP = "codexbanksteward";
	static final String STEWARD_KEY = "bankV1";
	private static final int SAVE_TICKS = 50;
	private static final int PUBLISH_TICKS = 2;
	/** Ticks a death is given to respawn before it is judged anyway. */
	private static final int DEATH_TICKS = 20;
	/** Ticks after login in which the game reports a deathbank, when its warning is on. */
	private static final int LOGIN_TICKS = 15;
	/** Tiles from a deathpile within which items picked up are taken from it. */
	private static final int PILE_REACH = 4;
	private static final int STASH_REACH = 6;
	private static final int COSTUME = 637;
	private static final int[] POUCH_RUNES = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
	};
	private static final int[] POUCH_AMOUNTS = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
	};
	private static final Set<Integer> POUCHES = new HashSet<>(java.util.Arrays.asList(
		ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER, ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER));
	private static final Set<Integer> LOOTING_BAGS = new HashSet<>(java.util.Arrays.asList(ItemID.LOOTING_BAG, ItemID.LOOTING_BAG_OPEN));

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private WorldMapPointManager worldMapPointManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Gson gson;

	@Inject
	private CodexNomadConfig config;

	@Inject
	private EventBus eventBus;

	@Inject
	private ModelOutlineRenderer outlines;

	private final Plaque plaque = new Plaque();
	private Marker marker;
	private Highlights highlights;
	private Clicks clicks;
	private RouteGuide guide;
	/** The plaque as the last publish left it, before the route row is added each tick. */
	private List<Plaque.Row> plaqueRows = new ArrayList<>();
	private Color plaqueTint = Theme.GOLD;
	private String guidedKey;
	private final List<WorldMapPoint> mapPoints = new ArrayList<>();
	private final Map<Integer, String> names = new HashMap<>();
	private final Set<String> warned = new HashSet<>();
	/** Items taken out of the inventory and worn since the tick before last, for STASH deposits. */
	private final Map<Integer, Long> removed = new HashMap<>();
	private final Map<Integer, Long> removedBefore = new HashMap<>();

	private NomadPanel panel;
	private NavigationButton navButton;
	private StashBook stash;
	private Costume costume;
	private Route route;

	private String profile;
	private Nomad nomad;
	private Map<Integer, Long> lastInv = new HashMap<>();
	private Map<Integer, Long> lastWorn = new HashMap<>();
	private Map<Integer, Long> beforeDeath;
	private WorldPoint deathAt;
	private int deathRegion;
	private boolean deathInstanced;
	private int deathTick = -1;
	private int loginTick = -1;
	private boolean sawRetrieval;
	private int stashDeposit = -1;
	private int stashWithdraw = -1;
	private int ticks;
	private long lastTickMs;
	private boolean dirty;
	private boolean unsaved;

	@Provides
	CodexNomadConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CodexNomadConfig.class);
	}

	@Override
	protected void startUp()
	{
		stash = StashBook.load(gson);
		costume = Costume.load(gson);
		route = Route.load();
		marker = new Marker(client);
		guide = new RouteGuide(client, eventBus, route, id -> name(itemManager.canonicalize(id)));
		highlights = new Highlights(client, outlines, guide);
		clicks = new Clicks(client, guide, id -> name(itemManager.canonicalize(id)));
		Arrivals arrivals = new Arrivals();
		ItemIcons icons = (itemId, onLoaded) ->
		{
			AsyncBufferedImage image = itemManager.getImage(itemId);
			arrivals.ask(image, onLoaded);
			return image;
		};
		panel = new NomadPanel(icons, new NomadPanel.Actions()
		{
			@Override
			public void saveTrip(String name)
			{
				clientThread.invokeLater(() -> CodexNomadPlugin.this.saveTrip(name));
			}

			@Override
			public void useTrip(String name)
			{
				clientThread.invokeLater(() -> change(n -> n.active = name));
			}

			@Override
			public void forgetTrip(String name)
			{
				clientThread.invokeLater(() -> change(n -> n.forget(name)));
			}

			@Override
			public void forgetStore(String key)
			{
				clientThread.invokeLater(() -> change(n -> n.remove(key)));
			}

			@Override
			public void tickStep(String key, boolean done)
			{
				clientThread.invokeLater(() -> change(n -> n.tick(key, done)));
			}

			@Override
			public void follow(boolean on)
			{
				clientThread.invokeLater(() -> change(n -> n.following = on));
			}
		});
		navButton = NavigationButton.builder()
			.tooltip("Codex Nomad")
			.icon(Sigil.icon(16))
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(plaque);
		overlayManager.add(marker);
		overlayManager.add(highlights);
		overlayManager.add(clicks);
		clientThread.invokeLater(() ->
		{
			loadProfile();
			if (client.getGameState() == GameState.LOGGED_IN && nomad != null)
			{
				readCarried();
			}
			publish();
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(plaque);
		overlayManager.remove(marker);
		overlayManager.remove(highlights);
		overlayManager.remove(clicks);
		clientToolbar.removeNavigation(navButton);
		clearMap();
		RouteGuide g = guide;
		clientThread.invokeLater(() ->
		{
			g.stop();
			save();
			nomad = null;
			profile = null;
			reset();
		});
		panel = null;
	}

	private void reset()
	{
		names.clear();
		lastInv = new HashMap<>();
		lastWorn = new HashMap<>();
		beforeDeath = null;
		deathTick = -1;
		removed.clear();
		removedBefore.clear();
		stashDeposit = -1;
		stashWithdraw = -1;
		plaqueRows = new ArrayList<>();
		plaque.show(null, null, null);
		marker.show(null);
	}

	private interface Change
	{
		void apply(Nomad n);
	}

	/** Makes a change the panel asked for, on the client thread. */
	private void change(Change c)
	{
		if (nomad == null)
		{
			return;
		}
		c.apply(nomad);
		unsaved = true;
		publish();
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		CompletableFuture<Void> done = new CompletableFuture<>();
		clientThread.invokeLater(() ->
		{
			try
			{
				if (save())
				{
					configManager.sendConfig();
				}
			}
			finally
			{
				done.complete(null);
			}
		});
		event.waitFor(done);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		save();
		loadProfile();
		publish();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CodexNomadConfig.GROUP.equals(event.getGroup()) && event.getKey() != null && !event.getKey().startsWith(SAVE_KEY)
			&& !HOLDINGS_KEY.equals(event.getKey()))
		{
			clientThread.invokeLater(this::publish);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN)
		{
			save();
			reset();
			publish();
		}
		else if (state == GameState.LOGGED_IN)
		{
			if (loginTick < 0)
			{
				loginTick = client.getTickCount();
				sawRetrieval = false;
			}
		}
		else if (state == GameState.LOGGING_IN)
		{
			loginTick = -1;
			warned.clear();
		}
	}

	// ---- what is carried ----

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (nomad == null)
		{
			return;
		}
		int id = event.getContainerId();
		long now = System.currentTimeMillis();
		ItemContainer c = event.getItemContainer();
		if (id == InventoryID.INV || id == InventoryID.WORN)
		{
			Map<Integer, Long> after = contents(c);
			Map<Integer, Long> before = id == InventoryID.INV ? lastInv : lastWorn;
			Items.add(removed, Items.minus(before, after));
			if (id == InventoryID.INV)
			{
				nomad.touch(before, after);
				nomad.carry(Nomad.INVENTORY, after, now);
				pickUp(Items.minus(after, before));
				lastInv = after;
				readPouch();
			}
			else
			{
				nomad.carry(Nomad.WORN, after, now);
				lastWorn = after;
			}
			changed();
		}
		else if (id == InventoryID.LOOTING_BAG)
		{
			nomad.carry(Nomad.LOOTING_BAG, contents(c), now);
			changed();
		}
		else if (id == InventoryID.SEED_VAULT)
		{
			Store s = nomad.store("seedvault");
			if (s == null)
			{
				s = new Store("seedvault", Kind.SEED_VAULT, "Seed vault").at(1250, 3732, 0);
				nomad.put(s);
			}
			s.fill(contents(c), now);
			changed();
		}
		else if (id == InventoryID.GRAVESTONE && nomad.account == 2)
		{
			// An ultimate ironman has no gravestone: this window is an item retrieval service.
			Map<Integer, Long> held = contents(c);
			Store bank = nomad.deathbank();
			if (held.isEmpty())
			{
				if (bank != null)
				{
					nomad.remove(Nomad.DEATHBANK);
					say("Your deathbank is empty: everything is back with you.");
				}
			}
			else
			{
				if (bank == null)
				{
					bank = new Store(Nomad.DEATHBANK, Kind.DEATHBANK, place());
					bank.since = now;
					nomad.put(bank);
				}
				bank.fill(held, now);
			}
			changed();
		}
		else if ((id & 0x7FFF) == COSTUME)
		{
			Map<Integer, Long> shown = contents(c);
			String piece = costume.piece(shown);
			if (piece != null)
			{
				String key = "poh:" + piece.toLowerCase(Locale.ENGLISH);
				Store s = nomad.store(key);
				if (s == null)
				{
					s = new Store(key, Kind.COSTUME, piece);
					nomad.put(s);
				}
				s.fill(costume.held(piece, shown), now);
				changed();
			}
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (nomad == null)
		{
			return;
		}
		int v = event.getVarbitId();
		for (int i = 0; i < POUCH_RUNES.length; i++)
		{
			if (v == POUCH_RUNES[i] || v == POUCH_AMOUNTS[i])
			{
				readPouch();
				changed();
				return;
			}
		}
		if (v == VarbitID.IRONMAN)
		{
			nomad.account = event.getValue();
			changed();
		}
	}

	private void readCarried()
	{
		long now = System.currentTimeMillis();
		nomad.account = client.getVarbitValue(VarbitID.IRONMAN);
		lastInv = contents(client.getItemContainer(InventoryID.INV));
		lastWorn = contents(client.getItemContainer(InventoryID.WORN));
		nomad.carry(Nomad.INVENTORY, lastInv, now);
		nomad.carry(Nomad.WORN, lastWorn, now);
		ItemContainer bag = client.getItemContainer(InventoryID.LOOTING_BAG);
		if (bag != null)
		{
			nomad.carry(Nomad.LOOTING_BAG, contents(bag), now);
		}
		readPouch();
		changed();
	}

	private void readPouch()
	{
		boolean pouch = false;
		for (int id : lastInv.keySet())
		{
			pouch |= POUCHES.contains(id);
		}
		Map<Integer, Long> runes = new HashMap<>();
		if (pouch)
		{
			EnumComposition e = client.getEnum(EnumID.RUNEPOUCH_RUNE);
			for (int i = 0; i < POUCH_RUNES.length; i++)
			{
				int type = client.getVarbitValue(POUCH_RUNES[i]);
				int amount = client.getVarbitValue(POUCH_AMOUNTS[i]);
				int item = type > 0 && e != null ? e.getIntValue(type) : -1;
				if (item > 0 && amount > 0)
				{
					runes.merge(item, (long) amount, Long::sum);
				}
			}
		}
		nomad.carry(Nomad.RUNE_POUCH, runes, System.currentTimeMillis());
	}

	/** Items picked up beside a deathpile come out of it. */
	private void pickUp(Map<Integer, Long> gained)
	{
		Player me = client.getLocalPlayer();
		if (gained.isEmpty() || me == null)
		{
			return;
		}
		WorldPoint at = me.getWorldLocation();
		for (Store pile : nomad.stores(Kind.DEATHPILE))
		{
			if (pile.plane != at.getPlane() || Math.max(Math.abs(pile.x - at.getX()), Math.abs(pile.y - at.getY())) > PILE_REACH)
			{
				continue;
			}
			for (Map.Entry<Integer, Long> e : gained.entrySet())
			{
				long n = pile.take(e.getKey(), e.getValue());
				gained.put(e.getKey(), e.getValue() - n);
			}
			if (pile.isEmpty())
			{
				nomad.remove(pile.key);
				say("Deathpile cleared: you have everything back.");
			}
		}
	}

	// ---- deaths ----

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (nomad == null || event.getActor() != client.getLocalPlayer())
		{
			return;
		}
		Map<Integer, Long> before = new HashMap<>(lastInv);
		Items.add(before, lastWorn);
		Items.add(before, nomad.carried(Nomad.LOOTING_BAG));
		Items.add(before, nomad.carried(Nomad.RUNE_POUCH));
		beforeDeath = before;
		WorldPoint at = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation());
		deathAt = at;
		deathRegion = at == null ? -1 : at.getRegionID();
		deathInstanced = client.isInInstancedRegion();
		deathTick = client.getTickCount();
	}

	/** Once respawned, works out what the death did with everything. */
	private void judgeDeath()
	{
		Player me = client.getLocalPlayer();
		if (beforeDeath == null || me == null)
		{
			return;
		}
		boolean moved = deathAt == null || me.getWorldLocation().distanceTo(deathAt) > 15;
		boolean late = client.getTickCount() - deathTick > DEATH_TICKS;
		if (!moved && !late || client.getBoostedSkillLevel(Skill.HITPOINTS) <= 0)
		{
			return;
		}
		Map<Integer, Long> after = new HashMap<>(lastInv);
		Items.add(after, lastWorn);
		Death d = Death.of(beforeDeath, after, deathRegion, deathInstanced);
		long now = System.currentTimeMillis();
		Store bank = nomad.deathbank();
		if (d.kind == Death.Kind.PILE)
		{
			if (bank != null)
			{
				nomad.remove(Nomad.DEATHBANK);
				say("That death wiped your deathbank at " + bank.name + ".", true);
			}
			Store pile = nomad.pile(d.lost, deathAt.getX(), deathAt.getY(), deathAt.getPlane(), now);
			say("Deathpile left: " + Format.coins(Items.worth(pile.items, this::price)) + ", gone in an hour of play.");
		}
		else if (d.kind == Death.Kind.DEATHBANK)
		{
			if (bank != null && !bank.name.equals(d.place))
			{
				say("Your deathbank at " + bank.name + " was wiped by this death.", true);
			}
			Store s = new Store(Nomad.DEATHBANK, Kind.DEATHBANK, d.place);
			s.items = Items.clean(d.lost);
			s.exact = false;
			s.seen = now;
			s.since = now;
			nomad.put(s);
			say("Deathbank at " + d.place + ": " + Format.coins(Items.worth(s.items, this::price))
				+ ". Any unsafe death before you claim it wipes it.");
		}
		beforeDeath = null;
		deathTick = -1;
		changed();
	}

	/** The place a retrieval window belongs to, as best known: where the player is or last died. */
	private String place()
	{
		Player me = client.getLocalPlayer();
		if (me != null)
		{
			String lair = Death.lair(me.getWorldLocation().getRegionID());
			if (lair != null)
			{
				return lair;
			}
		}
		return deathAt == null ? "Unknown place" : "near your death";
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (nomad == null || event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String message = Text.removeTags(event.getMessage());
		guide.heard(message);
		if (nomad.following)
		{
			observeRoute();
		}
		if (message.startsWith("You have items stored in an item retrieval service."))
		{
			sawRetrieval = true;
			if (nomad.deathbank() == null)
			{
				Store s = new Store(Nomad.DEATHBANK, Kind.DEATHBANK, "Unknown place");
				s.exact = false;
				s.seen = System.currentTimeMillis();
				nomad.put(s);
				changed();
			}
		}
		else if (message.equals("You deposit your items into the STASH unit."))
		{
			stashDeposit = client.getTickCount();
		}
		else if (message.equals("You withdraw your items from the STASH unit."))
		{
			stashWithdraw = client.getTickCount();
		}
	}

	private void settleStash()
	{
		boolean deposit = stashDeposit >= 0 && client.getTickCount() > stashDeposit;
		boolean withdraw = stashWithdraw >= 0 && client.getTickCount() > stashWithdraw;
		if (!deposit && !withdraw)
		{
			return;
		}
		STASHUnit unit = nearestStash();
		if (unit != null)
		{
			String key = "stash:" + unit.name();
			Store s = nomad.store(key);
			long now = System.currentTimeMillis();
			if (withdraw)
			{
				if (s != null)
				{
					s.fill(new HashMap<>(), now);
				}
			}
			if (deposit)
			{
				if (s == null)
				{
					WorldPoint p = unit.getWorldPoints()[0];
					StashBook.Unit u = stash.unit(unit.name());
					s = new Store(key, Kind.STASH, u == null ? Text.titleCase(unit) : u.place).at(p.getX(), p.getY(), p.getPlane());
					nomad.put(s);
				}
				Map<Integer, Long> put = new HashMap<>(removedBefore);
				Items.add(put, removed);
				s.fill(put, now);
			}
			changed();
		}
		stashDeposit = -1;
		stashWithdraw = -1;
	}

	private STASHUnit nearestStash()
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return null;
		}
		WorldPoint at = me.getWorldLocation();
		STASHUnit best = null;
		int bestD = Integer.MAX_VALUE;
		for (STASHUnit u : STASHUnit.values())
		{
			for (WorldPoint p : u.getWorldPoints())
			{
				int d = p.getPlane() == at.getPlane() ? p.distanceTo2D(at) : Integer.MAX_VALUE;
				if (d < bestD)
				{
					bestD = d;
					best = u;
				}
			}
		}
		return bestD <= STASH_REACH ? best : null;
	}

	// ---- the tick ----

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (nomad == null)
		{
			return;
		}
		long nowMs = System.currentTimeMillis();
		if (lastTickMs > 0)
		{
			nomad.played += Math.min(5_000, Math.max(0, nowMs - lastTickMs));
		}
		lastTickMs = nowMs;
		settleStash();
		removedBefore.clear();
		removedBefore.putAll(removed);
		removed.clear();
		if (deathTick >= 0)
		{
			judgeDeath();
		}
		for (Store gone : nomad.tick())
		{
			say("A deathpile ran out of time: " + Format.coins(Items.worth(gone.items, this::price)) + " is gone.", true);
			changed();
		}
		warnPiles();
		checkLogin();
		observeRoute();
		guideRoute();
		ticks++;
		if (ticks % SAVE_TICKS == 0)
		{
			unsaved = true;
			save();
		}
		if (dirty || ticks % PUBLISH_TICKS == 0 && hasTimers())
		{
			publish();
		}
	}

	private boolean hasTimers()
	{
		return !nomad.stores(Kind.DEATHPILE).isEmpty();
	}

	private void warnPiles()
	{
		long warnTicks = config.pileWarning() * 100L;
		if (warnTicks <= 0)
		{
			return;
		}
		for (Store pile : nomad.stores(Kind.DEATHPILE))
		{
			if (pile.ticksLeft == warnTicks && warned.add(pile.key))
			{
				say("A deathpile has " + config.pileWarning() + " minutes of play left.", true);
			}
		}
	}

	/** When the game's deathbank warning is on and stays quiet at login, a remembered deathbank is gone. */
	private void checkLogin()
	{
		if (loginTick < 0 || client.getTickCount() - loginTick != LOGIN_TICKS)
		{
			return;
		}
		Store bank = nomad.deathbank();
		if (bank != null && !sawRetrieval && client.getVarbitValue(VarbitID.OPTION_ITEM_RETRIEVAL_WARNING_DISABLED) == 0)
		{
			nomad.remove(Nomad.DEATHBANK);
			say("The game reports no deathbank, so Nomad has let go of the one at " + bank.name + ".");
			changed();
		}
	}

	/** Ticks the route step in hand when everything it asks for holds now. */
	private void observeRoute()
	{
		List<Route.Step> done = guide.observe(nomad, here());
		for (Route.Step s : done)
		{
			nomad.tick(s.key(), true);
		}
		if (!done.isEmpty())
		{
			guide.stale();
			Route.Step next = guide.current(nomad);
			say(next == null ? "Route finished: welcome to Prifddinas." : "Route step done. Next: " + next.text);
			changed();
		}
	}

	/** Keeps the arrow, path, outlines and plaque row on the route step in hand, each tick. */
	private void guideRoute()
	{
		Route.Step s = client.getGameState() == GameState.LOGGED_IN ? guide.current(nomad) : null;
		String key = s == null ? null : s.key();
		if (key == null ? guidedKey != null : !key.equals(guidedKey))
		{
			guidedKey = key;
			changed();
		}
		if (s == null)
		{
			guide.stop();
		}
		else
		{
			guide.guide(s, config.guideArrow(), config.guideNpcs(), config.shortestPath());
		}
		List<Plaque.Row> rows = new ArrayList<>(plaqueRows);
		if (s != null && config.routeStep())
		{
			String way = RouteGuide.way(here(), guide.target(s));
			rows.add(new Plaque.Row("Route " + (s.index + 1) + " of " + route.size(), way == null ? "" : way, s.text, Theme.GOLD));
			String quest = Route.inQuest(s, guide.account());
			if (quest != null)
			{
				rows.add(new Plaque.Row("Inside the quest", "", "Open " + quest + " in Quest Helper for each click", Theme.TEAL));
			}
		}
		plaque.show("Nomad", plaqueTint, rows);
	}

	private Route.Account account()
	{
		return guide.account();
	}

	// ---- guards ----

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		MenuEntry entry = event.getMenuEntry();
		String option = Text.removeTags(entry.getOption());
		int item = entry.getItemId();
		if (item <= 0)
		{
			return;
		}
		if (config.guardLootingBag() && "Destroy".equals(option) && LOOTING_BAGS.contains(item)
			&& client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 0)
		{
			client.getMenu().removeMenuEntry(entry);
		}
		else if (config.guardSell() && option.startsWith("Sell") && worth(item) > config.sellAbove())
		{
			client.getMenu().removeMenuEntry(entry);
		}
	}

	/** An item's worth for the sell guard: the higher of its Grand Exchange and high alchemy values. */
	private long worth(int item)
	{
		int id = itemManager.canonicalize(item);
		ItemComposition c = itemManager.getItemComposition(id);
		return Math.max(itemManager.getItemPrice(id), c == null ? 0 : c.getHaPrice());
	}

	// ---- the panel and overlays ----

	private void changed()
	{
		dirty = true;
		unsaved = true;
	}

	private int[] here()
	{
		Player me = client.getLocalPlayer();
		if (me == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		WorldPoint p = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
		return p == null ? null : new int[]{p.getX(), p.getY(), p.getPlane()};
	}

	private int keep()
	{
		Player me = client.getLocalPlayer();
		if (me != null && me.getSkullIcon() >= 0)
		{
			return client.isPrayerActive(Prayer.PROTECT_ITEM) ? 1 : 0;
		}
		return client.isPrayerActive(Prayer.PROTECT_ITEM) ? 4 : 3;
	}

	private void publish()
	{
		dirty = false;
		NomadPanel target = panel;
		if (nomad == null)
		{
			plaqueRows = new ArrayList<>();
		plaque.show(null, null, null);
			marker.show(null);
			clearMap();
			if (target != null)
			{
				SwingUtilities.invokeLater(() -> target.show(new View()));
			}
			return;
		}
		int[] here = here();
		Views.Lookup look = lookup();
		View view = Views.build(nomad, look, stash, route, account(), here, keep(), config.idleHours() * 3_600_000L,
			System.currentTimeMillis());
		view.loggedIn = client.getGameState() == GameState.LOGGED_IN;
		showOverlays(view, here);
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.show(view));
		}
	}

	private void showOverlays(View v, int[] here)
	{
		List<Plaque.Row> rows = new ArrayList<>();
		boolean wild = client.getGameState() == GameState.LOGGED_IN && client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1;
		Color tint = Theme.GOLD;
		if (config.plaque())
		{
			for (View.Peril p : v.perils)
			{
				if (p.kind == Kind.DEATHPILE)
				{
					Color c = p.ticksLeft <= config.pileWarning() * 100L ? Theme.BLOOD : Theme.EMBER;
					rows.add(new Plaque.Row("Deathpile", Format.ticks(p.ticksLeft), Format.coins(p.worth) + " lying on the ground", c));
					tint = Theme.EMBER;
				}
				else if (p.kind == Kind.DEATHBANK)
				{
					rows.add(new Plaque.Row(p.title, p.exact ? Format.coins(p.worth) : "?",
						wild ? "You are in the Wilderness: dying here wipes it" : "Any unsafe death wipes it", wild ? Theme.BLOOD : Theme.EMBER));
					tint = wild ? Theme.BLOOD : Theme.EMBER;
				}
			}
		}
		List<Marker.Mark> marks = new ArrayList<>();
		List<WorldPoint> points = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		for (Store pile : nomad.stores(Kind.DEATHPILE))
		{
			WorldPoint p = new WorldPoint(pile.x, pile.y, pile.plane);
			marks.add(new Marker.Mark(p, "Deathpile " + Format.ticks(pile.ticksLeft), Theme.EMBER));
			points.add(p);
			labels.add("Deathpile");
		}
		if (v.fetch != null && !v.fetch.stops.isEmpty())
		{
			View.StopView next = v.fetch.stops.get(0);
			if (config.plaque())
			{
				rows.add(new Plaque.Row("Next: " + next.name, next.distance >= 0 ? next.distance + " tiles" : "",
					"Take " + takeText(next) + " for " + v.fetch.name, Theme.TEAL));
			}
			Trip t = nomad.activeTrip();
			Fetch f = t == null ? null : Views.plan(nomad, t, lookup(), here);
			if (config.pointer() && f != null && f.next() != null && f.next().store.located() && f.next().store.kind != Kind.DEATHPILE)
			{
				Store s = f.next().store;
				WorldPoint p = new WorldPoint(s.x, s.y, s.plane);
				marks.add(new Marker.Mark(p, "Nomad: " + s.name, Theme.TEAL));
				points.add(p);
				labels.add("Next stop: " + s.name);
			}
		}
		Route.Step step = guide.current(nomad);
		WorldPoint target = guide.target(step);
		if (target != null)
		{
			String label = step.text.length() > 40 ? step.text.substring(0, 38) + "..." : step.text;
			marks.add(new Marker.Mark(target, "Route: " + label, Theme.GOLD));
			points.add(target);
			labels.add("Route step " + (step.index + 1) + ": " + step.text);
		}
		plaqueRows = rows;
		plaqueTint = tint;
		plaque.show("Nomad", tint, rows);
		marker.show(config.pointer() ? marks : null);
		if (!config.pointer())
		{
			points.clear();
		}
		updateMap(points, labels);
	}

	private static String takeText(View.StopView stop)
	{
		List<String> parts = new ArrayList<>();
		for (View.ItemLine l : stop.take)
		{
			parts.add(l.qty > 1 ? l.qty + " " + l.name : l.name);
		}
		return parts.size() > 2 ? parts.get(0) + ", " + parts.get(1) + " and " + (parts.size() - 2) + " more" : String.join(", ", parts);
	}

	private final List<WorldPoint> shownPoints = new ArrayList<>();

	private void updateMap(List<WorldPoint> points, List<String> labels)
	{
		if (points.equals(shownPoints))
		{
			return;
		}
		clearMap();
		shownPoints.addAll(points);
		for (int i = 0; i < points.size(); i++)
		{
			WorldMapPoint p = new WorldMapPoint(points.get(i), Sigil.icon(20));
			p.setName(labels.get(i));
			p.setTooltip("Codex Nomad: " + labels.get(i));
			p.setSnapToEdge(true);
			p.setJumpOnClick(true);
			mapPoints.add(p);
			worldMapPointManager.add(p);
		}
	}

	private void clearMap()
	{
		for (WorldMapPoint p : mapPoints)
		{
			worldMapPointManager.remove(p);
		}
		mapPoints.clear();
		shownPoints.clear();
	}

	private void say(String text)
	{
		say(text, false);
	}

	private void say(String text, boolean alarm)
	{
		String message = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("Nomad: ")
			.append(alarm ? ChatColorType.HIGHLIGHT : ChatColorType.NORMAL)
			.append(text)
			.build();
		chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.CONSOLE).runeLiteFormattedMessage(message).build());
	}

	// ---- trips ----

	private void saveTrip(String name)
	{
		if (nomad == null || name == null || name.trim().isEmpty())
		{
			return;
		}
		Trip t = new Trip(name.trim(), System.currentTimeMillis());
		Map<Integer, Long> worn = Items.canon(lastWorn, itemManager::canonicalize);
		Map<Integer, Long> inv = Items.canon(lastInv, itemManager::canonicalize);
		Items.add(inv, Items.canon(nomad.carried(Nomad.RUNE_POUCH), itemManager::canonicalize));
		worn.forEach((id, q) -> t.wants.add(new Trip.Want(id, name(id), q, true)));
		inv.forEach((id, q) ->
		{
			if (!LOOTING_BAGS.contains(id) && id != ItemID.COINS)
			{
				t.wants.add(new Trip.Want(id, name(id), q, false));
			}
		});
		if (t.wants.isEmpty())
		{
			return;
		}
		nomad.save(t);
		nomad.active = t.name;
		unsaved = true;
		publish();
	}

	// ---- items ----

	private Map<Integer, Long> contents(ItemContainer container)
	{
		Map<Integer, Long> items = new LinkedHashMap<>();
		if (container == null)
		{
			return items;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() >= 0 && item.getQuantity() > 0)
			{
				items.merge(item.getId(), (long) item.getQuantity(), Long::sum);
			}
		}
		return items;
	}

	private String name(int id)
	{
		return names.computeIfAbsent(id, i ->
		{
			ItemComposition c = itemManager.getItemComposition(i);
			return c == null ? "Item " + i : c.getMembersName();
		});
	}

	private long price(int id)
	{
		if (id == ItemID.COINS)
		{
			return 1;
		}
		return itemManager.getItemPrice(itemManager.canonicalize(id));
	}

	private Views.Lookup lookup()
	{
		return new Views.Lookup()
		{
			@Override
			public String name(int id)
			{
				return CodexNomadPlugin.this.name(itemManager.canonicalize(id));
			}

			@Override
			public long price(int id)
			{
				return CodexNomadPlugin.this.price(id);
			}

			@Override
			public boolean stackable(int id)
			{
				ItemComposition c = itemManager.getItemComposition(id);
				return c != null && (c.isStackable() || c.getNote() != -1);
			}

			@Override
			public boolean noted(int id)
			{
				ItemComposition c = itemManager.getItemComposition(id);
				return c != null && c.getNote() != -1;
			}

			@Override
			public int canon(int id)
			{
				return itemManager.canonicalize(id);
			}
		};
	}

	// ---- saving ----

	private void loadProfile()
	{
		profile = configManager.getRSProfileKey();
		nomad = null;
		if (profile == null)
		{
			return;
		}
		String saved = configManager.getRSProfileConfiguration(CodexNomadConfig.GROUP, SAVE_KEY);
		Nomad.Loaded read = Nomad.read(gson, saved);
		if (read.damaged)
		{
			log.warn("The saved Nomad data was damaged; kept what still reads and set the save as it was aside");
			configManager.setConfiguration(CodexNomadConfig.GROUP, profile, DAMAGED_KEY, saved);
		}
		nomad = read.nomad;
		unsaved = false;
		dirty = true;
	}

	private boolean save()
	{
		if (profile == null || nomad == null || !unsaved)
		{
			return false;
		}
		unsaved = false;
		configManager.setConfiguration(CodexNomadConfig.GROUP, profile, SAVE_KEY, nomad.write(gson));
		String holdings = Holdings.write(gson, nomad.holdings(), System.currentTimeMillis());
		configManager.setConfiguration(CodexNomadConfig.GROUP, profile, HOLDINGS_KEY, holdings);
		if (config.shareHoldings() && nomad.account == 2)
		{
			String steward = configManager.getConfiguration(STEWARD_GROUP, profile, STEWARD_KEY);
			if (Holdings.ours(gson, steward))
			{
				configManager.setConfiguration(STEWARD_GROUP, profile, STEWARD_KEY, holdings);
			}
		}
		return true;
	}
}
