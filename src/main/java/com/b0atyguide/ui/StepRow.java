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

import com.b0atyguide.data.ItemRef;
import com.b0atyguide.data.Step;
import com.b0atyguide.data.Tag;
import com.b0atyguide.data.Target;
import com.b0atyguide.progress.Progress;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;

/**
 * One step: a checkbox, the guide's text verbatim, and whatever the pipeline
 * could attach to it.
 *
 * <p>The text is never rewritten. What the wiki says is what the player reads.
 */
class StepRow extends JPanel
{
	private static final Color CURRENT_BORDER = ColorScheme.BRAND_ORANGE;
	private static final Icon CHECKED_ICON = new CheckIcon(true);
	private static final Icon UNCHECKED_ICON = new CheckIcon(false);
	private static final Icon BULK_ICON = new CheckIcon(true, true);

	private static final int LEFT_PAD = 4;
	private static final int RIGHT_PAD = 8;
	private static final int DEPTH_INDENT = 12;

	private final Step step;
	private final Progress progress;

	private final JCheckBox checkBox = new JCheckBox();
	private final WrappedText textArea;
	private WrappedText detailArea;
	private boolean hovered;

	StepRow(
		Step step,
		Progress progress,
		BiConsumer<Step, Boolean> onToggle,
		Consumer<Step> onSelect,
		String filter,
		int fontSize)
	{
		this.step = step;
		this.progress = progress;

		final Font font = bodyFont(fontSize);

		setLayout(new BorderLayout(6, 0));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(4, indentFor(step), 4, RIGHT_PAD));

		checkBox.setOpaque(false);
		checkBox.setBorderPainted(false);
		checkBox.setFocusPainted(false);
		// A JCheckBox has its own margin around the icon; without clearing it the
		// box drifts right as the font grows.
		checkBox.setMargin(new Insets(0, 0, 0, 0));
		checkBox.setIcon(UNCHECKED_ICON);
		checkBox.setSelectedIcon(CHECKED_ICON);
		checkBox.setRolloverIcon(UNCHECKED_ICON);
		checkBox.setRolloverSelectedIcon(CHECKED_ICON);
		checkBox.setSelected(progress.isComplete(step.getId()));
		checkBox.addActionListener(e -> onToggle.accept(step, checkBox.isSelected()));

		textArea = new WrappedText(step.getText(), Color.WHITE);
		textArea.setFont(font);
		textArea.highlightMatches(filter);

		// The checkbox must sit on the first line of the text, not float in the
		// middle of a row whose height depends on how many lines that step wraps
		// to. NORTH pins it to the top; the inset centres it on the first line
		// and is derived from the font, so it stays put at any size.
		final JPanel checkHolder = new JPanel(new BorderLayout());
		checkHolder.setOpaque(false);
		checkHolder.setBorder(
			BorderFactory.createEmptyBorder(firstLineOffset(font), 0, 0, 0));
		checkHolder.add(checkBox, BorderLayout.NORTH);

		final JPanel body = new JPanel(new BorderLayout());
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.add(textArea, BorderLayout.NORTH);

		final String detail = buildDetail();
		if (!detail.isEmpty())
		{
			detailArea = new WrappedText(detail, ColorScheme.LIGHT_GRAY_COLOR);
			detailArea.setFont(bodyFont(fontSize - 2));
			body.add(detailArea, BorderLayout.SOUTH);
		}

		// A handful of steps link to a timestamped video or an image of what the
		// step means. The wiki renders them as links, so the panel should too
		// rather than dropping them.
		for (JLabel link : buildLinks(fontSize))
		{
			body.add(link, BorderLayout.SOUTH);
		}

		add(checkHolder, BorderLayout.WEST);
		add(body, BorderLayout.CENTER);

