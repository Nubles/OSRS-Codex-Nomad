package com.codex.nomad;

import com.codex.nomad.core.Death;
import com.codex.nomad.core.Fetch;
import com.codex.nomad.core.Idle;
import com.codex.nomad.core.Items;
import com.codex.nomad.core.Kept;
import com.codex.nomad.core.Kind;
import com.codex.nomad.core.Nomad;
import com.codex.nomad.core.Notary;
import com.codex.nomad.core.Route;
import com.codex.nomad.core.StashBook;
import com.codex.nomad.core.Store;
import com.codex.nomad.core.Trip;
import com.codex.nomad.ui.Format;
import com.codex.nomad.ui.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the panel's {@link View} from the account's data, on the client thread. */
final class Views
{
	/** What the game knows about items, asked through the plugin. */
	interface Lookup
	{
		String name(int id);

		long price(int id);

		boolean stackable(int id);

		boolean noted(int id);

		/** The item's common form: unnoted, no placeholder. */
		int canon(int id);
	}

	/** Items never called dead weight: the containers themselves. */
	static final java.util.Set<Integer> CONTAINERS = new java.util.HashSet<>(java.util.Arrays.asList(
		net.runelite.api.gameval.ItemID.LOOTING_BAG, net.runelite.api.gameval.ItemID.LOOTING_BAG_OPEN,
		net.runelite.api.gameval.ItemID.BH_RUNE_POUCH, net.runelite.api.gameval.ItemID.BH_RUNE_POUCH_TROUVER,
		net.runelite.api.gameval.ItemID.DIVINE_RUNE_POUCH, net.runelite.api.gameval.ItemID.DIVINE_RUNE_POUCH_TROUVER,
		net.runelite.api.gameval.ItemID.COINS, net.runelite.api.gameval.ItemID.SLAYER_HERB_SACK,
		net.runelite.api.gameval.ItemID.SLAYER_HERB_SACK_OPEN, net.runelite.api.gameval.ItemID.SEED_BOX,
		net.runelite.api.gameval.ItemID.SEED_BOX_OPEN, net.runelite.api.gameval.ItemID.TACKLE_BOX,
		net.runelite.api.gameval.ItemID.HUNTSMANS_KIT, net.runelite.api.gameval.ItemID.FORESTRY_KIT,
		net.runelite.api.gameval.ItemID.PLANK_SACK, net.runelite.api.gameval.ItemID.XBOWS_BOLT_POUCH,
		net.runelite.api.gameval.ItemID.BOOKOFSCROLLS_CHARGED, net.runelite.api.gameval.ItemID.BOOKOFSCROLLS_EMPTY,
		net.runelite.api.gameval.ItemID.BOWSTRING_SPOOL, net.runelite.api.gameval.ItemID.MM_PREPOT_DEVICE,
		net.runelite.api.gameval.ItemID.RCU_POUCH_SMALL, net.runelite.api.gameval.ItemID.RCU_POUCH_MEDIUM,
		net.runelite.api.gameval.ItemID.RCU_POUCH_LARGE, net.runelite.api.gameval.ItemID.RCU_POUCH_GIANT,
		net.runelite.api.gameval.ItemID.RCU_POUCH_COLOSSAL, net.runelite.api.gameval.ItemID.COAL_BAG,
		net.runelite.api.gameval.ItemID.COAL_BAG_OPEN, net.runelite.api.gameval.ItemID.GEM_BAG,
		net.runelite.api.gameval.ItemID.GEM_BAG_OPEN, net.runelite.api.gameval.ItemID.FISH_BARREL_CLOSED,
		net.runelite.api.gameval.ItemID.FISH_BARREL_OPEN, net.runelite.api.gameval.ItemID.LOG_BASKET_CLOSED,
		net.runelite.api.gameval.ItemID.LOG_BASKET_OPEN,
		net.runelite.api.gameval.ItemID.BOTTOMLESS_COMPOST_BUCKET, net.runelite.api.gameval.ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED));

	/** The bags read from what the game says, with the items that are the bag itself. */
	private static final Map<String, int[]> HEARD = new LinkedHashMap<>();

