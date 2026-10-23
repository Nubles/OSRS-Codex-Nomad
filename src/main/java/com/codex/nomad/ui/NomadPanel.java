package com.codex.nomad.ui;

import com.codex.nomad.core.Kind;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.PluginPanel;

/**
 * The Nomad side panel: the crest, a search over everything owned, then four pages. Pack is everything carried with what is at
 * risk, dead weight and noting; Stores is every place things are kept; Trips is the packing lists
 * with the stops to make; Route is the way to Prifddinas step by step.
 */
public class NomadPanel extends PluginPanel
{
	static final int WIDTH = 200;
	private static final String[] TABS = {"pack", "stores", "trips", "route"};
	/** Route steps shown past the one in hand before "Show more". */
	private static final int AHEAD = 6;

	/** What the panel asks the plugin to do. */
	public interface Actions
	{
		void saveTrip(String name);

		void useTrip(String name);

		void forgetTrip(String name);

		void forgetStore(String key);

		void tickStep(String key, boolean done);

		void follow(boolean on);
	}

	private final ItemIcons icons;
	private final Actions actions;
	private final Crest crest = new Crest();
	private final Segmented tabs = new Segmented(true, "Pack", "Stores", "Trips", "Route");
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JScrollPane[] pages = {scroller(), scroller(), scroller(), scroller()};
	private final boolean[] stale = {true, true, true, true};
	private final Set<String> open = new HashSet<>();
	private final Set<String> sectionsOpen = new HashSet<>();
	private final Field tripName = new Field("Name this setup");
	private final Field find = new Field("Find anything you own");
	private final JScrollPane findPage = scroller();
	private boolean findStale = true;
	private boolean routeAll;

	private View view = new View();

