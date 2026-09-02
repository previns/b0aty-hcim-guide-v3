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
import com.b0atyguide.overlay.Ring;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Rings items in the bank that the current bank's withdraw list asks for and
 * the player is not carrying.
 *
 * <p>Only ever draws on things that are missing. Marking everything on the list
 * would highlight most of the bank tab and say nothing; marking only the gap is
 * the useful signal, and it disappears as the gap closes.
 */
public class WithdrawOverlay extends WidgetItemOverlay
{
	private static final int PADDING = 1;

	@Inject
	private WithdrawTracker tracker;

	@Inject
	private B0atyGuideConfig config;

	@Inject
	WithdrawOverlay()
	{
		showOnBank();
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem item)
	{
		// Two different things, drawn the same way: something still to withdraw,
		// and the item this step travels with. The second is not "missing" --
		// the player usually has it -- so it is a separate question.
		final boolean travel = config.showTeleportItem() && tracker.isTeleport(itemId);
		final boolean needed = config.highlightWithdrawItems() && tracker.isMissing(itemId);
		if (!travel && !needed)
		{
			return;
		}

		final Rectangle bounds = item.getCanvasBounds();
		if (bounds == null)
		{
			return;
		}

		Ring.draw(graphics, bounds, config.highlightColor(), PADDING);
	}
}
