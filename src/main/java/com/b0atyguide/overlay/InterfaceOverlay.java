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
package com.b0atyguide.overlay;

import com.b0atyguide.B0atyGuideConfig;
import com.b0atyguide.data.QuestHelperSteps;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
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
		return null;
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
