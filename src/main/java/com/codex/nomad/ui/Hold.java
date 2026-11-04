package com.codex.nomad.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/**
 * How full a cargo hold was when last seen: a slim gauge ruled in tenths like a ship's draught
 * marks, gold while there is room, ember when nearly full and blood when full.
 */
final class Hold extends JComponent
{
	private final int used;
	private final int capacity;

	Hold(int used, int capacity)
	{
		this.used = Math.max(0, used);
		this.capacity = Math.max(1, capacity);
		setPreferredSize(new Dimension(10, 16));
		setOpaque(false);
		setToolTipText(used + " of " + capacity + " slots used, as last seen");
	}

	static Color color(int used, int capacity)
	{
		return used >= capacity ? Theme.BLOOD : used * 10 >= capacity * 9 ? Theme.EMBER : Theme.GOLD;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		g.setFont(Theme.small());
		FontMetrics fm = g.getFontMetrics();
		Color c = color(used, capacity);
		String label = "HOLD";
		String figure = Format.whole(used) + " / " + Format.whole(capacity);
		int base = (getHeight() + fm.getAscent()) / 2 - 1;
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(label, 0, base);
		int fw = fm.stringWidth(figure);
		Ui.shadowed(g, figure, getWidth() - fw, base, c);

		int x = fm.stringWidth(label) + 8;
		int w = getWidth() - x - fw - 8;
		int h = 6;
		int y = (getHeight() - h) / 2;
		if (w > 10)
		{
			g.setColor(Theme.INK_DEEP);
			g.fill(new RoundRectangle2D.Float(x, y, w, h, h, h));
			float fill = w * Math.min(1f, used / (float) capacity);
			if (fill > 0)
			{
				g.setPaint(new GradientPaint(x, 0, Theme.mix(c, Theme.INK, 0.45), x + w, 0, c));
				g.fill(new RoundRectangle2D.Float(x, y, Math.max(h, fill), h, h, h));
				g.setColor(Theme.alpha(Color.WHITE, 40));
				g.drawLine(x + 1, y, Math.round(x + Math.max(h, fill)) - 2, y);
			}
			g.setColor(Theme.alpha(Theme.STROKE, 200));
			for (int i = 1; i < 10; i++)
			{
				int tx = x + Math.round(w * i / 10f);
				g.drawLine(tx, y + (i == 5 ? 0 : 2), tx, y + h - 1);
			}
			g.setColor(Theme.STROKE_SOFT);
			g.draw(new RoundRectangle2D.Float(x - 0.5f, y - 0.5f, w, h, h, h));
		}
		g.dispose();
	}
}
