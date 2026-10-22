package com.codex.nomad.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** The Nomad mark: a gilt satchel on its strap, with a teal wayfinder's star on the flap. */
public final class Sigil
{
	private Sigil()
	{
	}

	public static BufferedImage icon(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		Theme.smooth(g);
		paint(g, 0, 0, size);
		g.dispose();
		return image;
	}

	/** Draws the mark in a size by size box. */
	public static void paint(Graphics2D g, float x, float y, float size)
	{
		float s = size / 16f;
		float stroke = Math.max(0.6f, 0.55f * s);

		// The strap: a dark arc over the top.
		g.setStroke(new BasicStroke(Math.max(1f, 1.3f * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		Path2D strap = new Path2D.Float();
		strap.moveTo(x + 3.4f * s, y + 6.6f * s);
		strap.curveTo(x + 3.4f * s, y + 0.8f * s, x + 12.6f * s, y + 0.8f * s, x + 12.6f * s, y + 6.6f * s);
		g.setColor(Theme.mix(Theme.GOLD_DIM, Theme.INK_DEEP, 0.3));
		g.draw(strap);

		// The body: a rounded gilt bag, lit from above.
		g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		RoundRectangle2D body = new RoundRectangle2D.Float(x + 1.6f * s, y + 5.6f * s, 12.8f * s, 9.4f * s, 3.2f * s, 3.2f * s);
		g.setPaint(new GradientPaint(x, y + 5 * s, Theme.GOLD, x, y + 15 * s, Theme.mix(Theme.GOLD_DIM, Theme.INK_DEEP, 0.45f)));
		g.fill(body);
		g.setColor(Theme.INK_DEEP);
		g.draw(body);

		// The flap, a shade darker, curving down to a point at the clasp.
		Path2D flap = new Path2D.Float();
		flap.moveTo(x + 1.8f * s, y + 6.4f * s);
		flap.lineTo(x + 14.2f * s, y + 6.4f * s);
		flap.lineTo(x + 14.2f * s, y + 8.6f * s);
		flap.curveTo(x + 12.2f * s, y + 10.6f * s, x + 10.0f * s, y + 11.4f * s, x + 8.0f * s, y + 11.6f * s);
		flap.curveTo(x + 6.0f * s, y + 11.4f * s, x + 3.8f * s, y + 10.6f * s, x + 1.8f * s, y + 8.6f * s);
		flap.closePath();
		g.setPaint(new GradientPaint(x, y + 6 * s, Theme.mix(Theme.GOLD, Theme.GOLD_BRIGHT, 0.4f), x, y + 11 * s, Theme.GOLD_DIM));
		g.fill(flap);
		g.setColor(Theme.INK_DEEP);
		g.draw(flap);
		g.setColor(Theme.alpha(Color.WHITE, 110));
		g.drawLine(Math.round(x + 3.2f * s), Math.round(y + 7.0f * s), Math.round(x + 12.8f * s), Math.round(y + 7.0f * s));

		// The star on the clasp: four teal points with a soft glow.
		float cx = x + 8f * s;
		float cy = y + 10.4f * s;
		float r = 2.5f * s;
		float w = 0.6f * s;
		g.setColor(Theme.alpha(Theme.TEAL, 70));
		g.fill(new Ellipse2D.Float(cx - r * 0.8f, cy - r * 0.8f, r * 1.6f, r * 1.6f));
		Path2D star = new Path2D.Float();
		star.moveTo(cx, cy - r);
		star.lineTo(cx + w, cy - w);
		star.lineTo(cx + r, cy);
		star.lineTo(cx + w, cy + w);
		star.lineTo(cx, cy + r);
		star.lineTo(cx - w, cy + w);
		star.lineTo(cx - r, cy);
		star.lineTo(cx - w, cy - w);
		star.closePath();
		g.setColor(Theme.mix(Theme.TEAL, Color.WHITE, 0.35));
		g.fill(star);
	}
}
