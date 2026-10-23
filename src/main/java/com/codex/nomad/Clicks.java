package com.codex.nomad;

import com.codex.nomad.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import java.util.function.IntFunction;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Highlights in the game's interfaces what the route step in hand asks you to click: items to use
 * in the inventory, items to buy in a shop, and the chat option to choose.
 */
class Clicks extends Overlay
{
	private final Client client;
	private final RouteGuide guide;
	private final IntFunction<String> name;
	private final java.util.function.Supplier<Errand> errand;
	private final java.util.function.IntUnaryOperator canon;

	Clicks(Client client, RouteGuide guide, IntFunction<String> name, java.util.function.Supplier<Errand> errand,
		java.util.function.IntUnaryOperator canon)
	{
		this.errand = errand;
		this.canon = canon;
		this.client = client;
		this.guide = guide;
		this.name = name;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Errand job = errand.get();
		if (job != null && job.kind == Errand.Kind.FETCH && !job.take.isEmpty())
		{
			for (Widget root : client.getWidgetRoots())
			{
				fetch(g, root, job.take, 0);
			}
		}
		if (job != null && job.kind == Errand.Kind.UNNOTE)
		{
			Widget inv = client.getWidget(InterfaceID.Inventory.ITEMS);
			if (inv != null && !inv.isHidden() && inv.getDynamicChildren() != null)
			{
				for (Widget w : inv.getDynamicChildren())
				{
					if (w.getItemId() > 0 && !w.isHidden() && job.take.contains(w.getItemId()))
					{
						mark(g, w.getBounds());
					}
				}
			}
		}
		com.codex.nomad.core.Route.Lead l = guide.lead();
		if (l.isEmpty())
		{
			return null;
		}
		if (!l.use.isEmpty())
		{
			items(g, client.getWidget(InterfaceID.Inventory.ITEMS), l.use);
		}
		if (!l.shop.isEmpty())
		{
			items(g, client.getWidget(InterfaceID.Shopmain.ITEMS), l.shop);
		}
		if (!l.say.isEmpty())
		{
			Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
			if (options != null && !options.isHidden() && options.getDynamicChildren() != null)
			{
				for (Widget w : options.getDynamicChildren())
				{
					String text = w.getText();
					if (text == null || text.isEmpty())
					{
						continue;
					}
					String plain = text.replaceAll("<[^>]*>", "").toLowerCase(Locale.ENGLISH);
					for (String want : l.say)
					{
						if (plain.contains(want.toLowerCase(Locale.ENGLISH)))
						{
							mark(g, w.getBounds());
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Marks the items an errand asks for in any open interface but the inventory: the costume room,
	 * the tool leprechaun, the seed vault and the rest.
	 */
	private void fetch(Graphics2D g, Widget w, java.util.Set<Integer> want, int depth)
	{
		if (w == null || w.isHidden() || depth > 12)
		{
			return;
		}
		if ((w.getId() >>> 16) == InterfaceID.INVENTORY)
		{
			return;
		}
		int id = w.getItemId();
		if (id > 0 && want.contains(canon.applyAsInt(id)))
		{
			mark(g, w.getBounds());
		}
		for (Widget[] kids : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren(), w.getNestedChildren()})
		{
			if (kids != null)
			{
				for (Widget k : kids)
				{
					fetch(g, k, want, depth + 1);
				}
			}
		}
	}

	private void items(Graphics2D g, Widget parent, java.util.List<String> want)
	{
		if (parent == null || parent.isHidden())
		{
			return;
		}
		Widget[] kids = parent.getDynamicChildren();
		if (kids == null)
		{
			return;
		}
		for (Widget w : kids)
		{
			int id = w.getItemId();
			if (id <= 0 || w.isHidden())
			{
				continue;
			}
			String n = name.apply(id);
			for (String item : want)
			{
				if (item.equalsIgnoreCase(n))
				{
					mark(g, w.getBounds());
					break;
				}
			}
		}
	}

	private static void mark(Graphics2D g, Rectangle r)
	{
		if (r == null || r.width <= 0)
		{
			return;
		}
		RoundRectangle2D box = new RoundRectangle2D.Float(r.x - 1, r.y - 1, r.width + 2, r.height + 2, 6, 6);
		g.setColor(Theme.alpha(Theme.GOLD, 45));
		g.fill(box);
		g.setColor(Theme.GOLD);
		g.setStroke(new BasicStroke(2));
		g.draw(box);
	}
}
