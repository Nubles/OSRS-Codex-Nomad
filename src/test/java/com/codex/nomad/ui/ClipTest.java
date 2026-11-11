package com.codex.nomad.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import com.codex.nomad.overlay.Plaque;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import org.junit.Test;

/**
 * Drawing never clears or leaves changed the clip, font, stroke or hints of the graphics it is
 * given: RuneLite hands every overlay the same graphics, and a cleared clip breaks the renderer.
 */
public class ClipTest
{
	@Test
	public void thePlaqueLeavesTheGraphicsAsItFoundThem()
	{
		BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setClip(new Rectangle(5, 5, 300, 200));
		Font font = new Font(Font.SERIF, Font.PLAIN, 9);
		g.setFont(font);
		Stroke stroke = new BasicStroke(3);
		g.setStroke(stroke);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		Shape clip = g.getClip();

		Plaque plaque = new Plaque();
		plaque.show("Nomad", Theme.GOLD, Arrays.asList(
			new Plaque.Row("Deathpile", "38:42", "60.2M · the quickest way back is your home teleport", Theme.EMBER),
			new Plaque.Row("Slots", "3 free", null, Theme.GOLD)));
		assertNotNull(plaque.render(g));

		assertEquals(clip, g.getClip());
		assertEquals(font, g.getFont());
		assertEquals(stroke, g.getStroke());
		assertEquals(RenderingHints.VALUE_ANTIALIAS_OFF, g.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
		g.dispose();
	}

	@Test
	public void anAccentedCardRestoresTheClip()
	{
		Card card = new Card(new BorderLayout());
		card.accent(Theme.EMBER);
		card.setSize(180, 40);
		BufferedImage img = new BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setClip(new Rectangle(2, 2, 150, 30));
		Shape clip = g.getClip();
		card.paint(g);
		assertEquals(clip, g.getClip());
		g.dispose();
	}
}
