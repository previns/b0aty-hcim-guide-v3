/*
 * Copyright (c) 2026, Previn <https://github.com/previns>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.b0atyguide.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * A short list of the things this plugin does that a player would not find on
 * their own.
 *
 * <p>Most of the plugin explains itself: the panel is visibly a checklist, and
 * the highlights appear without being asked for. A few of the best parts do
 * not. Nobody types {@code bank150} into a bank search box on the chance that
 * something happens, and nobody right-clicks a step to see where it is. Those
 * are worth saying once.
 *
 * <p>Sits at the top of the step list rather than in the header, so it scrolls
 * away instead of costing height forever, and it can be dismissed for good.
 */
class FeaturesPanel extends JPanel
{
	/**
	 * Kept deliberately short. A list nobody finishes reading teaches nothing,
	 * so this is only the things that are genuinely not discoverable.
	 */
	private static final String[][] FEATURES = {
		{"Search a bank in game",
			"Type bank150 (or bank 150) in the bank search to see exactly what "
				+ "that bank needs. Needs the Bank Tags plugin on."},
		{"See what you are missing",
			"Items a bank asks for that you are not carrying are ringed in the "
				+ "bank and inventory."},
		{"Right-click any step",
			"Highlights it in the world without ticking it, so you can look "
				+ "ahead. Left-click anywhere on a step to tick it."},
		{"Tick a whole bank",
			"The checkbox on a bank header ticks all of it. Steps you ticked "
				+ "by hand are remembered, so pressing it by accident costs "
				+ "nothing."},
		{"Some steps tick themselves",
			"Quests, achievement diary tasks and skill levels complete on "
				+ "their own. Everything else is yours to tick."},
		{"Watch the episode",
			"The link under the search box opens the video for the bank you "
				+ "are on."},
	};

	private final JPanel body = new JPanel();
	private final JLabel header = new JLabel();
	private boolean expanded;

	/**
	 * @param fontSize  the panel's step font size, so this matches it
	 * @param onDismiss called when the player closes the list for good
	 */
	FeaturesPanel(IntSupplier fontSize, Consumer<Boolean> onDismiss, boolean startExpanded)
	{
		this.expanded = startExpanded;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		header.setForeground(GuideLinks.BLUE);
		header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(fontSize.getAsInt(), 9)));
		header.setCursor(new Cursor(Cursor.HAND_CURSOR));
		header.setToolTipText("Things you would not find on your own");
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				setExpanded(!FeaturesPanel.this.expanded);
			}
		});
		add(header, BorderLayout.NORTH);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

		for (String[] feature : FEATURES)
		{
			body.add(row(feature[0], feature[1], fontSize.getAsInt()));
		}

		final JLabel dismiss = new JLabel("Hide this");
		dismiss.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		dismiss.setFont(new Font(Font.SANS_SERIF, Font.PLAIN,
			Math.max(fontSize.getAsInt() - 2, 9)));
		dismiss.setCursor(new Cursor(Cursor.HAND_CURSOR));
		dismiss.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		dismiss.setToolTipText("Turn it back on under Panel in the plugin settings");
		dismiss.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				onDismiss.accept(Boolean.FALSE);
			}
		});
		body.add(dismiss);

		add(body, BorderLayout.CENTER);
		setExpanded(startExpanded);
	}

	private void setExpanded(boolean expand)
	{
		this.expanded = expand;
		header.setText((expand ? "▼ " : "▶ ") + "What this plugin can do");
		body.setVisible(expand);
		revalidate();
		repaint();
	}

	/** One feature: what it is, and how to actually use it. */
	private static JPanel row(String title, String detail, int fontSize)
	{
		final JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));

		final JLabel name = new JLabel(title);
		name.setForeground(Color.WHITE);
		name.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(fontSize - 1, 9)));

		// A wrapping text area rather than an HTML label: the sidebar is
		// resizable, and an HTML label needs its wrap width baked into the
		// markup, which is what clipped the step rows.
		final WrappedText text = new WrappedText(detail, ColorScheme.LIGHT_GRAY_COLOR);
		text.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(fontSize - 2, 9)));

		panel.add(name, BorderLayout.NORTH);
		panel.add(text, BorderLayout.CENTER);
		return panel;
	}
}
