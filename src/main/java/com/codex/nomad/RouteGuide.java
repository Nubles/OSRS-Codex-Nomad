package com.codex.nomad;

import com.codex.nomad.core.Nomad;
import com.codex.nomad.core.Route;
import com.codex.nomad.core.Store;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * Reads the game for the route: what is true for good (quests, levels, unlocks) and what is true
 * this moment (items, place, coins, chat, furniture), ticks the step in hand when it holds, and
 * guides the player to it with the hint arrow, outlines on everything to click and the Shortest Path plugin.
 */
final class RouteGuide
{
	/** Ticks a game message counts as heard for a chat check. */
	private static final int HEARD_TICKS = 10;
	/** Ticks a reading of quests and levels is reused before it is taken again. */
	private static final int ACCOUNT_TICKS = 5;
	private static final String[][] TIERS = {
		{"easy", String.valueOf(VarbitID.CA_THRESHOLD_EASY)},
		{"medium", String.valueOf(VarbitID.CA_THRESHOLD_MEDIUM)},
		{"hard", String.valueOf(VarbitID.CA_THRESHOLD_HARD)},
		{"elite", String.valueOf(VarbitID.CA_THRESHOLD_ELITE)},
		{"master", String.valueOf(VarbitID.CA_THRESHOLD_MASTER)},
		{"grandmaster", String.valueOf(VarbitID.CA_THRESHOLD_GRANDMASTER)},
	};

	private final Client client;
	private final EventBus eventBus;
	private final Route route;
	private final IntFunction<String> name;
	private final Map<String, Quest> quests = new HashMap<>();
	private final Map<String, Skill> skills = new HashMap<>();
	private final Deque<Object[]> heard = new ArrayDeque<>();

	private Route.Account account;
	private int accountTick = Integer.MIN_VALUE;
	private WorldPoint pathed;
	private WorldPoint arrowPoint;
	private NPC arrowNpc;
	/** What to click for the step in hand, for the overlays. */
	private Route.Lead lead = new Route.Lead();
	/** Objects in the scene to click, found by the last scan. */
	private List<TileObject> objects = Collections.emptyList();
	/** Ground items to pick up, found by the last scan: the tile and the item's name. */
	private List<Object[]> ground = Collections.emptyList();

	RouteGuide(Client client, EventBus eventBus, Route route, IntFunction<String> name)
	{
		this.client = client;
		this.eventBus = eventBus;
		this.route = route;
		this.name = name;
		for (Quest q : Quest.values())
		{
			quests.put(q.getName(), q);
		}
		for (Skill s : Skill.values())
		{
			skills.put(s.getName(), s);
		}
	}

	/** Forget the last reading, so the next one is fresh. */
	void stale()
	{
		accountTick = Integer.MIN_VALUE;
	}

	void heard(String message)
	{
		heard.addLast(new Object[]{client.getTickCount(), message.toLowerCase(Locale.ENGLISH)});
		while (heard.size() > 40)
		{
			heard.removeFirst();
		}
	}

	Route.Account account()
	{
		int now = client.getTickCount();
		if (account != null && now - accountTick < ACCOUNT_TICKS && now >= accountTick)
		{
			return account;
		}
		accountTick = now;
		boolean in = client.getGameState() == GameState.LOGGED_IN;
		Map<String, QuestState> states = new HashMap<>();
		account = new Route.Account()
		{
			private QuestState state(String quest)
			{
				Quest q = quests.get(quest);
				if (!in || q == null)
				{
					return QuestState.NOT_STARTED;
				}
				return states.computeIfAbsent(quest, k -> q.getState(client));
			}

			@Override
			public boolean questDone(String quest)
			{
				return state(quest) == QuestState.FINISHED;
			}

			@Override
			public boolean questStarted(String quest)
			{
				return state(quest) != QuestState.NOT_STARTED;
			}

			@Override
			public int level(String skill)
			{
				Skill s = skills.get(skill);
				return in && s != null ? client.getRealSkillLevel(s) : 0;
			}

			@Override
			public int questPoints()
			{
				return in ? client.getVarpValue(VarPlayerID.QP) : 0;
			}

			@Override
			public int combat()
			{
				Player me = client.getLocalPlayer();
				return in && me != null ? me.getCombatLevel() : 0;
			}

			@Override
			public int varbit(int id)
			{
				return in ? client.getVarbitValue(id) : 0;
			}

			@Override
			public int varp(int id)
			{
				return in ? client.getVarpValue(id) : 0;
			}

			@Override
			public boolean tierDone(String tier)
			{
				for (String[] t : TIERS)
				{
					if (t[0].equals(tier))
					{
						int need = client.getVarbitValue(Integer.parseInt(t[1]));
						return in && need > 0 && client.getVarbitValue(VarbitID.CA_POINTS) >= need;
					}
				}
				return false;
			}
		};
		return account;
	}

