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
import com.b0atyguide.overlay.SceneTracker;
import com.b0atyguide.overlay.Ring;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.api.gameval.InterfaceID;
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
	private final ItemHighlightTargets targets = new ItemHighlightTargets();

	@Inject
	private WithdrawTracker tracker;

	@Inject
	private SceneTracker scene;

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
		// Three different things, drawn the same way: something still to
		// withdraw, the item this step travels with, and what the quest is
		// waiting on. Only the first is "missing" -- the player usually has the
		// other two -- so they are separate questions.
		final boolean travel = config.showTeleportItem() && tracker.isTeleport(itemId);

		// What Quest Helper would ring for the step the quest is on. In the
		// inventory, where the player is about to click it, and only for the
		// instruction in force -- the rest of the quest's shopping list is not
		// what they need right now.
		final boolean quest = config.showQuestSteps()
			&& targets.neededByQuest(scene.getInstruction(), itemId);

		// And the item the step itself is about. "Read the Ardougne Teleport
		// Scroll in your inventory" resolves to the scroll and nothing was
		// marking it: an item target is not scenery, so the model outline had
		// nothing to draw on and the bank ring only ever looked at the bank.
		final boolean itself = config.highlightWithdrawItems()
			&& targets.isStepItem(scene.getStep(), itemId);

		// Only in the bank. Ringing a withdraw item in the inventory too means
		// the ones already taken out keep glowing while the player works down
		// the list, which reads as "still missing" when it is the opposite.
		final boolean needed = config.highlightWithdrawItems()
			&& tracker.isMissing(itemId)
			&& inTheBank(item);

		if (!travel && !needed && !quest && !itself)
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

	/** Release selected guide data on profile changes and plugin shutdown. */
	public void clear()
	{
		targets.clear();
	}

	/**
	 * Whether this slot is in the bank rather than the inventory.
	 *
	 * <p>One overlay draws over both, so the container has to be asked. The
	 * teleport item is the other way round -- that one belongs in the
	 * inventory, where the player will click it.
	 */
	private static boolean inTheBank(WidgetItem item)
	{
		final Widget widget = item.getWidget();
		return widget != null
			&& WidgetUtil.componentToInterface(widget.getId()) == InterfaceID.BANKMAIN;
	}
}
