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

/**
 * How a step says to get somewhere: "Ardy Cloak -&gt; CKS".
 *
 * <p>The guide names the item as well as the destination, and knowing which
 * one to reach for is half the instruction -- the cloak has five tiers and the
 * step never says which you own.
 *
 * <p>{@link #getIds()} is empty for the methods that are not items at all:
 * fairy rings, minecarts, the house teleport. Those still carry a
 * {@link #getVia()} worth showing.
 */
public class Teleport
{
	private String via;
	private List<Integer> ids;
	private String collection;

	/** The method as the guide writes it, e.g. "Ardy Cloak". */
	public String getVia()
	{
		return via == null ? "" : via;
	}

	/** Every item that would do, best tier first, or empty when it is not an item. */
	public List<Integer> getIds()
	{
		return ids == null ? Collections.emptyList() : ids;
	}

	/** The item collection this came from, for logs and for diffing the data. */
	public String getCollection()
	{
		return collection;
	}

	public boolean isItem()
	{
		return !getIds().isEmpty();
	}
}