		// The whole row is the click target. A 13px box is a fiddly thing to hit
		// two thousand times, and there is nothing else a click on a step could
		// reasonably mean.
		//
		// Swing delivers a click to the deepest component that has listeners, and
		// it does not bubble, so the handler goes on every child that could be
		// under the cursor -- but never on the checkbox, which has its own and
		// would otherwise toggle twice.
		final MouseAdapter rowMouse = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (SwingUtilities.isRightMouseButton(e))
				{
					// Targeting a step you have not reached yet is still useful,
					// so it keeps a home rather than disappearing.
					onSelect.accept(step);
					return;
				}
				onToggle.accept(step, !progress.isComplete(step.getId()));
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setHovered(true);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				// Moving between the row's own children fires an exit; the
				// pointer has only really left when it is over none of them.
				setHovered(getMousePosition(true) != null);
			}
		};

		for (Component target : new Component[]{this, body, textArea, detailArea})
		{
			if (target != null)
			{
				target.addMouseListener(rowMouse);
				target.setCursor(new Cursor(Cursor.HAND_CURSOR));
			}
		}
		setToolTipText("Click to mark complete. Right-click to highlight it in game.");

		applyCompletionStyle();
	}

	/**
	 * Links the step carries: an explicit URL, or a bare YouTube id.
	 *
	 * <p>Only hosts the panel is willing to open produce a label. These come
	 * from a wiki page anyone can edit, and a link opens the player's browser
	 * wherever it points -- a stricter problem than an image, which is only
	 * shown.
	 */
	private List<JLabel> buildLinks(int fontSize)
	{
		final List<JLabel> links = new ArrayList<>();
		for (String url : step.getUrls())
		{
			final JLabel link = GuideLinks.link("Open link", url, url);
			if (link != null)
			{
				link.setFont(bodyFont(fontSize - 2));
				links.add(link);
			}
		}
		for (String videoId : step.getVideoIds())
		{
			final String url = GuideLinks.youTubeUrl(videoId);
			final JLabel link = GuideLinks.link("Watch this step", url, url);
			if (link != null)
			{
				link.setFont(bodyFont(fontSize - 2));
				links.add(link);
			}
		}
		return links;
	}

	/**
	 * Body text uses the platform's sans face rather than the pixel RuneScape
	 * one, which is designed for a single size and gets muddy when scaled up.
	 * This panel is read at a glance mid-task, so legibility wins over theme.
	 */
	private static Font bodyFont(int size)
	{
		return new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(size, 9));
	}

	private void setHovered(boolean hovered)
	{
		if (this.hovered == hovered)
		{
			return;
		}
		this.hovered = hovered;
		final Color background = hovered
			? ColorScheme.DARK_GRAY_HOVER_COLOR
			: ColorScheme.DARK_GRAY_COLOR;
		setBackground(background);
		if (textArea.getParent() != null)
		{
			textArea.getParent().setBackground(background);
		}
	}

	/** Vertical offset that centres the check icon on the first line of text. */
	private int firstLineOffset(Font font)
	{
		final int lineHeight = getFontMetrics(font).getHeight();
		return Math.max(0, (lineHeight - CheckIcon.SIZE) / 2);
	}

	private static int indentFor(Step step)
	{
		return LEFT_PAD + (step.getDepth() - 1) * DEPTH_INDENT;
	}

	/**
	 * The dimmed second line of a step: what it points at, how to answer the
	 * dialogue, and what it asks you to carry. Only the parts that apply.
	 */
	private String buildDetail()
	{
		final List<String> parts = new ArrayList<>();

		final Target target = step.getTarget();
		if (target != null && target.isHighlightable())
		{
			// A leading tilde marks a name the guide's wording implied but the
			// wiki never confirmed, so the reader knows not to trust it blindly.
			parts.add((target.isWikiBacked() ? "" : "~") + target.getName());
		}

		if (!step.getDialogue().isEmpty())
		{
			parts.add(describeDialogue());
		}

		if (step.getInventorySlots() != null)
		{
			parts.add(step.getInventorySlots() + " slots");
		}

		if (!step.getItems().isEmpty())
		{
			parts.add(describeItems());
		}

		for (Tag tag : step.getTags())
		{
			if (tag.getQuest() != null)
			{
				parts.add(tag.getQuest());
			}
			else if (tag.isDiary())
			{
				parts.add(tag.getTag());
			}
		}

		if (!step.getVideoIds().isEmpty())
		{
			parts.add("video");
		}

		return String.join(" · ", parts);
	}

	/** The guide's {@code (3,1)} chat options, as {@code chat 3-1}. */
	private String describeDialogue()
	{
		final StringBuilder chat = new StringBuilder("chat ");
		for (List<Integer> sequence : step.getDialogue())
		{
			for (int i = 0; i < sequence.size(); i++)
			{
				chat.append(sequence.get(i));
				if (i < sequence.size() - 1)
				{
					chat.append("-");
				}
			}
		}
		return chat.toString();
	}

	/**
	 * How many items the step lists, and how many of those the plugin can
	 * actually ring in a bank. The gap is the honest part: an unresolved item
	 * still reads fine in the step text, it just cannot be highlighted.
	 */
	private String describeItems()
	{
		int resolved = 0;
		for (ItemRef item : step.getItems())
		{
			if (item.isResolved())
			{
				resolved++;
			}
		}
		return step.getItems().size() + " items"
			+ (resolved > 0 ? " (" + resolved + " known)" : "");
	}

	private void applyCompletionStyle()
	{
		final boolean complete = progress.isComplete(step.getId());
		final boolean manual = progress.isManual(step.getId());

		textArea.setForeground(complete ? ColorScheme.LIGHT_GRAY_COLOR : Color.WHITE);

		// A step ticked by "complete bank" is drawn hollow, so an accidental
		// bulk tick stays visibly different from work the player ticked off
		// themselves -- which is the whole point of remembering the difference.
		checkBox.setSelectedIcon(manual ? CHECKED_ICON : BULK_ICON);
		checkBox.setRolloverSelectedIcon(manual ? CHECKED_ICON : BULK_ICON);
		checkBox.setToolTipText(complete && !manual
			? "Completed with the whole bank"
			: "Mark this step complete");
	}

	void onProgressChanged(Step current)
	{
		final boolean complete = progress.isComplete(step.getId());
		if (checkBox.isSelected() != complete)
		{
			checkBox.setSelected(complete);
		}
		applyCompletionStyle();

		final boolean isCurrent = current != null && current.getId().equals(step.getId());
		setBorder(BorderFactory.createCompoundBorder(
			isCurrent
				? BorderFactory.createMatteBorder(0, 2, 0, 0, CURRENT_BORDER)
				: BorderFactory.createEmptyBorder(0, 2, 0, 0),
			BorderFactory.createEmptyBorder(4, indentFor(step) - 2, 4, RIGHT_PAD)));
	}

	/**
	 * A checkbox that shows completion, not just state.
	 *
	 * <p>Drawn rather than themed so it looks the same on every look and feel,
	 * and so "done" is a solid green block that is obvious while scrolling a
	 * list of 2,900 steps. The hollow variant marks a step completed in bulk.
	 */
	private static final class CheckIcon implements Icon
	{
		private static final int SIZE = 13;

		private final boolean checked;
		private final boolean hollow;

		CheckIcon(boolean checked)
		{
			this(checked, false);
		}

		CheckIcon(boolean checked, boolean hollow)
		{
			this.checked = checked;
			this.hollow = hollow;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			final Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(
					RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				if (!checked)
				{
					g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
					g2.fillRoundRect(x, y, SIZE, SIZE, 4, 4);
					g2.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
					g2.drawRoundRect(x, y, SIZE, SIZE, 4, 4);
					return;
				}

				if (hollow)
				{
					g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
					g2.fillRoundRect(x, y, SIZE, SIZE, 4, 4);
					g2.setColor(ColorScheme.PROGRESS_COMPLETE_COLOR);
					g2.drawRoundRect(x, y, SIZE, SIZE, 4, 4);
					g2.setStroke(new BasicStroke(2f));
				}
				else
				{
					g2.setColor(ColorScheme.PROGRESS_COMPLETE_COLOR);
					g2.fillRoundRect(x, y, SIZE, SIZE, 4, 4);
					g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
					g2.setStroke(new BasicStroke(2f));
				}
				g2.drawLine(x + 3, y + 6, x + 5, y + 9);
				g2.drawLine(x + 5, y + 9, x + 10, y + 3);
			}
			finally
			{
				g2.dispose();
			}
		}

		@Override
		public int getIconWidth()
		{
			return SIZE + 1;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE + 1;
		}
	}
}
