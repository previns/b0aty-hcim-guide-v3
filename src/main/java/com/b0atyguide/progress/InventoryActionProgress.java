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

import com.b0atyguide.data.InventoryAction;
import com.b0atyguide.data.ItemRef;
import com.b0atyguide.data.Step;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;

/** Proves recipe production from successive inventory snapshots, never possession alone. */
@Singleton
public final class InventoryActionProgress
{
	private Step watched;
	private Map<Integer, Integer> previous = Collections.emptyMap();
	private long produced;
	private boolean ready;

	public void clear()
	{
		watched = null;
		previous = Collections.emptyMap();
		produced = 0;
		ready = false;
	}

	public boolean observe(Step step, Map<Integer, Integer> inventory, boolean banking)
	{
		InventoryAction action = step == null ? null : step.getInventoryAction();
		if (step != watched)
		{
			clear();
			watched = step;
		}
		if (action == null || !action.isRecipe() || action.getProduced() == null
			|| action.getConsumed().isEmpty() || inventory == null)
		{
			ready = false;
			return false;
		}
		if (banking)
		{
			// Depositing ingredients and withdrawing a product is not crafting.
			ready = false;
			previous = Collections.emptyMap();
			return false;
		}
		if (ready)
		{
			long batches = Math.max(0L, (long) HeldItems.heldCount(action.getProduced(), inventory)
				- HeldItems.heldCount(action.getProduced(), previous)) / action.getProduced().getCount();
			for (ItemRef input : action.getConsumed())
			{
				long spent = (long) HeldItems.heldCount(input, previous) - HeldItems.heldCount(input, inventory);
				batches = Math.min(batches, Math.max(0L, spent) / input.getCount());
			}
			produced += batches;
		}
		previous = new HashMap<>(inventory);
		ready = true;
		return finished(step);
	}

	public boolean finished(Step step)
	{
		return step != null && watched == step && step.getInventoryAction() != null
			&& produced >= step.getInventoryAction().getCount();
	}
}