	static
	{
		HEARD.put(HerbSack.NAME, new int[]{net.runelite.api.gameval.ItemID.SLAYER_HERB_SACK, net.runelite.api.gameval.ItemID.SLAYER_HERB_SACK_OPEN});
		HEARD.put(Bags.COAL, new int[]{net.runelite.api.gameval.ItemID.COAL_BAG, net.runelite.api.gameval.ItemID.COAL_BAG_OPEN});
		HEARD.put(Bags.GEMS, new int[]{net.runelite.api.gameval.ItemID.GEM_BAG, net.runelite.api.gameval.ItemID.GEM_BAG_OPEN});
		HEARD.put(Bags.FISH, new int[]{net.runelite.api.gameval.ItemID.FISH_BARREL_CLOSED, net.runelite.api.gameval.ItemID.FISH_BARREL_OPEN});
		HEARD.put(Bags.BUCKET, new int[]{net.runelite.api.gameval.ItemID.BOTTOMLESS_COMPOST_BUCKET, net.runelite.api.gameval.ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED});
		HEARD.put(Bags.LOGS, new int[]{net.runelite.api.gameval.ItemID.LOG_BASKET_CLOSED, net.runelite.api.gameval.ItemID.LOG_BASKET_OPEN});
	}

	private Views()
	{
	}

	private static boolean carries(Nomad n, int[] ids)
	{
		for (int id : ids)
		{
			if (n.carried(Nomad.INVENTORY).getOrDefault(id, 0L) > 0)
			{
				return true;
			}
		}
		return false;
	}

