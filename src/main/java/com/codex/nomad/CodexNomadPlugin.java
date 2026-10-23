package com.codex.nomad;

import com.codex.nomad.core.Costume;
import com.codex.nomad.core.Death;
import com.codex.nomad.core.Fetch;
import com.codex.nomad.core.Items;
import com.codex.nomad.core.Kind;
import com.codex.nomad.core.LootingBag;
import com.codex.nomad.core.Nomad;
import com.codex.nomad.core.Route;
import com.codex.nomad.core.StashBook;
import com.codex.nomad.core.Store;
import com.codex.nomad.core.Trip;
import com.codex.nomad.core.WayBack;
import com.codex.nomad.overlay.ItemTips;
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
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.http.api.item.ItemPrice;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
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
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
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
	/** Ticks after login before the game's counters are read whole: by then the server has sent them. */
	private static final int READ_TICKS = 3;
	/** Ticks a Drop click waits for its item to land. */
	private static final int DROP_TICKS = 3;
	/** Tiles from the player within which a vanishing item was seen to go, not just left behind. */
	private static final int FLOOR_SIGHT = 15;
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
	private Notifier notifier;

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

	@Inject
	private TooltipManager tooltipManager;

	private ItemTips tips;

	private final Plaque plaque = new Plaque();
	private Marker marker;
	private Highlights highlights;
	private RaidFloor raidFloor;
	/** Chests in the scene that hold some deathbank. */
	private final List<TileObject> holders = new ArrayList<>();
	private Clicks clicks;
	private RouteGuide guide;
	/** The plaque as the last publish left it, before the route row is added each tick. */
	private List<Plaque.Row> plaqueRows = new ArrayList<>();
	private Color plaqueTint = Theme.GOLD;
	private String guidedKey;
	/** Where to send the player when no route step is in hand, or a deathpile needs saving first. */
	private WorldPoint errand;
	private boolean errandUrgent;
	private int lastRegion = -1;
	/** The words last read from the dialogue and message boxes, so each is read once. */
	private String lastShown = "";
	private final List<WorldMapPoint> mapPoints = new ArrayList<>();
	private final Map<Integer, String> names = new HashMap<>();
	private final Set<String> warned = new HashSet<>();
	/** Items taken out of the inventory and worn since the tick before last, for STASH deposits. */
	private final Map<Integer, Long> removed = new HashMap<>();
	private final Map<Integer, Long> removedBefore = new HashMap<>();
	/** Items that arrived in the inventory since the last tick. */
	private final Map<Integer, Long> gained = new HashMap<>();
	private HerbSack herbSack;
	/** Items the player clicked Drop on, by the tick of the click. */
	private final Map<Integer, Integer> dropping = new HashMap<>();

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
	/** The last tick the player took damage where a death would wipe the deathbank, and the last warning. */
	private int hurtTick = -1000;
	private int hurtWarned = -1000;
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
		herbSack = new HerbSack(id -> name(id));
		marker = new Marker(client);
		guide = new RouteGuide(client, eventBus, route, id -> name(itemManager.canonicalize(id)));
		raidFloor = new RaidFloor(client);
		highlights = new Highlights(client, outlines, guide, () -> raidLeft().isEmpty() || !config.guardRaidFloor()
			? java.util.Collections.emptyList() : raidFloor.exits(), this::raidLabel,
			() -> nomad == null || nomad.deathbank() == null || !config.pointer() ? null : nomad.deathbank().name, () -> holders);
		clicks = new Clicks(client, guide, id -> name(itemManager.canonicalize(id)));
		tips = new ItemTips(client, tooltipManager, itemManager::canonicalize, config::itemTips);
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
		overlayManager.add(tips);
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
		overlayManager.remove(tips);
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
		dropping.clear();
		gained.clear();
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
		if (com.codex.nomad.core.Kits.GROUP.equals(event.getGroup()) && com.codex.nomad.core.Kits.KEY.equals(event.getKey()))
		{
			clientThread.invokeLater(this::importKits);
		}
	}

	/** Brings Codex Quartermaster's kits in as packing lists, and lets go of ones it no longer has. */
	private void importKits()
	{
		if (nomad == null || profile == null)
		{
			return;
		}
		String json = configManager.getConfiguration(com.codex.nomad.core.Kits.GROUP, profile, com.codex.nomad.core.Kits.KEY);
		List<Trip> kits = com.codex.nomad.core.Kits.read(json, nomad.holdings(), this::name);
		boolean changedAny = false;
		for (String gone : com.codex.nomad.core.Kits.stale(nomad.trips(), kits))
		{
			nomad.forget(gone);
			changedAny = true;
		}
		for (Trip t : kits)
		{
			Trip had = nomad.trip(t.name);
			if (had == null || !sameWants(had, t))
			{
				t.made = System.currentTimeMillis();
				nomad.save(t);
				changedAny = true;
			}
		}
		if (changedAny)
		{
			changed();
		}
	}

	private static boolean sameWants(Trip a, Trip b)
	{
		if (a.wants.size() != b.wants.size())
		{
			return false;
		}
		for (int i = 0; i < a.wants.size(); i++)
		{
			Trip.Want x = a.wants.get(i);
			Trip.Want y = b.wants.get(i);
			if (x.item != y.item || x.qty != y.qty || x.worn != y.worn)
			{
				return false;
			}
		}
		return true;
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
		else if (state == GameState.LOADING)
		{
			raidFloor.sceneLoading();
			holders.clear();
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
				Items.add(gained, Items.minus(after, before));
			}
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
		else if (opened(id) != null)
		{
			nomad.carry(opened(id).name, contents(c), now);
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
		CompostBins.Bin bin = CompostBins.near(lastRegion);
		if (bin != null && v == bin.varbit)
		{
			readBin();
		}
		if (v == VarbitID.INSIDE_WILDERNESS && event.getValue() == 1 && config.wildWarning())
		{
			warnWilderness();
		}
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			for (Reads.Read r : Reads.touched(v, v >= 0 ? -1 : event.getVarpId()))
			{
				apply(r);
			}
		}
	}

	private static Reads.Opened opened(int container)
	{
		for (Reads.Opened o : Reads.OPENED)
		{
			if (o.container == container)
			{
				return o;
			}
		}
		return null;
	}

	private final Reads.Vars vars = new Reads.Vars()
	{
		@Override
		public int bit(int id)
		{
			return client.getVarbitValue(id);
		}

		@Override
		public int player(int id)
		{
			return client.getVarpValue(id);
		}
	};

	/** Reads one thing the game counts by itself, into the pack or its store. */
	private void apply(Reads.Read r)
	{
		long now = System.currentTimeMillis();
		Map<Integer, Long> items = r.read(vars);
		if (r.carried)
		{
			if (!items.isEmpty() || nomad.seen(r.name))
			{
				nomad.carry(r.name, items, now);
				changed();
			}
			return;
		}
		Store s = nomad.store(r.key);
		if (items.isEmpty())
		{
			if (s != null)
			{
				nomad.remove(r.key);
				changed();
			}
			return;
		}
		if (s == null)
		{
			s = new Store(r.key, r.kind, r.name);
			if (r.plane >= 0)
			{
				s.at(r.x, r.y, r.plane);
			}
			nomad.put(s);
		}
		if (!items.equals(s.items))
		{
			s.fill(items, now);
			changed();
		}
	}

	private void readAll()
	{
		importKits();
		for (Reads.Read r : Reads.ALL)
		{
			apply(r);
		}
	}

	/** The box shown on checking the fish barrel or log basket. */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.CHATMENU)
		{
			clientThread.invokeLater(() ->
			{
				Widget menu = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
				Widget title = menu == null ? null : menu.getChild(0);
				if (nomad != null && title != null && Bags.shilo(title.getText(), nomad, System.currentTimeMillis()))
				{
					changed();
				}
			});
			return;
		}
		if (event.getGroupId() != InterfaceID.OBJECTBOX)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			Widget w = client.getWidget(InterfaceID.Objectbox.TEXT);
			if (nomad != null && w != null && w.getText() != null
				&& Bags.checked(w.getText(), this::itemNamed, nomad, System.currentTimeMillis()))
			{
				changed();
			}
		});
	}

	/** A tradeable item by its exact name, or -1. */
	private int itemNamed(String name)
	{
		for (ItemPrice p : itemManager.search(name))
		{
			if (p.getName().equalsIgnoreCase(name))
			{
				return p.getId();
			}
		}
		return -1;
	}

	// ---- the floor ----

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = Text.removeTags(event.getMenuOption());
		if (nomad != null && "Drop".equals(option) && event.getItemId() > 0)
		{
			dropping.put(event.getItemId(), client.getTickCount());
		}
		else if (nomad != null && "Destroy".equals(option) && LOOTING_BAGS.contains(event.getItemId()))
		{
			List<String> lost = new ArrayList<>();
			for (int i : LootingBag.perishable(nomad.carried(Nomad.LOOTING_BAG), this::perishes))
			{
				lost.add(itemManager.getItemComposition(i).getName());
			}
			say(!bagDropsHere() ? "Destroying the looting bag here deletes everything in it."
				: lost.isEmpty() ? "Destroying the looting bag drops what is in it at your feet."
				: "Destroying the looting bag drops the rest at your feet, but loses " + String.join(", ", lost) + ".", true);
		}
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		TileItem item = event.getItem();
		Player me = client.getLocalPlayer();
		Integer clicked = dropping.get(item.getId());
		if (nomad == null || me == null || clicked == null || item.getOwnership() != TileItem.OWNERSHIP_SELF
			|| client.getTickCount() - clicked > DROP_TICKS)
		{
			return;
		}
		WorldPoint at = WorldPoint.fromLocalInstance(client, event.getTile().getLocalLocation());
		WorldPoint mine = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
		if (at == null || mine == null || at.getPlane() != mine.getPlane() || at.distanceTo2D(mine) > 1)
		{
			return;
		}
		long now = System.currentTimeMillis();
		boolean raid = raidFloor.inRaid();
		long until = raid ? Long.MAX_VALUE : now + Math.max(0, item.getDespawnTime() - client.getTickCount()) * 600L;
		nomad.drop(raid ? Nomad.RAID : client.getWorld(), at.getX(), at.getY(), at.getPlane(), item.getId(), item.getQuantity(), until, now);
		changed();
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		Player me = client.getLocalPlayer();
		if (nomad == null || me == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		WorldPoint at = WorldPoint.fromLocalInstance(client, event.getTile().getLocalLocation());
		WorldPoint mine = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
		if (at == null || mine == null || at.getPlane() != mine.getPlane() || at.distanceTo2D(mine) > FLOOR_SIGHT)
		{
			return;
		}
		TileItem item = event.getItem();
		int world = raidFloor.inRaid() ? Nomad.RAID : client.getWorld();
		if (nomad.store(Nomad.floorKey(world, at.getX(), at.getY(), at.getPlane())) != null)
		{
			nomad.lift(world, at.getX(), at.getY(), at.getPlane(), item.getId(), item.getQuantity());
			changed();
		}
	}

	/**
	 * The quickest way back to a deathpile with what an ultimate ironman has after a death: running,
	 * the home teleport and the minigame teleports this account has unlocked. Null when running is best.
	 */
	private WayBack.Way wayBack(Store pile)
	{
		int[] here = here();
		if (here == null || pile == null || !pile.located() || client.isInInstancedRegion())
		{
			return null;
		}
		long now = System.currentTimeMillis();
		int book = client.getVarbitValue(VarbitID.SPELLBOOK);
		WayBack.Spot home = book >= 0 && book < WayBack.HOMES.length ? WayBack.HOMES[book] : null;
		// Varps 892 and 888: the minute (since 1970) the home and minigame teleports were last cast.
		int homeWait = WayBack.wait(client.getVarpValue(892), 30, now);
		int gameWait = WayBack.wait(client.getVarpValue(888), 20, now);
		List<WayBack.Spot> games = new ArrayList<>(java.util.Arrays.asList(WayBack.CASTLE_WARS, WayBack.FEROX, WayBack.SOUL_WARS, WayBack.BURTHORPE));
		if (client.getRealSkillLevel(Skill.FISHING) >= 15)
		{
			games.add(WayBack.TRAWLER);
		}
		if (client.getLocalPlayer() != null && client.getLocalPlayer().getCombatLevel() >= 40)
		{
			games.add(WayBack.PEST_CONTROL);
		}
		if (client.getRealSkillLevel(Skill.FARMING) >= 34)
		{
			games.add(WayBack.TITHE);
		}
		if (Quest.SHADES_OF_MORTTON.getState(client) == QuestState.FINISHED)
		{
			games.add(WayBack.MORTTON);
		}
		if (client.getRealSkillLevel(Skill.COOKING) >= 40 && Quest.CABIN_FEVER.getState(client) == QuestState.FINISHED)
		{
			games.add(WayBack.BREWING);
		}
		List<WayBack.Way> ways = WayBack.plan(here, new int[]{pile.x, pile.y}, home, homeWait, games, gameWait);
		return ways.isEmpty() || ways.get(0).how.equals("Run there") ? null : ways.get(0);
	}

	/** Taking damage with a deathbank waiting, anywhere a death is not safe: warn, at most once a minute. */
	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Player me = client.getLocalPlayer();
		Store bank = nomad == null ? null : nomad.deathbank();
		if (bank == null || me == null || event.getActor() != me || event.getHitsplat().getAmount() <= 0 || !config.wildWarning())
		{
			return;
		}
		WorldPoint p = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
		if (p == null || Death.safe(p.getRegionID()))
		{
			return;
		}
		int tick = client.getTickCount();
		hurtTick = tick;
		dirty = true;
		if (tick - hurtWarned >= 100)
		{
			hurtWarned = tick;
			String text = "You are taking damage with your deathbank at " + bank.name
				+ (bank.exact ? " (" + Format.coins(Items.worth(bank.items, this::price)) + ")" : "") + " waiting: a death now wipes it.";
			say(text, true);
			notifier.notify(text);
		}
	}

	/** Entering a boss lair with a deathbank elsewhere: a death here would wipe it. */
	private void watchLair()
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return;
		}
		WorldPoint p = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
		int region = p == null ? -1 : p.getRegionID();
		if (region == lastRegion)
		{
			return;
		}
		lastRegion = region;
		readBin();
		String lair = Death.lair(region);
		Store bank = nomad.deathbank();
		if (lair != null && bank != null && !lair.equals(bank.name) && config.wildWarning())
		{
			say("A death at " + lair + " wipes your deathbank at " + bank.name
				+ (bank.exact ? " (" + Format.coins(Items.worth(bank.items, this::price)) + ")" : "") + ". Collect it first if you can.", true);
		}
	}

	/** Reads what a dialogue or message box says about a place that keeps things. */
	private void readShown(long now)
	{
		StringBuilder shown = new StringBuilder();
		for (int id : new int[]{InterfaceID.ChatLeft.TEXT, InterfaceID.Messagebox.TEXT, InterfaceID.Objectbox.TEXT})
		{
			Widget w = client.getWidget(id);
			if (w != null && !w.isHidden() && w.getText() != null)
			{
				shown.append(w.getText()).append('\n');
			}
		}
		String text = shown.toString();
		if (text.equals(lastShown))
		{
			return;
		}
		lastShown = text;
		boolean any = false;
		for (String part : text.split("\n"))
		{
			any |= Sayings.shown(part, gained, nomad, now);
		}
		if (any)
		{
			changed();
		}
	}

	/** The item in the weapon slot, or -1. */
	private int weapon()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item w = worn == null ? null : worn.getItem(net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx());
		return w == null ? -1 : w.getId();
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		raidFloor.despawned(event.getGameObject());
		holders.remove(event.getGameObject());
	}

	/** What lies on the Chambers of Xeric floor, all piles together. */
	private Map<Integer, Long> raidLeft()
	{
		Map<Integer, Long> left = new LinkedHashMap<>();
		if (nomad != null)
		{
			for (Store s : nomad.stores(Kind.FLOOR))
			{
				if (s.world == Nomad.RAID)
				{
					s.items.forEach((item, qty) -> left.merge(item, qty, Long::sum));
				}
			}
		}
		return left;
	}

	/** "You left 6 things (1.2M) on the floor" above the raid's ways out. */
	private String raidLabel()
	{
		Map<Integer, Long> left = raidLeft();
		int n = left.size();
		return "You left " + n + (n == 1 ? " thing" : " things") + " (" + Format.coins(Items.worth(left, this::price)) + ") on the floor";
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		raidFloor.spawned(event.getGameObject());
		if (Death.holderObject(event.getGameObject().getId()))
		{
			holders.add(event.getGameObject());
		}
		int[] capes = nomad == null ? null : CapeHanger.holds(event.getGameObject().getId());
		if (capes == null)
		{
			return;
		}
		Map<Integer, Long> items = new HashMap<>();
		for (int id : capes)
		{
			items.put(id, 1L);
		}
		Store s = nomad.store(CapeHanger.KEY);
		if (items.isEmpty())
		{
			if (s != null)
			{
				nomad.remove(CapeHanger.KEY);
				changed();
			}
			return;
		}
		if (s == null)
		{
			s = new Store(CapeHanger.KEY, Kind.KEPT, CapeHanger.NAME);
			nomad.put(s);
		}
		if (!items.equals(s.items))
		{
			s.fill(items, System.currentTimeMillis());
			changed();
		}
	}

	/** Reads the compost bin nearby, if the game is telling us about one here. */
	private void readBin()
	{
		CompostBins.Bin bin = CompostBins.near(lastRegion);
		if (bin == null || nomad == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		Map<Integer, Long> items = CompostBins.read(bin.big, client.getVarbitValue(bin.varbit));
		Store s = nomad.store(bin.key);
		if (items.isEmpty())
		{
			if (s != null)
			{
				nomad.remove(bin.key);
				changed();
			}
			return;
		}
		if (s == null)
		{
			s = new Store(bin.key, Kind.KEPT, bin.name);
			nomad.put(s);
		}
		if (!items.equals(s.items))
		{
			s.fill(items, now);
			changed();
		}
	}

	/** Says once, on the way in, what dying in the Wilderness would cost. */
	private void warnWilderness()
	{
		List<String> lose = new ArrayList<>();
		Store bank = nomad.deathbank();
		if (bank != null)
		{
			lose.add("your deathbank at " + bank.name + (bank.exact ? " (" + Format.coins(Items.worth(bank.items, this::price)) + ")" : ""));
		}
		com.codex.nomad.core.Kept k = com.codex.nomad.core.Kept.of(nomad.loose(), this::price, keep());
		long drop = k.droppedWorth + Items.worth(nomad.inside(), this::price);
		if (drop > 0)
		{
			lose.add(Format.coins(drop) + " of what you carry");
		}
		if (!lose.isEmpty())
		{
			say("Wilderness: dying here costs " + String.join(" and ", lose) + ".", true);
		}
	}

	/** Lets dropped items go when their time comes, and warns before. */
	private void watchFloor()
	{
		long now = System.currentTimeMillis();
		dropping.values().removeIf(t -> client.getTickCount() - t > DROP_TICKS);
		for (Map.Entry<Store, Map<Integer, Long>> e : nomad.expire(now).entrySet())
		{
			nomad.lose("Dropped on world " + e.getKey().world + " and vanished", e.getValue(), Items.worth(e.getValue(), this::price), now);
			say("Dropped on world " + e.getKey().world + " and now gone: " + itemsText(e.getValue()) + ".", true);
			changed();
		}
		long warnMs = config.floorWarning() * 1000L;
		if (warnMs <= 0)
		{
			return;
		}
		for (Store s : nomad.stores(Kind.FLOOR))
		{
			for (Map.Entry<Integer, Long> e : s.expires.entrySet())
			{
				long left = e.getValue() - now;
				if (left > 0 && left <= warnMs && warned.add(s.key + ":" + e.getKey() + ":" + e.getValue()))
				{
					say(name(itemManager.canonicalize(e.getKey())) + " on the floor vanishes in " + (left + 999) / 1000 + " seconds"
						+ (s.world != client.getWorld() ? ", on world " + s.world : "") + ".", true);
				}
			}
		}
	}

	private String itemsText(Map<Integer, Long> items)
	{
		List<String> parts = new ArrayList<>();
		items.forEach((id, q) -> parts.add(q > 1 ? q + " x " + name(itemManager.canonicalize(id)) : name(itemManager.canonicalize(id))));
		return parts.size() > 3 ? String.join(", ", parts.subList(0, 3)) + " and " + (parts.size() - 3) + " more" : String.join(", ", parts);
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
		Items.add(before, nomad.inside());
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
				nomad.lose("Deathbank at " + bank.name + " wiped by a death", bank.items, Items.worth(bank.items, this::price), now);
				say("That death wiped your deathbank at " + bank.name + ".", true);
			}
			Store pile = nomad.pile(d.lost, deathAt.getX(), deathAt.getY(), deathAt.getPlane(), now);
			WayBack.Way way = wayBack(pile);
			say("Deathpile left: " + Format.coins(Items.worth(pile.items, this::price)) + ", gone in an hour of play."
				+ (way != null ? " Quickest way back: " + way.say() + "." : ""));
		}
		else if (d.kind == Death.Kind.DEATHBANK)
		{
			if (bank != null && !bank.name.equals(d.place))
			{
				nomad.lose("Deathbank at " + bank.name + " wiped by a death", bank.items, Items.worth(bank.items, this::price), now);
				say("Your deathbank at " + bank.name + " was wiped by this death.", true);
			}
			Store s = new Store(Nomad.DEATHBANK, Kind.DEATHBANK, d.place);
			s.items = Items.clean(d.lost);
			s.exact = false;
			s.seen = now;
			s.since = now;
			nomad.put(s);
			say("Deathbank at " + d.place + ": " + Format.coins(Items.worth(s.items, this::price))
				+ ". " + (Death.claim(d.place) != null ? Death.claim(d.place) + ". " : "")
				+ "Any unsafe death before you claim it wipes it.");
		}
		if (d.kind == Death.Kind.PILE || d.kind == Death.Kind.DEATHBANK)
		{
			// Whatever was inside a bag, pouch or sack went with it.
			for (String c : nomad.containers())
			{
				if (!c.equals(Nomad.INVENTORY) && !c.equals(Nomad.WORN))
				{
					nomad.carry(c, new HashMap<>(), now);
				}
			}
			readPouch();
			readAll();
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
		if (herbSack.heard(message, nomad, System.currentTimeMillis()) | Bags.heard(message, nomad, System.currentTimeMillis())
			| Sayings.heard(message, weapon(), this::itemNamed, nomad, System.currentTimeMillis()))
		{
			changed();
		}
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
		if (herbSack.tick(removed, gained, nomad, nowMs))
		{
			changed();
		}
		readShown(nowMs);
		gained.clear();
		removedBefore.clear();
		removedBefore.putAll(removed);
		removed.clear();
		if (deathTick >= 0)
		{
			judgeDeath();
		}
		for (Store gone : nomad.tick())
		{
			nomad.lose("Deathpile ran out of time", gone.items, Items.worth(gone.items, this::price), nowMs);
			say("A deathpile ran out of time: " + Format.coins(Items.worth(gone.items, this::price)) + " is gone.", true);
			changed();
		}
		warnPiles();
		watchFloor();
		if (client.getTickCount() - hurtTick == 9)
		{
			dirty = true;
		}
		if (raidFloor.tick())
		{
			Map<Integer, Long> lost = nomad.endRaid();
			if (!lost.isEmpty())
			{
				nomad.lose("Left on the Chambers of Xeric floor", lost, Items.worth(lost, this::price), nowMs);
				say("You left the Chambers of Xeric with things on its floor; they are gone: " + itemsText(lost) + ".", true);
				changed();
			}
		}
		watchLair();
		checkLogin();
		if (loginTick >= 0 && client.getTickCount() - loginTick == READ_TICKS)
		{
			readAll();
		}
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
		return !nomad.stores(Kind.DEATHPILE).isEmpty() || !nomad.stores(Kind.FLOOR).isEmpty();
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
		if (errand != null && (errandUrgent || s == null))
		{
			guide.head(errand, config.guideArrow(), config.shortestPath());
		}
		else if (s == null)
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
		if (config.guardRaidFloor() && RaidFloor.objectAction(entry.getType()) && RaidFloor.exit(entry.getIdentifier())
			&& raidFloor.inRaid() && !raidLeft().isEmpty())
		{
			entry.setDeprioritized(true);
			return;
		}
		if (item <= 0)
		{
			return;
		}
		if (config.guardLootingBag() && "Destroy".equals(option) && LOOTING_BAGS.contains(item) && !bagDropsHere())
		{
			client.getMenu().removeMenuEntry(entry);
		}
		else if (config.guardSell() && option.startsWith("Sell") && worth(item) > config.sellAbove())
		{
			client.getMenu().removeMenuEntry(entry);
		}
	}

	/** Whether destroying the looting bag where the player stands drops its contents, not deletes them. */
	private boolean bagDropsHere()
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return false;
		}
		WorldPoint p = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
		return p != null && LootingBag.dropsHere(client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1, p.getX(), p.getY(), p.getPlane());
	}

	/** Unnoted food, potions and vials of water, which destroying a looting bag loses. */
	private boolean perishes(int item)
	{
		ItemComposition c = itemManager.getItemComposition(item);
		if (c == null || c.getNote() != -1)
		{
			return false;
		}
		if ("Vial of water".equals(c.getName()))
		{
			return true;
		}
		for (String a : c.getInventoryActions())
		{
			if ("Eat".equals(a) || "Drink".equals(a))
			{
				return true;
			}
		}
		return false;
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
		// An ultimate ironman keeps nothing on an unsafe death, Protect Item included (OSRS Wiki).
		if (nomad != null && nomad.account == 2)
		{
			return 0;
		}
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
			tips.show(null);
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
		for (View.Peril p : view.perils)
		{
			Store pile = p.kind == Kind.DEATHPILE ? nomad.store(p.key) : null;
			WayBack.Way way = pile == null ? null : wayBack(pile);
			if (way != null && way.wait == 0)
			{
				p.note = p.note + " · " + way.say();
				p.way = way.say();
			}
		}
		showOverlays(view, here);
		tips.show(whereKept(view));
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
					rows.add(new Plaque.Row("Deathpile", Format.ticks(p.ticksLeft),
						p.note != null && p.note.startsWith("Needs ") ? p.note.split(" · ")[0] : Format.coins(p.worth) + " lying on the ground", c));
					if (p.way != null)
					{
						rows.add(new Plaque.Row("Way back", "", p.way, Theme.GOLD));
					}
					tint = Theme.EMBER;
				}
				else if (p.kind == Kind.DEATHBANK)
				{
					Store bank = nomad.deathbank();
					String brief = bank == null ? null : Death.brief(bank.name);
					boolean hurt = client.getTickCount() - hurtTick <= 8;
					rows.add(new Plaque.Row(p.title, p.exact ? Format.coins(p.worth) : "?",
						hurt ? "You are taking damage: a death now wipes it" : wild ? "You are in the Wilderness: dying here wipes it"
							: brief != null ? "Reclaim: " + brief : "Any unsafe death wipes it",
						wild || hurt ? Theme.BLOOD : Theme.EMBER));
					tint = wild || hurt ? Theme.BLOOD : Theme.EMBER;
				}
			}
		}
		List<Marker.Mark> marks = new ArrayList<>();
		List<WorldPoint> points = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		errand = null;
		errandUrgent = false;
		long nowMs = System.currentTimeMillis();
		Store soonest = null;
		for (Store floor : nomad.stores(Kind.FLOOR))
		{
			if (floor.world == client.getWorld())
			{
				long left = floor.until - nowMs;
				marks.add(new Marker.Mark(new WorldPoint(floor.x, floor.y, floor.plane), "Dropped " + Format.clock(left),
					left <= config.floorWarning() * 1000L ? Theme.BLOOD : Theme.EMBER));
			}
			if (soonest == null || floor.until < soonest.until)
			{
				soonest = floor;
			}
		}
		if (soonest != null && config.plaque())
		{
			long left = soonest.until - nowMs;
			rows.add(0, new Plaque.Row(soonest.world == Nomad.RAID ? "On the raid floor" : "On the floor", Format.clock(left), itemsText(soonest.items)
				+ (soonest.world != client.getWorld() && soonest.world != Nomad.RAID ? " · world " + soonest.world : ""), left <= config.floorWarning() * 1000L ? Theme.BLOOD : Theme.EMBER));
			tint = Theme.EMBER;
		}
		Store urgent = null;
		for (Store pile : nomad.stores(Kind.DEATHPILE))
		{
			WorldPoint p = new WorldPoint(pile.x, pile.y, pile.plane);
			marks.add(new Marker.Mark(p, "Deathpile " + Format.ticks(pile.ticksLeft), Theme.EMBER));
			points.add(p);
			labels.add("Deathpile");
			if (urgent == null || pile.ticksLeft < urgent.ticksLeft)
			{
				urgent = pile;
			}
		}
		if (urgent != null && config.pointer())
		{
			errand = new WorldPoint(urgent.x, urgent.y, urgent.plane);
			errandUrgent = true;
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
				if (errand == null)
				{
					errand = p;
				}
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

	/** Every place each item is kept, by its common id, for the inventory tooltip. */
	private Map<Integer, List<String>> whereKept(View v)
	{
		Map<Integer, List<String>> where = new HashMap<>();
		for (View.StoreView s : v.stores)
		{
			String place = s.kind == Kind.FLOOR && s.world == Nomad.RAID ? "On the Chambers of Xeric floor"
				: s.kind == Kind.FLOOR ? "On the floor, world " + s.world
				: s.kind == Kind.DEATHPILE ? "Deathpile, " + Format.ticks(s.ticksLeft) + " left"
				: s.kind == Kind.STASH ? "STASH, " + s.name
				: s.kind == Kind.DEATHBANK ? "Deathbank, " + s.name : s.name;
			for (View.ItemLine l : s.items)
			{
				where.computeIfAbsent(itemManager.canonicalize(l.id), k -> new ArrayList<>())
					.add(l.qty > 1 ? place + ": " + Format.whole(l.qty) : place);
			}
		}
		return where;
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