	public NomadPanel(ItemIcons icons, Actions actions)
	{
		super(false);
		this.icons = icons;
		this.actions = actions;
		setLayout(new BorderLayout());
		setBackground(Theme.INK);

		JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(crest, BorderLayout.NORTH);
		JPanel tabHolder = new JPanel(new BorderLayout());
		tabHolder.setOpaque(false);
		tabHolder.setBorder(new EmptyBorder(8, 8, 2, 8));
		tabHolder.add(tabs, BorderLayout.CENTER);
		JPanel findHolder = new JPanel(new BorderLayout());
		findHolder.setOpaque(false);
		findHolder.setBorder(new EmptyBorder(6, 0, 0, 0));
		findHolder.add(find, BorderLayout.CENTER);
		tabHolder.add(findHolder, BorderLayout.SOUTH);
		north.add(tabHolder, BorderLayout.SOUTH);
		add(north, BorderLayout.NORTH);

		content.setOpaque(false);
		for (int i = 0; i < TABS.length; i++)
		{
			content.add(pages[i], TABS[i]);
		}
		content.add(findPage, "find");
		add(content, BorderLayout.CENTER);
		tabs.onChange(i ->
		{
			find.setText("");
			showTab(i);
		});
		find.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onFind();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onFind();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onFind();
			}
		});
		tripName.addActionListener(e -> saveTrip());
		updateCrest();
		refresh(true);
	}

	private static JScrollPane scroller()
	{
		JScrollPane scroll = new JScrollPane();
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		return scroll;
	}

	/** Called on the Swing thread whenever anything shown changes. */
	public void show(View view)
	{
		this.view = view;
		updateCrest();
		java.util.Arrays.fill(stale, true);
		findStale = true;
		refresh(false);
	}

	public void showTab(int index)
	{
		tabs.setSelected(index, false);
		cards.show(content, TABS[index]);
		refresh(true);
	}

	@Override
	public void onActivate()
	{
		refresh(true);
	}

	private void refresh(boolean force)
	{
		if (!force && !isShowing())
		{
			return;
		}
		if (finding())
		{
			if (findStale)
			{
				findStale = false;
				show(findPage, found());
			}
			return;
		}
		int page = tabs.getSelected();
		if (!stale[page])
		{
			return;
		}
		stale[page] = false;
		JComponent body = page == 0 ? pack() : page == 1 ? stores() : page == 2 ? trips() : route();
		show(pages[page], body);
	}

	private static void show(JScrollPane scroll, JComponent body)
	{
		int y = scroll.getVerticalScrollBar().getValue();
		scroll.setViewportView(body);
		scroll.revalidate();
		scroll.getVerticalScrollBar().setValue(y);
		scroll.repaint();
	}

	private boolean finding()
	{
		return !find.getText().trim().isEmpty();
	}

	private void onFind()
	{
		if (finding())
		{
			findStale = true;
			cards.show(content, "find");
			refresh(true);
			findPage.getVerticalScrollBar().setValue(0);
		}
		else
		{
			cards.show(content, TABS[tabs.getSelected()]);
			stale[tabs.getSelected()] = true;
			refresh(true);
		}
	}

	private void rebuild()
	{
		stale[tabs.getSelected()] = true;
		findStale = true;
		refresh(true);
	}

	// ---- the crest ----

	private void updateCrest()
	{
		View v = view;
		String heading = account(v.account).toUpperCase(Locale.ENGLISH);
		if (!v.loggedIn && v.inventory.isEmpty() && v.stores.isEmpty())
		{
			crest.update("NOMAD", "-", "", -1, "Log in to open your pack", Theme.TEXT_MUTED, Theme.GOLD_DIM);
			return;
		}
		int free = 28 - v.invUsed;
		String status;
		String shortStatus = null;
		Color color;
		View.Peril pile = null;
		View.Peril bank = null;
		View.Peril floor = null;
		for (View.Peril p : v.perils)
		{
			if (p.kind == Kind.FLOOR && (floor == null || p.until < floor.until))
			{
				floor = p;
			}
			if (p.kind == Kind.DEATHPILE && (pile == null || p.ticksLeft < pile.ticksLeft))
			{
				pile = p;
			}
			if (p.kind == Kind.DEATHBANK)
			{
				bank = p;
			}
		}
		if (floor != null)
		{
			long left = floor.until - v.now;
			status = "On the floor: " + Format.clock(left) + " left · " + Format.coins(floor.worth);
			shortStatus = "Dropped: " + Format.clock(left) + " left";
			color = left <= 30_000 ? Theme.BLOOD : Theme.EMBER;
		}
		else if (pile != null)
		{
			status = "Deathpile: " + Format.ticks(pile.ticksLeft) + " left · " + Format.coins(pile.worth);
			shortStatus = "Deathpile: " + Format.ticks(pile.ticksLeft) + " left";
			color = pile.ticksLeft <= 1000 ? Theme.BLOOD : Theme.EMBER;
		}
		else if (bank != null)
		{
			status = bank.title + (bank.exact ? " · " + Format.coins(bank.worth) : "");
			shortStatus = "Deathbank at risk";
			color = Theme.EMBER;
		}
		else if (v.fetch != null && !v.fetch.stops.isEmpty())
		{
			status = "Next: " + v.fetch.stops.get(0).name + " for " + v.fetch.name;
			shortStatus = "Next: " + v.fetch.stops.get(0).name;
			color = Theme.TEAL;
		}
		else if (v.fetch != null && v.fetch.ready == v.fetch.total)
		{
			status = v.fetch.name + ": packed and ready";
			color = Theme.GREEN;
		}
		else if (v.route != null && v.route.following && v.route.current >= 0)
		{
			status = "Route: step " + (v.route.done + 1) + " of " + v.route.total;
			color = Theme.GOLD;
		}
		else
		{
			status = "Nothing at risk · " + Format.coins(v.carriedWorth) + " carried";
			shortStatus = "Nothing at risk";
			color = Theme.TEAL;
		}
		crest.update(heading, String.valueOf(free), free == 1 ? "slot free" : "slots free", free / 28.0, status, shortStatus, color,
			free <= 2 ? Theme.EMBER : Theme.GOLD);
	}

	static String account(int varbit)
	{
		switch (varbit)
		{
			case 2:
				return "Ultimate ironman";
			case 1:
				return "Ironman";
			case 3:
				return "Hardcore ironman";
			case 4:
			case 5:
			case 6:
				return "Group ironman";
			case 0:
				return "Main account";
			default:
				return "Nomad";
		}
	}

	// ---- pack ----

	private JComponent pack()
	{
		StackPanel p = new StackPanel(6, 8);
		View v = view;
		if (!v.loggedIn && v.inventory.isEmpty())
		{
			p.add(note("Log in and Nomad starts keeping your pack: what you carry, what you keep, and what you could lose."));
			return p;
		}
		if (v.account >= 0 && v.account != 2)
		{
			p.add(note("Nomad is built for ultimate ironmen. It works on any account, but deaths and deathbanks follow ultimate ironman rules."));
		}
		if (!v.perils.isEmpty())
		{
			p.add(new SectionHeader("At risk", String.valueOf(v.perils.size())));
			for (View.Peril r : v.perils)
			{
				long left = r.until - v.now;
				String figure = r.kind == Kind.FLOOR ? Format.clock(left) : r.kind == Kind.DEATHPILE ? Format.ticks(r.ticksLeft) : r.exact ? Format.coins(r.worth) : "?";
				Color c = r.kind == Kind.FLOOR ? left <= 30_000 ? Theme.BLOOD : Theme.EMBER
					: r.kind == Kind.DEATHPILE ? r.ticksLeft <= 1000 ? Theme.BLOOD : Theme.EMBER : r.kind == Kind.DEATHBANK ? Theme.EMBER : Theme.GOLD;
				String noteText = r.kind == Kind.DEATHPILE || r.kind == Kind.FLOOR ? Format.coins(r.worth) + " · " + r.note
					: r.exact ? r.count + " items · " + r.note : "Contents unknown · " + r.note;
				p.add(new Row(-1, 0, null, r.title, figure, c, noteText, c));
			}
		}
		JPanel tiles = new JPanel(new GridLayout(1, 2, 6, 0));
		tiles.setOpaque(false);
		tiles.add(new StatTile(Format.coins(v.carriedWorth), "Carried", (28 - v.invUsed) + " slots free", Theme.GOLD));
		tiles.add(new StatTile(Format.coins(v.dropNow), "Dying now", "keeps " + Format.coins(v.keepNow), v.dropNow > 0 ? Theme.EMBER : Theme.TEAL));
		p.add(tiles);
		if (!v.kept.isEmpty())
		{
			p.add(new SectionHeader("Kept if you die", v.keepCount == 1 ? "1 item" : v.keepCount + " items"));
			p.add(grid(v.kept, Theme.alpha(Theme.TEAL, 140)));
		}

		p.add(new SectionHeader("Inventory", v.invUsed + " / 28"));
		p.add(grid(v.inventory, null));
		if (!v.worn.isEmpty())
		{
			p.add(new SectionHeader("Worn", String.valueOf(v.worn.size())));
			p.add(grid(v.worn, null));
		}
		if (!v.pouch.isEmpty())
		{
			p.add(new SectionHeader("Rune pouch", String.valueOf(v.pouch.size())));
			p.add(grid(v.pouch, null));
		}
		for (View.Vessel vessel : v.vessels)
		{
			p.add(new SectionHeader(vessel.name, String.valueOf(vessel.items.size())));
			p.add(grid(vessel.items, null));
		}
		p.add(new SectionHeader("Looting bag", v.bagKnown ? v.bagUsed + " / 28" : "unchecked"));
		if (!v.bagKnown)
		{
			p.add(note("Check or open your looting bag once so Nomad can count it."));
		}
		else if (v.bag.isEmpty())
		{
			p.add(foot("Empty, seen " + Format.ago(v.now - v.bagSeen)));
		}
		else
		{
			p.add(grid(v.bag, Theme.alpha(Theme.EMBER, 120)));
			p.add(foot("Opens only in the Wilderness or on death · seen " + Format.ago(v.now - v.bagSeen)));
		}

		if (!v.idle.isEmpty())
		{
			p.add(new SectionHeader("Dead weight", v.idleSlots + " slots"));
			for (View.IdleLine i : v.idle)
			{
				String where = i.home != null ? "Belongs in the STASH at " + i.home : "Untouched for " + Format.duration(i.idleMs) + " of play";
				p.add(new Row(i.item.id, i.item.qty, icons, i.item.name, i.slots + (i.slots == 1 ? " slot" : " slots"), Theme.GOLD_DIM, where, null));
			}
			p.add(foot("Carried " + Format.duration(v.idleAfter) + " of play or more without being used."));
		}
		if (!v.notes.isEmpty())
		{
			p.add(new SectionHeader("Noting", String.valueOf(v.notes.size())));
			for (View.NoteLine n : v.notes)
			{
				String figure = n.unnote ? "Unnote" : "Frees " + n.frees;
				String fee = n.fee > 0 ? " · " + n.fee + " gp each" : n.fee == 0 ? " · free" : "";
				String where = n.who + ", " + n.where + fee + (n.wilderness ? " · Wilderness" : "");
				p.add(new Row(n.item.id, n.item.qty, icons, n.item.name, figure, n.unnote ? Theme.TEAL : Theme.GREEN, where,
					n.wilderness ? Theme.alpha(Theme.BLOOD, 160) : null));
			}
		}
		return p;
	}

	private JComponent grid(List<View.ItemLine> items, Color rim)
	{
		JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		holder.setOpaque(false);
		if (items.isEmpty())
		{
			holder.add(Ui.label("Nothing", Theme.small(), Theme.TEXT_FAINT));
			return holder;
		}
		holder.add(new ItemGrid(items, icons, rim, 5));
		return holder;
	}

	// ---- stores ----

	private JComponent stores()
	{
		StackPanel p = new StackPanel(6, 8);
		View v = view;
		if (v.stores.isEmpty())
		{
			p.add(note("Nomad remembers every place you keep things. Deposit into a STASH unit, open a costume room piece or the seed vault, and it shows here. Deathpiles, deathbanks, dropped items, the tool leprechaun, the Blast Furnace, fossil storage and the coffers appear on their own."));
			return p;
		}
		p.add(new StatTile(Format.coins(v.keptWorth), "Kept away", v.stores.size() + " places remembered", Theme.GOLD));
		Kind last = null;
		for (View.StoreView s : v.stores)
		{
			if (s.kind != last)
			{
				int count = 0;
				for (View.StoreView o : v.stores)
				{
					count += o.kind == s.kind ? 1 : 0;
				}
				p.add(new SectionHeader(s.kind == Kind.STASH ? "STASH units" : s.kind.getLabel(), String.valueOf(count)));
				last = s.kind;
			}
			p.add(store(s));
		}
		return p;
	}

	private JComponent store(View.StoreView s)
	{
		boolean isOpen = open.contains(s.key);
		Card card = new Card(new BorderLayout());
		card.setBorder(new EmptyBorder(6, 8, 6, 8));
		Color c = s.kind == Kind.DEATHPILE || s.kind == Kind.DEATHBANK || s.kind == Kind.FLOOR ? Theme.EMBER : null;
		if (c != null)
		{
			card.accent(c);
		}
		String figure = s.kind == Kind.FLOOR ? Format.clock(s.until - view.now) : s.kind == Kind.DEATHPILE ? Format.ticks(s.ticksLeft)
			: !s.exact ? "?" : s.worth > 0 ? Format.coins(s.worth) : "";
		String sub = (s.exact ? s.items.size() + (s.items.size() == 1 ? " item" : " items") : "Estimated from your death")
			+ (s.distance >= 0 ? " · " + s.distance + " tiles" : "")
			+ (s.kind.automatic() ? " · read live" : s.seen > 0 ? " · " + Format.ago(view.now - s.seen) : "");
		card.add(new Row.Plain(s.name, figure, c == null ? Theme.GOLD : c, sub), BorderLayout.NORTH);
		StackPanel in = new StackPanel(4, 0);
		in.setBorder(new EmptyBorder(6, 0, 0, 0));
		if (!s.items.isEmpty())
		{
			in.add(grid(s.items, null));
		}
		card.add(in, BorderLayout.CENTER);
		if (isOpen)
		{
			in.add(foot(s.kind.getSafety()));
			JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			holder.setOpaque(false);
			holder.add(new CodexButton("Forget this place", false, () -> actions.forgetStore(s.key)));
			in.add(holder);
		}
		card.highlighted(isOpen);
		card.onClick(() ->
		{
			if (!open.remove(s.key))
			{
				open.add(s.key);
			}
			rebuild();
		});
		return card;
	}

	// ---- find ----

	/** One item and every place it is. */
	private static final class Hit
	{
		final View.ItemLine item;
		long total;
		final List<String> where = new ArrayList<>();
		final List<Long> qty = new ArrayList<>();
		boolean risky;

		Hit(View.ItemLine item)
		{
			this.item = item;
		}
	}

	private JComponent found()
	{
		StackPanel p = new StackPanel(6, 8);
		View v = view;
		String q = find.getText().trim().toLowerCase(Locale.ENGLISH);
		Map<String, Hit> hits = new LinkedHashMap<>();
		look(hits, q, v.inventory, "Inventory", false);
		look(hits, q, v.worn, "Worn", false);
		look(hits, q, v.bag, "Looting bag", true);
		look(hits, q, v.pouch, "Rune pouch", false);
		for (View.Vessel vessel : v.vessels)
		{
			look(hits, q, vessel.items, vessel.name, false);
		}
		for (View.StoreView s : v.stores)
		{
			String place = s.kind == Kind.FLOOR ? "Floor, world " + s.world + " (" + Format.clock(s.until - v.now) + ")"
				: s.kind == Kind.DEATHPILE ? "Deathpile (" + Format.ticks(s.ticksLeft) + ")"
				: s.kind == Kind.STASH ? "STASH, " + s.name
				: s.kind == Kind.DEATHBANK ? "Deathbank, " + s.name : s.name;
			look(hits, q, s.items, place + (s.distance >= 0 ? ", " + s.distance + " tiles" : ""), s.kind.perilous());
		}
		p.add(new SectionHeader("Found", String.valueOf(hits.size())));
		if (hits.isEmpty())
		{
			p.add(note("Nothing you carry or keep is called that. Nomad only knows the places you have opened or used since it was installed."));
			return p;
		}
		for (Hit h : hits.values())
		{
			List<String> where = new ArrayList<>();
			for (int i = 0; i < h.where.size(); i++)
			{
				where.add(h.where.size() > 1 && h.qty.get(i) > 1 ? h.where.get(i) + " " + Format.whole(h.qty.get(i)) : h.where.get(i));
			}
			p.add(new Row(h.item.id, h.total, icons, h.item.name, Format.whole(h.total), h.risky ? Theme.EMBER : Theme.GOLD,
				String.join(" · ", where), h.risky ? Theme.alpha(Theme.EMBER, 120) : null));
		}
		p.add(foot("Clear the search to go back."));
		return p;
	}

	private static void look(Map<String, Hit> hits, String q, List<View.ItemLine> items, String place, boolean risky)
	{
		for (View.ItemLine l : items)
		{
			if (l.name == null || !l.name.toLowerCase(Locale.ENGLISH).contains(q))
			{
				continue;
			}
			Hit h = hits.computeIfAbsent(l.name, k -> new Hit(l));
			h.total += l.qty;
			h.where.add(place);
			h.qty.add(l.qty);
			h.risky |= risky;
		}
	}

	// ---- trips ----

	private void saveTrip()
	{
		String name = tripName.getText().trim();
		if (!name.isEmpty())
		{
			actions.saveTrip(name);
			tripName.setText("");
		}
	}

	private JComponent trips()
	{
		StackPanel p = new StackPanel(6, 8);
		View v = view;
		p.add(new SectionHeader("Save a packing list"));
		JPanel form = new JPanel(new BorderLayout(6, 0));
		form.setOpaque(false);
		form.add(tripName, BorderLayout.CENTER);
		form.add(new CodexButton("Save", true, this::saveTrip), BorderLayout.EAST);
		p.add(form);
		p.add(foot("Gear up once for a trip and save it: Nomad remembers what you wore and carried."));

		View.FetchView f = v.fetch;
		if (f != null)
		{
			boolean ready = f.ready == f.total;
			p.add(new SectionHeader(f.name, f.ready + " of " + f.total + " ready"));
			JPanel tiles = new JPanel(new GridLayout(1, 2, 6, 0));
			tiles.setOpaque(false);
			tiles.add(new StatTile(f.ready + "/" + f.total, "Packed", ready ? "Ready to go" : f.missing + " missing", ready ? Theme.GREEN : Theme.GOLD));
			tiles.add(new StatTile(f.slots + "/28", "Slots", f.slots <= 28 ? "Fits" : "Too much to carry", f.slots <= 28 ? Theme.TEAL : Theme.BLOOD));
			p.add(tiles);
			if (!f.stops.isEmpty())
			{
				p.add(new SectionHeader("Stops", String.valueOf(f.stops.size())));
				int n = 1;
				for (View.StopView s : f.stops)
				{
					Color c = s.kind == Kind.DEATHPILE ? Theme.EMBER : n == 1 ? Theme.TEAL : Theme.GOLD_DIM;
					List<String> take = new ArrayList<>();
					for (View.ItemLine l : s.take)
					{
						take.add(l.qty > 1 ? Format.amount(l.qty) + " " + l.name : l.name);
					}
					Card card = new Card(new BorderLayout());
					card.setBorder(new EmptyBorder(6, 8, 6, 8));
					card.accent(c);
					card.highlighted(n == 1);
					card.add(new Row.Plain(n + ". " + s.name, s.distance >= 0 ? s.distance + " tiles" : s.kind.getLabel(), c, String.join(", ", take)), BorderLayout.NORTH);
					p.add(card);
					n++;
				}
			}
			p.add(new SectionHeader("The list"));
			for (View.FetchLine l : f.lines)
			{
				boolean ok = l.have >= l.want.qty;
				String figure = Format.amount(l.have) + "/" + Format.amount(l.want.qty);
				String where = ok ? (l.worn ? "Worn" : "Carried") : l.from != null ? "In " + l.from
					: l.bagged > 0 ? "In your looting bag" : "Not anywhere Nomad knows";
				Color c = ok ? Theme.GREEN : l.missing > 0 && l.bagged == 0 ? Theme.BLOOD : Theme.GOLD;
				p.add(new Row(l.want.id, l.want.qty, icons, l.want.name, figure, c, where, null));
			}
			if (!f.extra.isEmpty())
			{
				p.add(new SectionHeader("Not on the list", String.valueOf(f.extra.size())));
				p.add(grid(f.extra, Theme.alpha(Theme.GOLD_DIM, 160)));
				p.add(foot("Carried but not needed: leave these somewhere before you go."));
			}
		}
		if (!v.trips.isEmpty())
		{
			p.add(new SectionHeader("Packing lists", String.valueOf(v.trips.size())));
			for (String name : v.trips)
			{
				boolean active = name.equals(v.active);
				Card card = new Card(new BorderLayout());
				card.setBorder(new EmptyBorder(5, 8, 5, 8));
				card.add(new Row.Plain(name, active ? "Fetching" : "", Theme.TEAL, null), BorderLayout.CENTER);
				if (active)
				{
					card.accent(Theme.TEAL).highlighted(true);
					card.add(new CodexButton("Forget", false, () -> actions.forgetTrip(name)), BorderLayout.EAST);
				}
				else
				{
					card.onClick(() -> actions.useTrip(name));
				}
				p.add(card);
			}
		}
		return p;
	}

	// ---- route ----

	private JComponent route()
	{
		StackPanel p = new StackPanel(6, 8);
		View.RouteView r = view.route;
		if (r == null || r.total == 0)
		{
			p.add(note("The route could not be read."));
			return p;
		}
		JPanel tiles = new JPanel(new GridLayout(1, 2, 6, 0));
		tiles.setOpaque(false);
		tiles.add(new StatTile(r.done + "/" + r.total, "Steps done", r.current < 0 ? "Route finished" : "ticked or done", Theme.GOLD));
		tiles.add(new StatTile(Format.percent(r.done / (double) r.total), "Of the way", "to Prifddinas", Theme.TEAL));
		p.add(tiles);
		JPanel holder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		holder.setOpaque(false);
		holder.add(new CodexButton(r.following ? "Stop following" : "Follow the route", !r.following, () -> actions.follow(!r.following)));
		p.add(holder);

		String currentSection = r.current >= 0 ? r.steps.get(r.current).section : null;
		for (int si = 0; si < r.sections.size(); si++)
		{
			String sec = r.sections.get(si);
			int[] prog = r.progress.get(si);
			boolean secOpen = sectionsOpen.contains(sec) || sec.equals(currentSection) && !sectionsOpen.contains("-" + sec);
			Card head = new Card(new BorderLayout());
			head.setBorder(new EmptyBorder(5, 8, 5, 8));
			boolean done = prog[0] == prog[1];
			head.add(new Row.Plain((si + 1) + ". " + sec, prog[0] + "/" + prog[1], done ? Theme.GREEN : sec.equals(currentSection) ? Theme.GOLD : Theme.TEXT_MUTED, null), BorderLayout.CENTER);
			head.highlighted(sec.equals(currentSection));
			head.onClick(() ->
			{
				if (sec.equals(currentSection))
				{
					if (!sectionsOpen.remove("-" + sec))
					{
						sectionsOpen.add("-" + sec);
					}
				}
				else if (!sectionsOpen.remove(sec))
				{
					sectionsOpen.add(sec);
				}
				rebuild();
			});
			p.add(head);
			if (!secOpen)
			{
				continue;
			}
			int shown = 0;
			for (View.StepView s : r.steps)
			{
				if (!s.section.equals(sec))
				{
					continue;
				}
				boolean isCurrent = s.index == r.current;
				boolean ahead = r.current >= 0 && s.index > r.current;
				if (sec.equals(currentSection) && !routeAll)
				{
					if (s.done && !isCurrent && s.index < r.current - 2)
					{
						continue;
					}
					if (ahead && shown >= AHEAD)
					{
						continue;
					}
					if (ahead)
					{
						shown++;
					}
				}
				p.add(new StepRow(s, isCurrent, () -> actions.tickStep(s.key, !s.done)));
			}
			if (sec.equals(currentSection))
			{
				JPanel more = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
				more.setOpaque(false);
				more.add(new CodexButton(routeAll ? "Show fewer steps" : "Show every step", false, () ->
				{
					routeAll = !routeAll;
					rebuild();
				}));
				p.add(more);
			}
		}
		p.add(foot("Steps paraphrased from the OSRS Wiki's Ultimate Ironman walkthrough to Prifddinas (CC BY-NC-SA 3.0)."));
		return p;
	}

	// ---- small parts ----

	private static JComponent note(String text)
	{
		Card card = new Card(new BorderLayout());
		card.add(Ui.wrapped(text, Theme.body(), Theme.TEXT_MUTED, WIDTH - 24), BorderLayout.CENTER);
		return card;
	}

	private static JComponent foot(String text)
	{
		return Ui.wrapped(text, Theme.small(), Theme.TEXT_FAINT, WIDTH);
	}
}