	static View build(Nomad n, Lookup look, StashBook stash, Route route, Route.Account account, int[] here, int keep,
		long idleAfter, long now)
	{
		View v = new View();
		v.now = now;
		v.account = n.account;

		// ---- pack ----
		Map<Integer, Long> inv = n.carried(Nomad.INVENTORY);
		v.inventory = lines(inv, look);
		v.worn = lines(n.carried(Nomad.WORN), look);
		v.pouch = lines(n.carried(Nomad.RUNE_POUCH), look);
		List<View.Vessel> vessels = new ArrayList<>();
		for (String name : n.containers())
		{
			if (!name.equals(Nomad.INVENTORY) && !name.equals(Nomad.WORN) && !name.equals(Nomad.LOOTING_BAG)
				&& !name.equals(Nomad.RUNE_POUCH) && !n.carried(name).isEmpty())
			{
				View.Vessel vessel = new View.Vessel();
				vessel.name = name;
				vessel.items = lines(n.carried(name), look);
				vessel.seen = n.seenAt(name);
				vessels.add(vessel);
			}
		}
		// A bag the game only describes in words stays unknown until it is first checked.
		for (Map.Entry<String, int[]> bag : HEARD.entrySet())
		{
			if (!n.seen(bag.getKey()) && carries(n, bag.getValue()))
			{
				View.Vessel vessel = new View.Vessel();
				vessel.name = bag.getKey();
				vessel.unchecked = true;
				vessels.add(vessel);
			}
		}
		vessels.sort((a, b) -> a.name.compareTo(b.name));
		v.vessels = vessels;
		v.bagKnown = n.seen(Nomad.LOOTING_BAG);
		v.bag = lines(n.carried(Nomad.LOOTING_BAG), look);
		v.bagUsed = n.carried(Nomad.LOOTING_BAG).size();
		v.bagSeen = n.seenAt(Nomad.LOOTING_BAG);
		v.invUsed = slots(inv, look);
		Map<Integer, Long> carried = n.carried();
		v.carriedWorth = Items.worth(carried, look::price);
		v.keepCount = keep;
		Kept k = Kept.of(n.loose(), look::price, keep);
		v.dropNow = k.droppedWorth + Items.worth(n.inside(), look::price);
		v.keepNow = k.keptWorth;
		v.kept = lines(k.kept, look);

		// ---- perils ----
		List<View.Peril> perils = new ArrayList<>();
		for (Store s : n.stores(Kind.FLOOR))
		{
			View.Peril p = new View.Peril();
			p.kind = Kind.FLOOR;
			p.key = s.key;
			p.title = s.world == Nomad.RAID ? "Chambers of Xeric floor" : "On the floor, world " + s.world;
			p.note = roomNote(s.world == Nomad.RAID ? "Lost when you leave the raid" : "Gone when its timer runs out", room(s.items, inv, look), 28 - v.invUsed);
			p.note = shedNote(p.note, s.items, inv, look, room(s.items, inv, look) - (28 - v.invUsed));
			p.worth = Items.worth(s.items, look::price);
			p.until = s.until;
			p.count = s.items.size();
			perils.add(p);
		}
		perils.sort((a, b) -> Long.compare(a.until, b.until));
		for (Store s : n.stores(Kind.DEATHPILE))
		{
			View.Peril p = new View.Peril();
			p.kind = Kind.DEATHPILE;
			p.key = s.key;
			p.title = "Deathpile";
			p.note = roomNote("Lost when its timer runs out", room(s.items, inv, look), 28 - v.invUsed);
			p.note = shedNote(p.note, s.items, inv, look, room(s.items, inv, look) - (28 - v.invUsed));
			p.worth = Items.worth(s.items, look::price);
			p.ticksLeft = s.ticksLeft;
			p.count = s.items.size();
			perils.add(p);
		}
		Store bank = n.deathbank();
		if (bank != null)
		{
			View.Peril p = new View.Peril();
			p.kind = Kind.DEATHBANK;
			p.key = bank.key;
			p.title = "Deathbank: " + bank.name;
			String claim = Death.claim(bank.name);
			long fee = Death.fee(bank.name);
			long coins = n.carried(Nomad.INVENTORY).getOrDefault(net.runelite.api.gameval.ItemID.COINS, 0L);
			p.note = claim == null ? "Any unsafe death wipes it"
				: fee > coins ? claim + "; you carry " + Format.coins(coins) : claim + " · any unsafe death wipes it";
			p.worth = Items.worth(bank.items, look::price);
			p.exact = bank.exact;
			p.count = bank.items.size();
			perils.add(p);
		}
		long bagWorth = Items.worth(n.carried(Nomad.LOOTING_BAG), look::price);
		if (bagWorth > 0)
		{
			View.Peril p = new View.Peril();
			p.kind = Kind.OTHER;
			p.key = "bag";
			p.title = "Looting bag";
			p.note = "Empties only on death, or destroyed deep enough in the Wilderness";
			p.worth = bagWorth;
			p.count = v.bagUsed;
			perils.add(p);
		}
		v.perils = perils;

		// ---- dead weight ----
		Map<Integer, Long> idleSet = new LinkedHashMap<>(inv);
		v.idleAfter = idleAfter;
		List<View.IdleLine> idle = new ArrayList<>();
		for (Idle i : Idle.find(n, idleSet, look::stackable, CONTAINERS::contains, idleAfter))
		{
			View.IdleLine line = new View.IdleLine();
			line.item = line(i.item, i.qty, look);
			line.idleMs = i.idleMs;
			line.slots = i.slots;
			List<StashBook.Home> homes = stash.homes(look.canon(i.item));
			line.home = homes.isEmpty() ? null : homes.get(0).place;
			idle.add(line);
		}
		v.idle = idle;
		v.idleSlots = 0;
		for (View.IdleLine i : idle)
		{
			v.idleSlots += i.slots;
		}

		// ---- noting ----
		List<View.NoteLine> notes = new ArrayList<>();
		for (Map.Entry<Integer, Long> e : inv.entrySet())
		{
			int id = e.getKey();
			boolean noted = look.noted(id);
			int plain = look.canon(id);
			long count = e.getValue();
			if (!noted && !look.stackable(id))
			{
				count = inv.getOrDefault(id, 0L);
			}
			Notary.Advice a = Notary.advise(plain, look.name(plain), count, noted, here);
			if (a == null)
			{
				continue;
			}
			Notary.Desk d = a.best();
			View.NoteLine line = new View.NoteLine();
			line.item = line(id, count, look);
			line.item.noted = noted;
			line.unnote = noted;
			line.who = d.who;
			line.where = d.where;
			line.fee = d.fee;
			line.wilderness = d.wilderness;
			line.frees = look.stackable(id) ? 0 : a.frees;
			line.distance = d.distance(here);
			if (noted || line.frees > 0)
			{
				notes.add(line);
			}
		}
		notes.sort((a, b) -> Integer.compare(b.frees, a.frees));
		v.notes = notes;

		// ---- stores ----
		List<View.StoreView> stores = new ArrayList<>();
		long kept = 0;
		for (Store s : n.stores())
		{
			View.StoreView sv = new View.StoreView();
			sv.key = s.key;
			sv.kind = s.kind;
			sv.name = s.name;
			sv.items = lines(s.items, look);
			sv.worth = Items.worth(s.items, look::price);
			sv.seen = s.seen;
			sv.exact = s.exact;
			sv.live = s.kind.automatic() && Reads.live(s.key);
			sv.ticksLeft = s.ticksLeft;
			sv.until = s.until;
			sv.world = s.world;
			if (here != null && s.located() && s.plane == here[2])
			{
				sv.distance = Math.max(Math.abs(s.x - here[0]), Math.abs(s.y - here[1]));
			}
			kept += sv.worth;
			stores.add(sv);
		}
		stores.sort((a, b) -> a.kind != b.kind ? Integer.compare(a.kind.ordinal(), b.kind.ordinal()) : Long.compare(b.worth, a.worth));
		v.stores = stores;
		v.keptWorth = kept;

		// ---- trips ----
		List<String> names = new ArrayList<>();
		for (Trip t : n.trips())
		{
			names.add(t.name);
		}
		v.trips = names;
		Trip active = n.activeTrip();
		if (active != null)
		{
			v.active = active.name;
			v.fetch = fetch(n, active, look, here);
		}

		// ---- route ----
		v.route = route(n, route, account);
		return v;
	}

