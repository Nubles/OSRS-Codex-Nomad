package com.codex.nomad.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * One step of the route: a box to tick, the step in words, and for the step in hand its tips and
 * what the game checks. Steps the game has confirmed cannot be unticked.
 */
class StepRow extends Card
{
	private static final int BOX = 12;

	StepRow(View.StepView s, boolean current, Runnable toggle)
	{
		super(new BorderLayout(8, 0));
		setBorder(new EmptyBorder(current ? 7 : 4, 8, current ? 7 : 4, 8));
		if (current)
		{
			accent(Theme.GOLD).highlighted(true);
		}
		add(new Box(s.done, s.met, current), BorderLayout.WEST);
		StackPanel text = new StackPanel(2, 0);
		Color color = s.done ? Theme.TEXT_FAINT : current ? Theme.TEXT : Theme.TEXT_MUTED;
		text.add(Ui.wrapped(s.text, current ? Theme.body() : Theme.small(), color, NomadPanel.WIDTH - 40));
		if (current)
		{
			for (String tip : s.tips)
			{
				text.add(Ui.wrapped(tip, Theme.small(), Theme.GOLD_DIM, NomadPanel.WIDTH - 40));
			}
		}
		if (s.check != null && (current || !s.done))
		{
			text.add(Ui.label(s.met ? "Done in game" : s.check, Theme.small(), s.met ? Theme.GREEN : Theme.TEAL));
		}
		JPanel holder = new JPanel(new BorderLayout());
		holder.setOpaque(false);
		holder.add(text, BorderLayout.NORTH);
		add(holder, BorderLayout.CENTER);
		if (!s.met)
		{
			onClick(toggle);
			setToolTipText(s.done ? "Click to untick" : "Click when done");
		}
		else
		{
			setToolTipText("The game says this is done");
		}
	}

	private static final class Box extends JComponent
	{
		private final boolean done;
		private final boolean met;
		private final boolean current;

		Box(boolean done, boolean met, boolean current)
		{
			this.done = done;
			this.met = met;
			this.current = current;
			setOpaque(false);
			setPreferredSize(new Dimension(BOX + 2, BOX + 4));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			RoundRectangle2D box = new RoundRectangle2D.Float(1, 3, BOX, BOX, 4, 4);
			g.setColor(Theme.INK_DEEP);
			g.fill(box);
			g.setColor(done ? (met ? Theme.GREEN : Theme.TEAL) : current ? Theme.GOLD : Theme.STROKE);
			g.draw(box);
			if (done)
			{
				Path2D tick = new Path2D.Float();
				tick.moveTo(3.5f, 9f);
				tick.lineTo(6f, 11.5f);
				tick.lineTo(10.5f, 5.5f);
				g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.setColor(met ? Theme.GREEN : Theme.TEAL);
				g.draw(tick);
			}
			g.dispose();
		}
	}
}
