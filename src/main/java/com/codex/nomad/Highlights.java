package com.codex.nomad;

import com.codex.nomad.core.Death;
import com.codex.nomad.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.util.List;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.Perspective;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Highlights in the world what the route step in hand asks you to click: the people it names, the
 * objects to use and the items to pick up, each outlined in gold with a word above it.
 */
class Highlights extends Overlay
{
	private static final String HOLDS = "Your deathbank";

	private final Client client;
	private final ModelOutlineRenderer outlines;
	private final RouteGuide guide;
	private final Supplier<List<TileObject>> warnAt;
	private final Supplier<String> warning;
	private final Supplier<String> bank;
	private final Supplier<List<TileObject>> holders;

	/**
	 * @param warnAt objects to mark in red, such as the ways out of a raid with things left on its floor
	 * @param warning the words above them
	 */
	Highlights(Client client, ModelOutlineRenderer outlines, RouteGuide guide, Supplier<List<TileObject>> warnAt, Supplier<String> warning,
		Supplier<String> bank, Supplier<List<TileObject>> holders)
	{
		this.bank = bank;
		this.holders = holders;
		this.client = client;
		this.outlines = outlines;
		this.guide = guide;
		this.warnAt = warnAt;
		this.warning = warning;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		List<TileObject> danger = warnAt.get();
		if (!danger.isEmpty())
		{
			String words = warning.get();
			for (TileObject o : danger)
			{
				outlines.drawOutline(o, 2, Theme.BLOOD, 4);
				Point at = o.getCanvasTextLocation(g, words, 200);
				if (at != null)
				{
					OverlayUtil.renderTextLocation(g, at, words, Theme.BLOOD);
				}
			}
		}
		String lair = bank.get();
		if (lair != null)
		{
			for (NPC npc : client.getTopLevelWorldView().npcs())
			{
				if (Death.holds(lair, npc.getId(), false))
				{
					outlines.drawOutline(npc, 2, Theme.EMBER, 4);
					Point at = npc.getCanvasTextLocation(g, HOLDS, npc.getLogicalHeight() + 40);
					if (at != null)
					{
						OverlayUtil.renderTextLocation(g, at, HOLDS, Theme.EMBER);
					}
				}
			}
			for (TileObject o : holders.get())
			{
				if (Death.holds(lair, o.getId(), true))
				{
					outlines.drawOutline(o, 2, Theme.EMBER, 4);
					Point at = o.getCanvasTextLocation(g, HOLDS, 150);
					if (at != null)
					{
						OverlayUtil.renderTextLocation(g, at, HOLDS, Theme.EMBER);
					}
				}
			}
		}
		if (guide.lead().isEmpty())
		{
			return null;
		}
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (!guide.wanted(npc))
			{
				continue;
			}
			outlines.drawOutline(npc, 2, Theme.GOLD, 4);
			label(g, npc.getCanvasTextLocation(g, npc.getName(), npc.getLogicalHeight() + 40), npc.getName());
		}
		for (TileObject o : guide.objects())
		{
			outlines.drawOutline(o, 2, Theme.GOLD, 4);
			Shape box = o.getClickbox();
			if (box != null)
			{
				g.setColor(Theme.alpha(Theme.GOLD, 40));
				g.fill(box);
			}
			label(g, o.getCanvasTextLocation(g, "Click", 150), "Click");
		}
		for (Object[] lying : guide.ground())
		{
			Tile t = (Tile) lying[0];
			String name = (String) lying[1];
			LocalPoint lp = t.getLocalLocation();
			Polygon poly = lp == null ? null : Perspective.getCanvasTilePoly(client, lp);
			if (poly != null)
			{
				g.setColor(Theme.alpha(Theme.GOLD, 40));
				g.fill(poly);
				g.setColor(Theme.GOLD);
				g.setStroke(new BasicStroke(2));
				g.draw(poly);
			}
			label(g, lp == null ? null : Perspective.getCanvasTextLocation(client, g, lp, "Take " + name, 40), "Take " + name);
		}
		return null;
	}

	private static void label(Graphics2D g, Point at, String text)
	{
		if (at != null && text != null)
		{
			OverlayUtil.renderTextLocation(g, at, text, Theme.GOLD);
		}
	}
}
