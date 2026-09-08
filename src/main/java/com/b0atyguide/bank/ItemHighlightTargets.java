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

import com.b0atyguide.data.QuestHelperSteps;
import com.b0atyguide.data.Step;
import com.b0atyguide.data.Target;
import java.util.HashSet;
import java.util.Set;

/**
 * The immutable guide data behind item rings, indexed once per selected step.
 *
 * <p>A WidgetItemOverlay is called for every visible slot on every frame. Walking
 * every requirement and its alternate item IDs for each slot multiplied the same
 * work by the bank's size and frame rate. Only the selected instruction/step can
 * change this membership; inventory quantities and config remain live decisions
 * in the overlay. Identity matters: two branches may share text and coordinates
 * but ask for different items.
 */
final class ItemHighlightTargets
{
	private QuestHelperSteps.Instruction instruction;
	private Step step;
	private final Set<Integer> questIds = new HashSet<>();
	private final Set<Integer> stepIds = new HashSet<>();

	boolean neededByQuest(QuestHelperSteps.Instruction selected, int itemId)
	{
		if (instruction != selected)
		{
			instruction = selected;
			questIds.clear();
			if (selected != null)
			{
				for (QuestHelperSteps.Need need : selected.getItems())
				{
					questIds.addAll(need.getIds());
				}
			}
		}
		// Keep the icon check exactly as before, including item zero: it is
		// a real item ID, not an empty inventory slot.
		return selected != null
			&& (selected.getIcon() == itemId || questIds.contains(itemId));
	}

	boolean isStepItem(Step selected, int itemId)
	{
		if (step != selected)
		{
			step = selected;
			stepIds.clear();
			final Target target = selected == null ? null : selected.getTarget();
			if (target != null && Target.KIND_ITEM.equals(target.getKind()))
			{
				stepIds.addAll(target.getIds());
			}
		}
		return stepIds.contains(itemId);
	}

	void clear()
	{
		instruction = null;
		step = null;
		questIds.clear();
		stepIds.clear();
	}
}
