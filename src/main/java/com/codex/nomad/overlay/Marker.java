package com.codex.nomad.overlay;

import com.codex.nomad.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Marks places on the ground: deathpiles, and the next place a packing list sends the player. */
public class Marker extends Overlay
{
	/** One marked tile. */
	public static final class Mark
	{
		final WorldPoint point;
		final String label;
		final Color color;

		public Mark(WorldPoint point, String label, Color color)
		{
			this.point = point;
			this.label = label;
			this.color = color;
		}
	}

	private final Client client;
	private volatile List<Mark> marks = Collections.emptyList();

	public Marker(Client client)
	{
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	public void show(List<Mark> marks)
	{
		this.marks = marks == null ? Collections.emptyList() : marks;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		for (Mark m : marks)
		{
			if (m.point.getPlane() != client.getPlane())
			{
				continue;
			}
			LocalPoint lp = LocalPoint.fromWorld(client, m.point);
			if (lp == null)
			{
				continue;
			}
			Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly != null)
			{
				OverlayUtil.renderPolygon(g, poly, m.color, Theme.alpha(m.color, 40), new BasicStroke(2f));
			}
			g.setFont(Theme.body());
			Point text = Perspective.getCanvasTextLocation(client, g, lp, m.label, 40);
			if (text != null)
			{
				OverlayUtil.renderTextLocation(g, text, m.label, m.color);
			}
		}
		return null;
	}
}
