/*
 * Copyright (c) 2026, Previn <https://github.com/previns>
 * Copyright (c) 2020, Zoinkwiz and Twinkle (cyclic-widget solver)
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
package com.b0atyguide.overlay;

import com.b0atyguide.B0atyGuideConfig;
import com.b0atyguide.data.QuestHelperSteps;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Marks the part of an open interface the quest step is about.
 *
 * <p>While a menu is up there is nothing in the world to outline, and the step's
 * sentence is all the player gets: "use the right tool on the spring, the middle
 * tool on the Safety switch, and the left tool on the gear" is a fine
 * instruction and a poor one to follow with three unlabelled tools on screen.
 * Quest Helper rings the tool. This does the same, from the same data.
 *
 * <p>The traversal is its {@code WidgetHighlight}: fetch the interface, descend
 * into one named child if the mark has one, otherwise walk the children when it
 * asks for that, and ring whatever passes all four of its filters. Its own
 * children and its static children both, because an interface built by a script
 * puts its parts in one or the other and quest-helper reads both.
 */
public class InterfaceOverlay extends Overlay
{
	/** An interface slot is tight, so the ring sits close. */
	private static final int PADDING = 1;

	/**
	 * How deep to walk.
	 *
	 * <p>Quest Helper recurses without a bound because its marks are hand-made
	 * and shallow. This runs over data, and a malformed mark that walks a deep
	 * interface every frame is a frame-rate bug rather than a wrong highlight.
	 */
	private static final int MAX_DEPTH = 6;

	private final Client client;
	private final B0atyGuideConfig config;
	private final SceneTracker tracker;


	@Inject
	InterfaceOverlay(Client client, B0atyGuideConfig config, SceneTracker tracker)
	{
		this.client = client;
		this.config = config;
		this.tracker = tracker;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.highlightInterfaces())
		{
			return null;
		}
		final QuestHelperSteps.Instruction instruction = tracker.getInstruction();
		if (instruction == null)
		{
			return null;
		}

