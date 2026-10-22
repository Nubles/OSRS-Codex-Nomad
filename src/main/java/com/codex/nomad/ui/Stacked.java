package com.codex.nomad.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.JComponent;

/** One bar split into coloured parts, each as wide as its share. */
public class Stacked extends JComponent
{
	private final double[] parts;
	private final Color[] colors;
	private final int height;

	public Stacked(List<Double> parts, List<Color> colors, int height)
	{
		this.parts = new double[parts.size()];
		for (int i = 0; i < parts.size(); i++)
		{
			this.parts[i] = parts.get(i);
		}
		this.colors = colors.toArray(new Color[0]);
		this.height = height;
		setOpaque(false);
		setPreferredSize(new Dimension(10, height + 2));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		paint(g, parts, colors, 0, 1, getWidth(), height);
		g.dispose();
	}

	static void paint(Graphics2D g, double[] parts, Color[] colors, int x, int y, int w, int h)
	{
		RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, w, h, h, h);
		g.setColor(Theme.INK_DEEP);
		g.fill(shape);
		double total = 0;
		for (double p : parts)
		{
			total += Math.max(0, p);
		}
		if (total <= 0)
		{
			return;
		}
		java.awt.Shape clip = g.getClip();
		g.clip(shape);
		double at = x;
		for (int i = 0; i < parts.length; i++)
		{
			double width = w * Math.max(0, parts[i]) / total;
			if (width <= 0)
			{
				continue;
			}
			g.setColor(colors[i]);
			g.fillRect((int) Math.round(at), y, (int) Math.ceil(width), h);
			at += width;
			if (at < x + w - 1)
			{
				g.setColor(Theme.INK_DEEP);
				g.drawLine((int) Math.round(at), y, (int) Math.round(at), y + h);
			}
		}
		g.setColor(Theme.alpha(Color.WHITE, 40));
		g.drawLine(x + h / 2, y, x + w - h / 2, y);
		g.setClip(clip);
	}
}
