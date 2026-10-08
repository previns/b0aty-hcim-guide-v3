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
package com.b0atyguide.data;

import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/** Source-resolved inventory operands, separate from products and supplies. */
public final class InventoryAction
{
	private String kind;
	private String source;
	private int count;
	private List<Integer> highlightIds;
	private List<ItemRef> consumed;
	private ItemRef produced;

	/** Reject ambiguous recipe operands before they can drive completion. */
	public boolean isValid()
	{
		if (!("interact".equals(kind) || isRecipe()) || getHighlightIds().isEmpty()) { return false; }
		Set<Integer> highlights = new HashSet<>();
		for (Integer id : getHighlightIds())
		{
			if (id == null || id < 0 || !highlights.add(id)) { return false; }
		}
		if (!isRecipe()) { return getConsumed().isEmpty() && produced == null; }
		if (count < 1 || source == null || source.isEmpty() || produced == null
			|| !produced.isResolved() || getConsumed().isEmpty()) { return false; }
		Set<Integer> inputs = new HashSet<>();
		for (ItemRef input : getConsumed())
		{
			if (input == null || !input.isResolved()) { return false; }
			for (Integer id : input.getIds())
			{
				if (id == null || id < 0 || !inputs.add(id)) { return false; }
			}
		}
		Set<Integer> outputs = new HashSet<>();
		for (Integer id : produced.getIds())
		{
			if (id == null || id < 0 || inputs.contains(id) || !outputs.add(id)) { return false; }
		}
		return highlights.equals(inputs);
	}

	public boolean isRecipe() { return "recipe".equals(kind); }
	public String getSource() { return source; }
	public int getCount() { return Math.max(1, count); }
	public List<Integer> getHighlightIds() { return highlightIds == null ? Collections.emptyList() : highlightIds; }
	public List<ItemRef> getConsumed() { return consumed == null ? Collections.emptyList() : consumed; }
	public ItemRef getProduced() { return produced; }
}
