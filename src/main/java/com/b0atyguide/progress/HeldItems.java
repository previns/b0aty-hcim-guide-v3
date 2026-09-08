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
package com.b0atyguide.progress;

import com.b0atyguide.data.ItemRef;
import com.b0atyguide.data.Step;
import java.util.Map;

/**
 * Whether the player is holding what a step told them to get.
 *
 * <p>Pure: it takes a count of what is carried rather than reading the client,
 * so the rule can be tested without a game. The reading is the caller's job,
 * and has to happen on the client thread.
 */
public final class HeldItems
{
	private HeldItems()
	{
	}

	/**
	 * Whether every item the step asks for is held, in the numbers it asks for.
	 *
	 * <p>A requirement is met by <em>any one</em> of its ids, and their counts
	 * add up: "Pickaxe" carries every tier, so three bronze and two iron is
	 * five pickaxes. That matches how the withdraw list already reads.
	 *
	 * <p>Answers false for a step with nothing to hold, so an empty list can
	 * never tick anything.
	 *
	 * @param held item id to how many are carried, inventory and worn together
	 */
	public static boolean satisfied(Step step, Map<Integer, Integer> held)
	{
		if (step == null || !step.isAcquires() || step.getItems().isEmpty())
		{
			return false;
		}

		for (ItemRef item : step.getItems())
		{
			if (item.getIds().isEmpty())
			{
				// Unknown ids cannot be checked, and guessing here would tick a
				// step the player has not done.
				return false;
			}

			if (heldCount(item, held) < item.getCount())
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * How many of this requirement the player holds.
	 *
	 * <p>A requirement is met by any of its ids <em>together</em>: "Pickaxe"
	 * carries every tier, so three bronze and two iron is five pickaxes.
	 *
	 * <p>Counted, not merely present. The bank used to ask only whether an id
	 * appeared at all, so a step wanting 454 arrow shafts stopped asking the
	 * moment the player had one.
	 */
	public static int heldCount(ItemRef item, Map<Integer, Integer> held)
	{
		int carried = 0;
		for (Integer id : item.getIds())
		{
			final Integer some = held.get(id);
			if (some != null)
			{
				carried += some;
			}
		}
		return carried;
	}
}
