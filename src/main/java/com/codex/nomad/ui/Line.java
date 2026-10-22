package com.codex.nomad.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import javax.swing.JComponent;

/** One quiet line: an optional coloured dot, a label, and a figure to the right. */
public class Line extends JComponent
{
	private final String label;
	private final String figure;
	private final Color dot;
	private final Color labelColor;
	private final Color figureColor;

	public Line(String label, String figure, Color dot)
	{
		this(label, figure, dot, Theme.TEXT_MUTED, Theme.TEXT);
	}

	public Line(String label, String figure, Color dot, Color labelColor, Color figureColor)
	{
		this.label = label;
		this.figure = figure;
		this.dot = dot;
		this.labelColor = labelColor;
		this.figureColor = figureColor;
		setOpaque(false);
		setFont(Theme.small());
		setPreferredSize(new Dimension(10, 15));
		setToolTipText(figure == null || figure.isEmpty() ? label : label + ": " + figure);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		g.setFont(getFont());
		FontMetrics fm = g.getFontMetrics();
		int w = getWidth();
		int base = (getHeight() + fm.getAscent()) / 2 - 1;
		int x = 0;
		if (dot != null)
		{
			g.setColor(dot);
			g.fill(new Ellipse2D.Float(1, getHeight() / 2f - 3, 6, 6));
			x = 11;
		}
		int fw = figure == null ? 0 : fm.stringWidth(figure);
		if (figure != null)
		{
			Ui.shadowed(g, figure, w - fw, base, figureColor);
		}
		Ui.shadowed(g, Ui.clip(label, fm, w - x - fw - 6), x, base, labelColor);
		g.dispose();
	}
}