	Route.Moment moment(Nomad n, int[] here)
	{
		Set<String> held = new HashSet<>();
		for (int id : n.carried().keySet())
		{
			held.add(name.apply(id).toLowerCase(Locale.ENGLISH));
		}
		int now = client.getTickCount();
		return new Route.Moment()
		{
			private Set<Integer> seen;
			private Set<String> owned;

			@Override
			public boolean held(String item)
			{
				return held.contains(item);
			}

			@Override
			public boolean owned(String item)
			{
				if (owned == null)
				{
					owned = new HashSet<>(held);
					for (Store s : n.stores())
					{
						for (int id : s.items.keySet())
						{
							owned.add(name.apply(id).toLowerCase(Locale.ENGLISH));
						}
					}
				}
				return owned.contains(item);
			}

			@Override
			public int[] here()
			{
				return here;
			}

			@Override
			public List<String> inventory()
			{
				List<String> out = new ArrayList<>();
				ItemContainer inv = client.getItemContainer(InventoryID.INV);
				if (inv != null)
				{
					for (Item i : inv.getItems())
					{
						if (i.getId() > 0 && i.getQuantity() > 0)
						{
							out.add(name.apply(i.getId()).toLowerCase(Locale.ENGLISH));
						}
					}
				}
				return out;
			}

			@Override
			public long coins()
			{
				ItemContainer inv = client.getItemContainer(InventoryID.INV);
				return inv == null ? 0 : inv.count(ItemID.COINS);
			}

			@Override
			public boolean heard(String text)
			{
				String t = text.toLowerCase(Locale.ENGLISH);
				for (Object[] h : heard)
				{
					if (now - (int) h[0] <= HEARD_TICKS && ((String) h[1]).contains(t))
					{
						return true;
					}
				}
				return false;
			}

			@Override
			public boolean deathbank()
			{
				return n.deathbank() != null;
			}

			@Override
			public boolean sees(int[] objects)
			{
				if (seen == null)
				{
					seen = sceneObjects();
				}
				for (int id : objects)
				{
					if (seen.contains(id))
					{
						return true;
					}
				}
				return false;
			}
		};
	}

	/** Every game object id in the scene on the player's plane. */
	private Set<Integer> sceneObjects()
	{
		Set<Integer> out = new HashSet<>();
		Scene scene = client.getTopLevelWorldView().getScene();
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		int plane = client.getTopLevelWorldView().getPlane();
		if (tiles == null || plane < 0 || plane >= tiles.length)
		{
			return out;
		}
		for (Tile[] row : tiles[plane])
		{
			for (Tile t : row)
			{
				if (t == null)
				{
					continue;
				}
				for (GameObject o : t.getGameObjects())
				{
					if (o != null)
					{
						out.add(o.getId());
					}
				}
				if (t.getWallObject() != null)
				{
					out.add(t.getWallObject().getId());
				}
				if (t.getDecorativeObject() != null)
				{
					out.add(t.getDecorativeObject().getId());
				}
				if (t.getGroundObject() != null)
				{
					out.add(t.getGroundObject().getId());
				}
			}
		}
		return out;
	}

