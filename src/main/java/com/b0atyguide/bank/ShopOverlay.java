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
package com.b0atyguide.bank;

import com.b0atyguide.B0atyGuideConfig;
import com.b0atyguide.data.ItemRef;
import com.b0atyguide.data.Step;
import com.b0atyguide.data.Target;
import com.b0atyguide.overlay.Ring;
import com.b0atyguide.overlay.SceneTracker;
import com.b0atyguide.progress.HeldItems;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Rings the thing to buy, in an open shop.
 *
 * <p>"Buy an Air Staff from Zaff" already highlights Zaff and walks you to him,
 * and then the shop opens and you are on your own in a grid of forty icons.
 * This marks the one the step asked for.
 *
 * <p>Matched by item id, so it is the same list the bank and inventory already
 * ring, and a step whose items did not resolve marks nothing rather than
 * guessing at a name.
 */
public class ShopOverlay extends Overlay
{
	/** The shop grid's own slots are tight, so the ring sits close. */
	private static final int PADDING = 1;
	private static final int[] SHOP_WIDGETS = {
		InterfaceID.Shopmain.ITEMS, InterfaceID.Shopmain.UNIVERSE
	};

	private final Client client;
	private final B0atyGuideConfig config;
	private final SceneTracker tracker;
	private final WithdrawTracker withdrawTracker;


	@Inject
	ShopOverlay(Client client, B0atyGuideConfig config, SceneTracker tracker,
		WithdrawTracker withdrawTracker)
	{
		this.client = client;
		this.config = config;
		this.tracker = tracker;
		this.withdrawTracker = withdrawTracker;
		setPosition(OverlayPosition.DYNAMIC);
		// The shop is a widget, so anything below this layer is drawn under it.
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.highlightShopItems())
		{
			return null;
		}

		// ITEMS is the stock grid itself. UNIVERSE is the whole interface and
		// is checked second: rooting there and recursing did not reach the
		// slots, which is why the shop rang nothing at all.
		for (int component : SHOP_WIDGETS)
		{
			final Widget root = client.getWidget(component);
			if (root != null && !root.isHidden())
			{
				// Most frames have no shop. Resolve requirements only after the
				// visible widget check, rather than allocating a set every frame.
				final Set<Integer> wanted = wantedIds();
				if (!wanted.isEmpty())
				{
					ring(graphics, root, wanted);
				}
				return null;
			}
		}
		return null;
	}

	/**
	 * Every item id the current step means to come away with.
	 *
	 * <p>Both the list form ("Buy 2x Tinderbox, Cake Tin") and the single form,
	 * where the extraction made the item the step's target.
	 */
	private Set<Integer> wantedIds()
	{
		return wantedIds(tracker.getStep(), withdrawTracker.carried());
	}

	static Set<Integer> wantedIds(Step step, Map<Integer, Integer> held)
	{
		if (step == null)
		{
			return java.util.Collections.emptySet();
		}

		final Set<Integer> ids = new HashSet<>();
		for (ItemRef item : step.getItems())
		{
			// Only what is still owed. A ring that stays on after the item is
			// bought reads as "buy another", and a step asking for three things
			// kept marking all three until the player left the shop.
			if (HeldItems.heldCount(item, held) < item.getCount())
			{
				ids.addAll(item.getIds());
			}
		}

		final Target target = step.getTarget();
		// The parsed purchase list is authoritative. Re-adding its target here
		// both rings goods already bought and can add a truncated name's item.
		if (step.getItems().isEmpty() && target != null && Target.KIND_ITEM.equals(target.getKind()))
		{
			ids.addAll(target.getIds());
		}
		return ids;
	}

	/**
	 * Walk the shop's widget tree, ringing any slot holding a wanted item.
	 *
	 * <p>Recursive because the stock grid is nested a few levels down and its
	 * depth is not the same in every shop layout. Cheaper than it sounds: a
	 * shop is a few dozen widgets, and this only runs while one is open.
	 */
	private void ring(Graphics2D graphics, Widget widget, Set<Integer> wanted)
	{
		if (widget == null || widget.isHidden())
		{
			return;
		}

		if (wanted.contains(widget.getItemId()))
		{
			final Rectangle bounds = widget.getBounds();
			if (bounds != null && !bounds.isEmpty())
			{
				Ring.draw(graphics, bounds, config.highlightColor(), PADDING);
			}
		}

		ringChildren(graphics, widget.getDynamicChildren(), wanted);
		ringChildren(graphics, widget.getStaticChildren(), wanted);
		ringChildren(graphics, widget.getNestedChildren(), wanted);
	}

	/**
	 * Every child of a widget, from all three lists.
	 *
	 * <p>Returning only the first non-empty list was wrong: a shop nests its
	 * stock under a static parent that also holds dynamic children, so
	 * stopping at one of them walked past the slots entirely.
	 */
	private void ringChildren(Graphics2D graphics, Widget[] children, Set<Integer> wanted)
	{
		if (children == null)
		{
			return;
		}
		for (Widget child : children)
		{
			ring(graphics, child, wanted);
		}
	}
}