		for (QuestHelperSteps.WidgetMark mark : instruction.getWidgets())
		{
			final Widget root = client.getWidget(mark.getId());
			if (root == null || root.isHidden())
			{
				continue;
			}
			mark(graphics, root, mark, 0);
		}
		for (QuestHelperSteps.WidgetPuzzle puzzle : instruction.getWidgetPuzzles())
		{
			drawPuzzle(graphics, puzzle);
		}
		for (QuestHelperSteps.CombinationLock lock : instruction.getCombinationLocks())
		{
			drawCombinationLock(graphics, lock);
		}
		return null;
	}

	/**
	 * The dials of a combination lock: which arrow, and how many clicks.
	 *
	 * <p>What Quest Helper draws for the chest in the Ribbiting Tale and the
	 * door in The Final Dawn. The arrows are children of the lock widget rather
	 * than components of their own, and each dial's current letter is a client
	 * int, not a varbit.
	 */
	private void drawCombinationLock(Graphics2D graphics, QuestHelperSteps.CombinationLock lock)
	{
		if (!lock.isValid())
		{
			return;
		}
		Widget submit = client.getWidget(lock.getSubmit());
		Widget parent = client.getWidget(lock.getParent());
		if (submit == null || submit.isHidden() || parent == null || parent.isHidden())
		{
			return;
		}
		// Nothing partial: every arrow has to be on screen before any is drawn,
		// so a half-loaded interface cannot produce half an answer.
		for (QuestHelperSteps.Dial dial : lock.getCycles())
		{
			final int current = client.getVarcIntValue(dial.getVarc());
			if (current < 0 || current >= lock.getSize())
			{
				return;
			}
			if (parent.getChild(dial.getDown()) == null || parent.getChild(dial.getUp()) == null)
			{
				return;
			}
		}

		boolean solved = true;
		for (QuestHelperSteps.Dial dial : lock.getCycles())
		{
			final int current = client.getVarcIntValue(dial.getVarc());
			final int child = dial.arrow(current, lock.getSize());
			if (child < 0)
			{
				continue;
			}
			solved = false;
			final Widget arrow = parent.getChild(child);
			if (arrow == null || arrow.isHidden())
			{
				continue;
			}
			markLeaf(graphics, arrow);
			final Rectangle bounds = arrow.getBounds();
			if (bounds == null || bounds.isEmpty())
			{
				continue;
			}
			drawCount(graphics, bounds, Integer.toString(dial.clicks(current, lock.getSize())));
		}
		if (solved)
		{
			markLeaf(graphics, submit);
		}
	}

	private void drawPuzzle(Graphics2D graphics, QuestHelperSteps.WidgetPuzzle puzzle)
	{
		if (puzzle.getCycles().size() < 2 || puzzle.getCycles().size() > 8 || puzzle.getSubmit() <= 0)
		{
			return;
		}
		Widget submit = client.getWidget(puzzle.getSubmit());
		if (submit == null || submit.isHidden())
		{
			return;
		}
		// Require the complete visible interface before drawing any partial hint.
		for (QuestHelperSteps.WidgetCycle cycle : puzzle.getCycles())
		{
			if (cycle.getVarbit() < 0)
			{
				return;
			}
			Widget left = client.getWidget(cycle.getLeft());
			Widget right = client.getWidget(cycle.getRight());
			if (left == null || right == null || left.isHidden() || right.isHidden()
				|| cycle.button(client.getVarbitValue(cycle.getVarbit())) < 0)
			{
				return;
			}
		}
		boolean solved = true;
		for (QuestHelperSteps.WidgetCycle cycle : puzzle.getCycles())
		{
			int current = client.getVarbitValue(cycle.getVarbit());
			int button = cycle.button(current);
			if (button == 0)
			{
				continue;
			}
			solved = false;
			Widget widget = client.getWidget(button);
			markLeaf(graphics, widget);
			Rectangle bounds = widget.getBounds();
			if (bounds == null || bounds.isEmpty())
			{
				continue;
			}
			drawCount(graphics, bounds, Integer.toString(cycle.distance(current)));
		}
		if (solved)
		{
			markLeaf(graphics, submit);
		}
	}

	/** Between the control and its count, so the two never touch. */
	private static final int COUNT_GAP = 4;

	/**
	 * How many clicks a control needs, written beside it rather than on it.
	 *
	 * <p>Drawn in the middle of the arrow it was barely legible: the arrow is a
	 * pale graphic with its own detail, and a thin glyph on top of it competes
	 * with the letter the dial is showing. So it goes outside, to the left,
	 * bold, over a dark backing that keeps it readable against whatever the
	 * interface puts behind it -- and to the right instead when the control is
	 * close enough to the screen edge that the left would be clipped.
	 */
	private void drawCount(Graphics2D graphics, Rectangle bounds, String count)
	{
		final Font previous = graphics.getFont();
		graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
		final FontMetrics metrics = graphics.getFontMetrics();
		final int width = metrics.stringWidth(count);

		int x = bounds.x - COUNT_GAP - width;
		if (x < 0)
		{
			x = bounds.x + bounds.width + COUNT_GAP;
		}
		// Centred on the control by the glyphs themselves, not by the line box,
		// which is taller than the digits and would sit them low.
		final int y = bounds.y + (bounds.height + metrics.getAscent() - metrics.getDescent()) / 2;

		graphics.setColor(Color.BLACK);
		graphics.drawString(count, x + 1, y + 1);
		graphics.setColor(config.highlightColor());
		graphics.drawString(count, x, y);
		graphics.setFont(previous);
	}

	private void mark(Graphics2D graphics, Widget widget,
		QuestHelperSteps.WidgetMark wanted, int depth)
	{
		if (widget == null || depth > MAX_DEPTH)
		{
			return;
		}

		// Step into the named child, once, and mark that.
		//
		// Quest Helper has two of these and they do not agree. WidgetHighlight
		// re-enters itself with the same index and so keeps descending until a
		// widget has no children left; WidgetStep takes getChild() once and
		// highlights the result. Every child index this plugin ships comes from
		// a WidgetStep -- WidgetHighlight's three-int form appears nowhere in
		// quest-helper -- so this follows WidgetStep. Descending repeatedly
		// walked past the tab and rang whatever leaf it landed on.
		final Integer child = wanted.getChild();
		if (child != null)
		{
			final Widget[] children = widget.getChildren();
			if (children != null && child >= 0 && child < children.length)
			{
				markLeaf(graphics, children[child]);
			}
			return;
		}

		if (wanted.searchesChildren())
		{
			walk(graphics, widget.getChildren(), wanted, depth);
			walk(graphics, widget.getStaticChildren(), wanted, depth);
		}

		if (widget.isHidden() || !wanted.accepts(
			widget.getItemId(), widget.getModelId(), widget.getText(), widget.getName()))
		{
			return;
		}
		markLeaf(graphics, widget);
	}

	/**
	 * Ring one widget, with no further questions asked of it.
	 *
	 * <p>Used where the mark named the component outright -- a child index
	 * points at one thing, and the filters are for searching a subtree rather
	 * than for second-guessing a direct reference.
	 */
	private void markLeaf(Graphics2D graphics, Widget widget)
	{
		if (widget == null || widget.isHidden())
		{
			return;
		}
		final Rectangle bounds = widget.getBounds();
		if (bounds == null || bounds.isEmpty())
		{
			return;
		}
		Ring.draw(graphics, bounds, config.highlightColor(), PADDING);
	}

	private void walk(Graphics2D graphics, Widget[] children,
		QuestHelperSteps.WidgetMark wanted, int depth)
	{
		if (children == null)
		{
			return;
		}
		for (Widget child : children)
		{
			mark(graphics, child, wanted, depth + 1);
		}
	}
}
