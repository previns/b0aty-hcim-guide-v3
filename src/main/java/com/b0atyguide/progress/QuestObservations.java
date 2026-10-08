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
package com.b0atyguide.progress;

import com.b0atyguide.data.QuestHelperSteps;
import com.b0atyguide.data.QuestHelperSteps.Dialog;
import com.b0atyguide.data.QuestHelperSteps.Instruction;
import com.b0atyguide.data.QuestHelperSteps.Memory;
import com.b0atyguide.data.QuestHelperSteps.Requirement;
import com.b0atyguide.data.QuestHelperSteps.Vars;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/** Observed Quest Helper conditions; persistent facts are scoped to an account by the caller. */
public final class QuestObservations
{
	private final Map<String, String> saved = new HashMap<>();
	private final Map<String, Dialog> dialogs = new HashMap<>();
	private final Set<String> seenDialogs = new HashSet<>();
	private final Set<String> passedOnce = new HashSet<>();
	private final List<Requirement> observations = new ArrayList<>();
	private String scope;
	private QuestHelperSteps helper;
	private boolean dirty;

	public void load(Map<String, String> values)
	{
		saved.clear();
		if (values != null) { saved.putAll(values); }
		bind(null, null);
		dirty = false;
	}

	public void bind(String key, QuestHelperSteps selected)
	{
		if (selected == helper && java.util.Objects.equals(scope, key)) { return; }
		scope = key;
		helper = selected;
		dialogs.clear();
		seenDialogs.clear();
		passedOnce.clear();
		observations.clear();
		if (selected != null)
		{
			for (Instruction instruction : selected.getSteps().values()) { collect(instruction); }
		}
	}

	private void collect(Instruction instruction)
	{
		if (instruction.getWhen() != null)
		{
			instruction.getWhen().visit(condition ->
			{
				if (condition.getDialog() != null)
				{
					Dialog dialog = condition.getDialog();
					dialogs.put(dialog.key(), dialog);
				}
				if (condition.getMemory() != null || condition.getOnce() != null) { observations.add(condition); }
			});
		}
		for (Instruction child : instruction.getWhenIn()) { collect(child); }
	}

	public void dialog(String message, String playerName)
	{
		for (Map.Entry<String, Dialog> entry : dialogs.entrySet())
		{
			Dialog condition = entry.getValue();
			if (condition.matches(message, playerName)) { seenDialogs.add(entry.getKey()); }
			else if (condition.isActive()) { seenDialogs.remove(entry.getKey()); }
		}
	}

	public void dialogueClosed()
	{
		for (Map.Entry<String, Dialog> entry : dialogs.entrySet())
		{
			if (entry.getValue().isActive()) { seenDialogs.remove(entry.getKey()); }
		}
	}

	/** Visit all listeners, including branches that short-circuit before their condition. */
	public void observe(WorldPoint at, Map<Integer, Integer> held, Vars vars)
	{
		for (Requirement condition : observations)
		{
			Memory memory = condition.getMemory() == null ? condition.getOnce() : condition.getMemory();
			if (memory.getWhen() == null || !memory.getWhen().holds(at, held, vars)) { continue; }
			if (condition.getMemory() == null) { passedOnce.add(memory.getKey()); }
			else
			{
				String key = scope + ":" + memory.getKey();
				String old = saved.put(key, memory.getValue());
				dirty |= !memory.getValue().equals(old);
			}
		}
	}

	public boolean remembered(String key, String value, boolean persistent)
	{
		return persistent ? value.equals(saved.get(scope + ":" + key)) : passedOnce.contains(key);
	}

	public boolean dialogSeen(Dialog dialog) { return seenDialogs.contains(dialog.key()); }
	public boolean isDirty() { return dirty; }
	public Map<String, String> snapshot() { dirty = false; return new HashMap<>(saved); }
}
