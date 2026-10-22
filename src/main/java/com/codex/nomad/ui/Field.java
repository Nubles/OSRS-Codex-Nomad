package com.codex.nomad.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/** A text box in ink and gold, with a quiet hint while it is empty. */
class Field extends JTextField
{
	private final String hint;

	Field(String hint)
	{
		this.hint = hint;
		setOpaque(false);
		setFont(Theme.body());
		setForeground(Theme.TEXT);
		setCaretColor(Theme.GOLD_BRIGHT);
		setSelectionColor(Theme.alpha(Theme.GOLD, 90));
		setBorder(new EmptyBorder(4, 8, 4, 8));
		setPreferredSize(new Dimension(10, 26));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 8, 8);
		g.setColor(Theme.INK_DEEP);
		g.fill(shape);
		g.setColor(isFocusOwner() ? Theme.GOLD_DIM : Theme.STROKE);
		g.draw(shape);
		if (getText().isEmpty() && !isFocusOwner())
		{
			g.setFont(Theme.body());
			g.setColor(Theme.TEXT_FAINT);
			g.drawString(hint, 8, (getHeight() + g.getFontMetrics().getAscent()) / 2 - 1);
		}
		g.dispose();
		super.paintComponent(graphics);
	}
}