	static Fetch plan(Nomad n, Trip trip, Lookup look, int[] here)
	{
		Map<Integer, Long> pocket = Items.canon(n.carried(Nomad.INVENTORY), look::canon);
		Items.add(pocket, Items.canon(n.carried(Nomad.WORN), look::canon));
		Map<Integer, Long> pouch = Items.canon(n.carried(Nomad.RUNE_POUCH), look::canon);
		Items.add(pocket, pouch);
		Map<Integer, Long> bag = Items.canon(n.carried(Nomad.LOOTING_BAG), look::canon);
		return Fetch.plan(trip, pocket, bag, n.stores(), look::canon, look::stackable, here,
			id -> CONTAINERS.contains(id) || pouch.containsKey(id), look::name);
	}

	private static View.FetchView fetch(Nomad n, Trip trip, Lookup look, int[] here)
	{
		Fetch f = plan(n, trip, look, here);
		View.FetchView fv = new View.FetchView();
		fv.name = trip.name;
		fv.ready = f.ready;
		fv.total = f.lines.size();
		fv.missing = f.missing;
		fv.slots = f.slots;
		List<View.FetchLine> lines = new ArrayList<>();
		for (Fetch.Line l : f.lines)
		{
			View.FetchLine fl = new View.FetchLine();
			fl.want = line(l.want.item, l.want.qty, look);
			fl.have = l.have;
			fl.missing = l.missing;
			fl.bagged = l.bagged;
			fl.worn = l.want.worn;
			List<String> where = new ArrayList<>();
			for (Map.Entry<String, Long> e : l.from.entrySet())
			{
				Store s = n.store(e.getKey());
				where.add((s == null ? "?" : s.name) + (l.from.size() > 1 ? " " + e.getValue() : ""));
			}
			fl.from = where.isEmpty() ? null : String.join(", ", where);
			lines.add(fl);
		}
		fv.lines = lines;
		List<View.StopView> stops = new ArrayList<>();
		for (Fetch.Stop s : f.stops)
		{
			View.StopView sv = new View.StopView();
			sv.name = s.store.name;
			sv.kind = s.store.kind;
			sv.distance = s.distance;
			sv.take = lines(s.take, look);
			stops.add(sv);
		}
		fv.stops = stops;
		fv.extra = lines(f.extra, look);
		return fv;
	}

