package com.codex.nomad;

import com.codex.nomad.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
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
	private final Client client;
	private final ModelOutlineRenderer outlines;
	private final RouteGuide guide;

	Highlights(Client client, ModelOutlineRenderer outlines, RouteGuide guide)
	{
		this.client = client;
		this.outlines = outlines;
		this.guide = guide;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
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
