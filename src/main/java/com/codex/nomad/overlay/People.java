package com.codex.nomad.overlay;

import com.codex.nomad.ui.Theme;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/** Outlines the people the route step in hand names, in gold, with their name above. */
public class People extends Overlay
{
	private final Client client;
	private final ModelOutlineRenderer outlines;
	private final Supplier<Set<String>> names;

	public People(Client client, ModelOutlineRenderer outlines, Supplier<Set<String>> names)
	{
		this.client = client;
		this.outlines = outlines;
		this.names = names;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Set<String> want = names.get();
		if (want.isEmpty())
		{
			return null;
		}
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			String name = npc.getName();
			if (name == null || !want.contains(name.toLowerCase(Locale.ENGLISH)))
			{
				continue;
			}
			outlines.drawOutline(npc, 2, Theme.GOLD, 4);
			Point label = npc.getCanvasTextLocation(g, name, npc.getLogicalHeight() + 40);
			if (label != null)
			{
				OverlayUtil.renderTextLocation(g, label, name, Theme.GOLD);
			}
		}
		return null;
	}
}