	private static View.RouteView route(Nomad n, Route route, Route.Account account)
	{
		View.RouteView rv = new View.RouteView();
		rv.following = n.following;
		rv.total = route.size();
		List<View.StepView> steps = new ArrayList<>();
		Route.Step current = route.current(n.ticked(), account);
		rv.current = current == null ? -1 : current.index;
		for (Route.Step s : route.steps())
		{
			View.StepView sv = new View.StepView();
			sv.index = s.index;
			sv.key = s.key();
			sv.section = s.section;
			sv.text = s.text;
			sv.tips = s.tips;
			sv.met = Route.met(s, account);
			sv.done = route.done(s, n.ticked(), account);
			sv.passed = sv.done && !sv.met && !n.ticked().contains(s.key());
			sv.optional = s.optional;
			if (sv.done)
			{
				rv.done++;
			}
			sv.check = check(s);
			if (current != null && s.index == current.index)
			{
				sv.clicks = clicks(route.lead(s, account));
			}
			steps.add(sv);
		}
		rv.steps = steps;
		rv.sections = route.sections();
		List<int[]> progress = new ArrayList<>();
		for (String sec : route.sections())
		{
			progress.add(route.section(sec, n.ticked(), account));
		}
		rv.progress = progress;
		return rv;
	}

	/** What the in-game highlights point at, in words. */
	static String clicks(Route.Lead l)
	{
		List<String> parts = new ArrayList<>();
		if (!l.npcs.isEmpty())
		{
			parts.add("Talk to " + String.join(" or ", l.npcs));
		}
		else if (!l.npcIds.isEmpty())
		{
			parts.add("Talk to the outlined person");
		}
		if (!l.objects.isEmpty())
		{
			parts.add("Click the outlined object");
		}
		if (!l.shop.isEmpty())
		{
			parts.add("Buy " + String.join(", ", l.shop));
		}
		if (!l.ground.isEmpty())
		{
			parts.add("Pick up " + String.join(", ", l.ground));
		}
		if (!l.use.isEmpty())
		{
			parts.add("Use " + String.join(", ", l.use));
		}
		for (String say : l.say)
		{
			parts.add("Choose \"" + say + "\"");
		}
		return parts.isEmpty() ? null : String.join(" · ", parts);
	}

	/** What the game watches to tick a step, in a few words. */
	static String check(Route.Step s)
	{
		List<String> parts = new ArrayList<>();
		if (!s.quests.isEmpty())
		{
			parts.add(s.quests.size() == 1 ? "Quest" : s.quests.size() + " quests");
		}
		if (!s.started.isEmpty())
		{
			parts.add("Quest started");
		}
		for (Map.Entry<String, Integer> e : s.skills.entrySet())
		{
			parts.add(e.getValue() + " " + e.getKey());
		}
		if (s.questPoints > 0)
		{
			parts.add(s.questPoints + " quest points");
		}
		if (s.combat > 0)
		{
			parts.add(s.combat + " combat");
		}
		if (s.tier != null)
		{
			parts.add(Character.toUpperCase(s.tier.charAt(0)) + s.tier.substring(1) + " combat achievements");
		}
		if (!s.varbits.isEmpty() || !s.varps.isEmpty())
		{
			parts.add("Unlock");
		}
		if (!s.items.isEmpty() || !s.owned.isEmpty())
		{
			int n = s.items.size() + s.owned.size();
			parts.add(n == 1 ? "Holding it" : "Holding all " + n);
		}
		if (!s.gone.isEmpty())
		{
			parts.add(s.gone.size() == 1 ? "Without " + s.gone.get(0) : "Without " + s.gone.size() + " items");
		}
		if (s.at != null || s.area != null)
		{
			parts.add("Being there");
		}
		if (s.emptyBut != null)
		{
			parts.add(s.emptyBut.isEmpty() ? "Empty inventory" : "Inventory cleared");
		}
		if (s.coins >= 0)
		{
			parts.add(s.coinsAtMost ? (s.coins == 0 ? "No coins on you" : "At most " + Format.coins(s.coins) + " on you")
				: Format.coins(s.coins) + " on you");
		}
		if (!s.chat.isEmpty())
		{
			parts.add("The game's message");
		}
		if (s.deathbank != null)
		{
			parts.add(s.deathbank ? "A deathbank" : "Deathbank emptied");
		}
		if (s.house >= 0)
		{
			parts.add("House moved");
		}
		if (!s.built.isEmpty())
		{
			parts.add("Seen built in your house");
		}
		return parts.isEmpty() ? "Ticks when the next checked step is done" : String.join(" · ", parts);
	}