	/** Steps in hand that hold now; the caller ticks them. */
	List<Route.Step> observe(Nomad n, int[] here)
	{
		if (n == null || !n.following || client.getGameState() != GameState.LOGGED_IN)
		{
			return Collections.emptyList();
		}
		return route.observe(n.ticked(), account(), moment(n, here));
	}

	/** The step in hand, or null when not following or finished. */
	Route.Step current(Nomad n)
	{
		return n == null || !n.following ? null : route.current(n.ticked(), account());
	}

	WorldPoint target(Route.Step s)
	{
		int[] t = s == null ? null : route.target(s, account());
		return t == null ? null : new WorldPoint(t[0], t[1], t[2]);
	}

	Route.Lead lead()
	{
		return lead;
	}

	List<TileObject> objects()
	{
		return objects;
	}

	List<Object[]> ground()
	{
		return ground;
	}

	/** True when this NPC is one the step in hand names. */
	boolean wanted(NPC npc)
	{
		return wanted(npc, lead);
	}

	private static boolean wanted(NPC npc, Route.Lead lead)
	{
		if (lead.npcIds.contains(npc.getId()))
		{
			return true;
		}
		String n = npc.getName();
		if (n == null)
		{
			return false;
		}
		for (String want : lead.npcs)
		{
			if (want.equalsIgnoreCase(n))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Points the way to the step in hand: the hint arrow at a named person in sight, else the thing
	 * to click, else the place; outlines everything to click; and asks Shortest Path for the way.
	 */
	void guide(Route.Step s, boolean arrow, boolean outline, boolean path)
	{
		WorldPoint target = target(s);
		Route.Lead l = route.lead(s, account());
		lead = outline ? l : new Route.Lead();
		scan(l);
		Player me = client.getLocalPlayer();
		NPC npc = null;
		int best = Integer.MAX_VALUE;
		if (me != null && (!l.npcs.isEmpty() || !l.npcIds.isEmpty()))
		{
			for (NPC c : client.getTopLevelWorldView().npcs())
			{
				if (wanted(c, l))
				{
					int d = c.getWorldLocation().distanceTo(me.getWorldLocation());
					if (d < best)
					{
						best = d;
						npc = c;
					}
				}
			}
		}
		WorldPoint point = near(target);
		if (npc == null && me != null)
		{
			best = Integer.MAX_VALUE;
			for (TileObject o : objects)
			{
				int d = o.getWorldLocation().distanceTo(me.getWorldLocation());
				if (d < best)
				{
					best = d;
					point = o.getWorldLocation();
				}
			}
		}
		if (!outline)
		{
			objects = Collections.emptyList();
			ground = Collections.emptyList();
		}
		pointArrow(arrow ? npc : null, arrow && npc == null ? point : null);
		pathTo(path ? target : null);
	}

	/** Points the way to a place off the route: a deathpile to save, or the next stop for a packing list. */
	void head(WorldPoint target, boolean arrow, boolean path)
	{
		lead = new Route.Lead();
		objects = Collections.emptyList();
		ground = Collections.emptyList();
		pointArrow(null, arrow ? near(target) : null);
		pathTo(path ? target : null);
	}

	/** Finds the objects and ground items the step asks for in the scene, on the player's plane. */
	private void scan(Route.Lead l)
	{
		if (l.objects.isEmpty() && l.ground.isEmpty())
		{
			objects = Collections.emptyList();
			ground = Collections.emptyList();
			return;
		}
		if (client.getTickCount() % 2 != 0 && (!objects.isEmpty() || !ground.isEmpty()))
		{
			return;
		}
		Set<Integer> ids = new HashSet<>(l.objects);
		Set<String> items = new HashSet<>();
		for (String g : l.ground)
		{
			items.add(g.toLowerCase(Locale.ENGLISH));
		}
		List<TileObject> found = new ArrayList<>();
		List<Object[]> lying = new ArrayList<>();
		Scene scene = client.getTopLevelWorldView().getScene();
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		int plane = client.getTopLevelWorldView().getPlane();
		if (tiles != null && plane >= 0 && plane < tiles.length)
		{
			for (Tile[] row : tiles[plane])
			{
				for (Tile t : row)
				{
					if (t == null)
					{
						continue;
					}
					if (!ids.isEmpty())
					{
						for (GameObject o : t.getGameObjects())
						{
							if (o != null && ids.contains(o.getId()) && !found.contains(o))
							{
								found.add(o);
							}
						}
						for (TileObject o : new TileObject[]{t.getWallObject(), t.getDecorativeObject(), t.getGroundObject()})
						{
							if (o != null && ids.contains(o.getId()))
							{
								found.add(o);
							}
						}
					}
					if (!items.isEmpty() && t.getGroundItems() != null)
					{
						for (TileItem i : t.getGroundItems())
						{
							String n = name.apply(i.getId()).toLowerCase(Locale.ENGLISH);
							if (items.contains(n))
							{
								lying.add(new Object[]{t, name.apply(i.getId())});
							}
						}
					}
				}
			}
		}
		objects = found;
		ground = lying;
	}

	/** The target, if it is close enough for the hint arrow to show. */
	private WorldPoint near(WorldPoint target)
	{
		Player me = client.getLocalPlayer();
		if (target == null || me == null)
		{
			return null;
		}
		WorldPoint at = me.getWorldLocation();
		if (at.getPlane() != target.getPlane() || at.distanceTo2D(target) > 60 || at.distanceTo2D(target) <= 1)
		{
			return null;
		}
		return target;
	}

	private void pointArrow(NPC npc, WorldPoint point)
	{
		boolean ours = arrowNpc != null && arrowNpc == client.getHintArrowNpc()
			|| arrowPoint != null && arrowPoint.equals(client.getHintArrowPoint());
		if (npc != null)
		{
			if (npc != arrowNpc || !ours)
			{
				if (ours || !client.hasHintArrow())
				{
					client.setHintArrow(npc);
					arrowNpc = npc;
					arrowPoint = null;
				}
			}
			return;
		}
		if (point != null)
		{
			if (!point.equals(arrowPoint) || !ours)
			{
				if (ours || !client.hasHintArrow())
				{
					client.setHintArrow(point);
					arrowPoint = point;
					arrowNpc = null;
				}
			}
			return;
		}
		if (ours)
		{
			client.clearHintArrow();
		}
		arrowNpc = null;
		arrowPoint = null;
	}

	private void pathTo(WorldPoint target)
	{
		if (target == null ? pathed == null : target.equals(pathed))
		{
			return;
		}
		if (target == null)
		{
			eventBus.post(new PluginMessage("shortestpath", "clear"));
		}
		else
		{
			Map<String, Object> data = new HashMap<>();
			data.put("target", target);
			eventBus.post(new PluginMessage("shortestpath", "path", data));
		}
		pathed = target;
	}

	/** Takes down the arrow and the path. */
	void stop()
	{
		pointArrow(null, null);
		pathTo(null);
		lead = new Route.Lead();
		objects = Collections.emptyList();
		ground = Collections.emptyList();
	}

	/** "212 tiles north-east", or null. */
	static String way(int[] here, WorldPoint target)
	{
		if (here == null || target == null)
		{
			return null;
		}
		int dx = target.getX() - here[0];
		int dy = target.getY() - here[1];
		int d = Math.max(Math.abs(dx), Math.abs(dy));
		if (d <= 2 && here[2] == target.getPlane())
		{
			return "Here";
		}
		if (d <= 2)
		{
			return target.getPlane() > here[2] ? "Upstairs" : "Downstairs";
		}
		String ns = dy > d / 2 ? "north" : dy < -d / 2 ? "south" : "";
		String ew = dx > d / 2 ? "east" : dx < -d / 2 ? "west" : "";
		String dir = ns.isEmpty() ? ew : ew.isEmpty() ? ns : ns + "-" + ew;
		return d + " tiles " + dir;
	}
}
