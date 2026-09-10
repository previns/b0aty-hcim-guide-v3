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
package com.b0atyguide.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches the guide's bank setup screenshots, off the event thread.
 *
 * <p>The images are the guide author's own uploads and are hotlinked from where
 * the wiki already points, never re-hosted. Nothing is requested until a bank is
 * expanded, so a player who never opens one makes no requests at all, and the
 * whole thing is behind a config switch.
 *
 * <p>Every failure is silent. A bank whose screenshot will not load shows the
 * steps and nothing else, which is exactly what the plugin did before this
 * existed -- a broken image is not worth an error in front of someone mid-route.
 */
@Slf4j
@Singleton
public class SectionImages
{
	/** Only this host, checked before any request leaves. */
	private static final String ALLOWED_HOST = "i.ibb.co";

	/**
	 * The guide has 173 screenshots. Holding them all is tens of megabytes for
	 * something a player scrolls past once, so the least recently used are
	 * dropped.
	 */
	private static final int MAX_CACHED = 24;

	private static final long MAX_BYTES = 4L * 1024 * 1024;

	private final OkHttpClient httpClient;

	/** Accessed only from the event thread. */
	private final Map<String, BufferedImage> cache =
		new LinkedHashMap<String, BufferedImage>(16, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest)
			{
				return size() > MAX_CACHED;
			}
		};

	/** URLs already tried and failed, so a dead link is not re-fetched forever. */
	private final Map<String, Boolean> failed = new LinkedHashMap<>();

	private Function<String, String> expectedHash;
	private final Map<String, Pending> pending = new LinkedHashMap<>();
	private long generation;

	private static final class Pending
	{
		private final List<Consumer<BufferedImage>> listeners = new ArrayList<>();
		private Call call;
	}

	@Inject
	SectionImages(OkHttpClient httpClient)
	{
		// The initial host check does not constrain a redirect's destination.
		// Refuse redirects instead of letting screenshot hosting contact an
		// unrelated server through the host application's shared HTTP client.
		this.httpClient = httpClient.newBuilder()
			.followRedirects(false).followSslRedirects(false).build();
	}

	/** Drop the decoded images and the hashes; the plugin is shutting down. */
	public void clear()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::clear);
			return;
		}
		generation++;
		for (Pending request : pending.values())
		{
			request.call.cancel();
		}
		pending.clear();
		cache.clear();
		failed.clear();
		expectedHash = null;
	}

	/**
	 * The hashes recorded when the guide was built. Set once the guide loads.
	 */
	public void setExpectedHashes(Function<String, String> hashes)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> setExpectedHashes(hashes));
			return;
		}
		clear();
		this.expectedHash = hashes;
	}

	/**
	 * Hand the image to {@code onLoaded} on the event thread, now if it is
	 * cached and later if it has to be fetched. Never calls back on failure.
	 */
	public void get(String url, Consumer<BufferedImage> onLoaded)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> get(url, onLoaded));
			return;
		}
		// Reject unknown links BEFORE making a request, not after downloading.
		final String hash = expectedHash == null ? null : expectedHash.apply(url);
		if (hash == null || !isAllowed(url))
		{
			return;
		}
		final BufferedImage cached = cache.get(url);
		if (cached != null)
		{
			onLoaded.accept(cached);
			return;
		}
		if (failed.containsKey(url))
		{
			return;
		}

		Pending existing = pending.get(url);
		if (existing != null)
		{
			existing.listeners.add(onLoaded);
			return;
		}
		final long requestedGeneration = generation;
		final Pending request = new Pending();
		request.listeners.add(onLoaded);
		request.call = httpClient.newCall(new Request.Builder().url(url).build());
		pending.put(url, request);
		request.call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("could not fetch {}", url, e);
				complete(null);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				BufferedImage image = null;
				try (Response closing = response)
				{
					if (closing.isSuccessful() && closing.body() != null
						&& closing.body().contentLength() <= MAX_BYTES)
					{
						final byte[] body = readBounded(closing.body().byteStream(), MAX_BYTES);
						// Hashed before it is decoded, so bytes that do not
						// match are never handed to the image decoder at all.
						if (matchesExpectedHash(url, hash, body))
						{
							image = ImageIO.read(new ByteArrayInputStream(body));
						}
					}
				}
				catch (IOException | RuntimeException e)
				{
					// ImageIO throws unchecked on some malformed input.
					log.debug("could not decode {}", url, e);
				}

				complete(image);
			}

			private void complete(BufferedImage loaded)
			{
				SwingUtilities.invokeLater(() ->
				{
					// Cancellation races with responses already delivered. An old
					// response must never refill the cache or update a closed panel.
					if (requestedGeneration != generation || pending.get(url) != request)
					{
						return;
					}
					pending.remove(url);
					if (loaded == null)
					{
						failed.put(url, Boolean.TRUE);
						return;
					}
					cache.put(url, loaded);
					for (Consumer<BufferedImage> listener : request.listeners)
					{
						listener.accept(loaded);
					}
				});
			}
		});
	}

	/**
	 * Whether these bytes are the ones the guide was built against.
	 *
	 * <p>A URL with no recorded hash is refused rather than trusted. The only
	 * way that happens is a screenshot the build could not reach, and showing
	 * an unverified picture is the thing this exists to prevent.
	 */
	private boolean matchesExpectedHash(String url, String expected, byte[] body)
	{
		if (expected == null)
		{
			log.debug("no recorded hash for {}", url);
			return false;
		}
		final String actual = sha256(body);
		if (!expected.equalsIgnoreCase(actual))
		{
			// Worth more than debug: it means the picture behind a link the
			// guide vouched for has been replaced since the build.
			log.warn("image at {} does not match the hash the guide was built with;"
				+ " not displaying it", url);
			return false;
		}
		return true;
	}

	/** Content-Length can be absent or dishonest; bound the bytes actually read. */
	static byte[] readBounded(InputStream input, long limit) throws IOException
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		long total = 0;
		int count;
		while ((count = input.read(buffer, 0,
			(int) Math.min(buffer.length, limit - total + 1))) != -1)
		{
			total += count;
			if (total > limit)
			{
				throw new IOException("Screenshot exceeds download limit");
			}
			output.write(buffer, 0, count);
		}
		return output.toByteArray();
	}

	private static String sha256(byte[] body)
	{
		try
		{
			final byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
			final StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest)
			{
				hex.append(Character.forDigit((b >> 4) & 0xF, 16));
				hex.append(Character.forDigit(b & 0xF, 16));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			// SHA-256 is required of every Java runtime.
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Whether this is a URL we are willing to request.
	 *
	 * <p>The guide's image links come from a wiki page anyone can edit, so the
	 * host is checked here rather than trusted. Without this an editor could
	 * point the plugin at any address they liked.
	 */
	public static boolean isAllowed(String url)
	{
		final HttpUrl parsed = url == null ? null : HttpUrl.parse(url);
		return parsed != null
			&& "https".equals(parsed.scheme())
			&& ALLOWED_HOST.equals(parsed.host());
	}
}