	static int slots(Map<Integer, Long> items, Lookup look)
	{
		int n = 0;
		for (Map.Entry<Integer, Long> e : items.entrySet())
		{
			n += look.stackable(e.getKey()) ? 1 : (int) Math.min(28, e.getValue());
		}
		return Math.min(28, n);
	}

	/** Inventory slots it takes to pick a pile up, counting stacks you already carry as free. */
	static int room(Map<Integer, Long> pile, Map<Integer, Long> inv, Lookup look)
	{
		int n = 0;
		for (Map.Entry<Integer, Long> e : pile.entrySet())
		{
			boolean stacks = look.stackable(e.getKey());
			if (stacks && inv.getOrDefault(e.getKey(), 0L) > 0)
			{
				continue;
			}
			n += stacks ? 1 : (int) Math.min(28, e.getValue());
		}
		return n;
	}

	/** "Needs 21 slots; you have 3 free", or the plain note when there is room. */
	static String roomNote(String note, int need, int free)
	{
		if (need <= 0)
		{
			return note;
		}
		return need > free ? "Needs " + need + (need == 1 ? " slot" : " slots") + "; you have " + free + " free"
			: note + " · needs " + need + (need == 1 ? " slot" : " slots");
	}

	/**
	 * What to drop to make room for a pile: carried items worth less per slot than the pile itself,
	 * cheapest first, until {@code short} slots are free. Each entry is "3 Logs" or a stack's name.
	 */
	static List<String> shed(Map<Integer, Long> pile, Map<Integer, Long> inv, Lookup look, int shortBy)
	{
		List<String> out = new ArrayList<>();
		int slots = 0;
		for (Map.Entry<Integer, Long> e : pile.entrySet())
		{
			slots += look.stackable(e.getKey()) ? 1 : (int) Math.min(28, e.getValue());
		}
		if (shortBy <= 0 || slots == 0)
		{
			return out;
		}
		double pileRate = (double) Items.worth(pile, look::price) / slots;
		List<Map.Entry<Integer, Long>> carried = new ArrayList<>();
		for (Map.Entry<Integer, Long> e : inv.entrySet())
		{
			if (!CONTAINERS.contains(e.getKey()) && !pile.containsKey(e.getKey()) && rate(e, look) < pileRate)
			{
				carried.add(e);
			}
		}
		carried.sort((a, b) -> Double.compare(rate(a, look), rate(b, look)));
		int freed = 0;
		for (Map.Entry<Integer, Long> e : carried)
		{
			if (freed >= shortBy)
			{
				break;
			}
			String name = look.name(e.getKey());
			if (look.stackable(e.getKey()))
			{
				freed++;
				out.add(name);
			}
			else
			{
				int n = (int) Math.min(e.getValue(), shortBy - freed);
				freed += n;
				out.add(n == 1 ? name : n + " " + name);
			}
		}
		return out;
	}

	/** Worth per inventory slot. */
	private static double rate(Map.Entry<Integer, Long> e, Lookup look)
	{
		return look.stackable(e.getKey()) ? (double) look.price(e.getKey()) * e.getValue() : look.price(e.getKey());
	}

	/** Adds "drop 3 Logs, Feathers first" to a pile's note when you lack the room. */
	static String shedNote(String note, Map<Integer, Long> pile, Map<Integer, Long> inv, Lookup look, int shortBy)
	{
		List<String> drop = shed(pile, inv, look, shortBy);
		return drop.isEmpty() ? note : note + " · drop " + String.join(", ", drop) + " first";
	}

	static View.ItemLine line(int id, long qty, Lookup look)
	{
		View.ItemLine l = new View.ItemLine(id, look.name(id), qty, look.price(id) * qty);
		l.noted = look.noted(id);
		return l;
	}

	static List<View.ItemLine> lines(Map<Integer, Long> items, Lookup look)
	{
		if (items == null || items.isEmpty())
		{
			return Collections.emptyList();
		}
		List<View.ItemLine> out = new ArrayList<>();
		items.forEach((id, q) -> out.add(line(id, q, look)));
		out.sort((a, b) -> Long.compare(b.worth, a.worth));
		return out;
	}
}
