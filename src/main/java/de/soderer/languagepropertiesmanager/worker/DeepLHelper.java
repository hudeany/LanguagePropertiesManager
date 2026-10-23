package de.soderer.languagepropertiesmanager.worker;

import java.net.HttpURLConnection;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.soderer.json.JsonArray;
import de.soderer.json.JsonObject;
import de.soderer.json.JsonReader;
import de.soderer.json.JsonWriter;
import de.soderer.network.HttpMethod;
import de.soderer.network.HttpRequest;
import de.soderer.network.HttpResponse;
import de.soderer.network.HttpUtilities;
import de.soderer.utilities.StringUtilities;

/**
 * Client for the DeepL translation API (v2). Requests that fail because of rate limiting (HTTP 429) or temporary server
 * errors (HTTP 5xx) are retried with exponential backoff.
 */
public class DeepLHelper {
	/** Base URL of the DeepL API for free accounts (API keys ending with ":fx") */
	public final static String DEEPL_BASEURL_FREE_DEFAULT = "https://api-free.deepl.com";

	/** Base URL of the DeepL API for paid (Pro) accounts */
	public final static String DEEPL_BASEURL_DEFAULT = "https://api.deepl.com";

	// Maximum number of retries on HTTP 429 (Too Many Requests)
	private final static int MAX_RETRY_COUNT = 5;

	// Initial backoff delay in milliseconds, doubled after each retry
	private final static long INITIAL_BACKOFF_MILLISECONDS = 500;

	// Upper bound for the backoff delay, to avoid excessively long waits
	private final static long MAX_BACKOFF_MILLISECONDS = 16_000;

	// DeepL-specific: translation quota exceeded, retrying will not help
	private final static int HTTP_QUOTA_EXCEEDED = 456;

	// HTTP code for rate limiting, retry with backoff makes sense
	private final static int HTTP_TOO_MANY_REQUESTS = 429;

	private final String baseUrl;
	private final String apiKey;
	private final Proxy proxy;

	private volatile List<String> supportedLanguages = null;
	private volatile List<String> supportedTargetLanguages = null;

	/**
	 * Creates a client for the free DeepL API without proxy.
	 *
	 * @param apiKey DeepL API key
	 */
	public DeepLHelper(final String apiKey) {
		this(DEEPL_BASEURL_FREE_DEFAULT, apiKey, null);
	}

	/**
	 * Creates a client without proxy.
	 *
	 * @param baseUrl base URL of the API, e.g. {@link #DEEPL_BASEURL_FREE_DEFAULT} or {@link #DEEPL_BASEURL_DEFAULT}
	 * @param apiKey DeepL API key
	 */
	public DeepLHelper(final String baseUrl, final String apiKey) {
		this(baseUrl, apiKey, null);
	}

	/**
	 * Creates a client.
	 *
	 * @param baseUrl base URL of the API, e.g. {@link #DEEPL_BASEURL_FREE_DEFAULT} or {@link #DEEPL_BASEURL_DEFAULT}
	 * @param apiKey DeepL API key
	 * @param proxy proxy to use, or null for the default
	 */
	public DeepLHelper(final String baseUrl, final String apiKey, final Proxy proxy) {
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.proxy = proxy;
	}

	/**
	 * Codes of the supported source languages in upper case (e.g. "DE", "EN"), read once from the API and cached.
	 *
	 * @return list of language codes
	 * @throws Exception if the languages cannot be read
	 */
	public List<String> getSupportedLanguages() throws Exception {
		if (supportedLanguages == null) {
			supportedLanguages = readLanguages("/v2/languages");
		}
		return supportedLanguages;
	}

	/**
	 * Codes of the supported target languages in upper case (e.g. "DE", "EN-GB", "EN-US", "PT-BR"), read once from the
	 * API and cached. Target languages may include regional variants, which are not available as source languages.
	 *
	 * @return list of language codes
	 * @throws Exception if the languages cannot be read
	 */
	public List<String> getSupportedTargetLanguages() throws Exception {
		if (supportedTargetLanguages == null) {
			supportedTargetLanguages = readLanguages("/v2/languages?type=target");
		}
		return supportedTargetLanguages;
	}

	private List<String> readLanguages(final String path) throws Exception {
		try {
			final HttpRequest request = new HttpRequest(HttpMethod.GET, baseUrl + path);

			request.addHeader("Authorization", "DeepL-Auth-Key " + apiKey);

			int retryCount = 0;
			while (true) {
				final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxy);
				if (response.getHttpCode() == HttpURLConnection.HTTP_OK) {
					final JsonArray jsonData = (JsonArray) JsonReader.readJsonItemString(response.getContent());
					final List<String> languages = new ArrayList<>();
					for (final Object item : jsonData) {
						final String language = (String) ((JsonObject) item).getSimpleValue("language");
						if (language != null) {
							languages.add(language.toUpperCase(Locale.ROOT));
						}
					}
					return languages;
				} else if (response.getHttpCode() == HTTP_TOO_MANY_REQUESTS || response.getHttpCode() >= 500) {
					if (retryCount < MAX_RETRY_COUNT) {
						// too many requests or temporary server error => retry with exponential backoff
						Thread.sleep(getBackoffMilliseconds(retryCount));
						retryCount++;
					} else {
						throw new Exception("Error read available DeepL languages. HTTP code: " + response.getHttpCode() + "\nToo many requests or server error, retries exhausted\nContent: " + response.getContent());
					}
				} else if (response.getHttpCode() == HTTP_QUOTA_EXCEEDED) {
					throw new Exception("Error read available DeepL languages. Translation quota exceeded (HTTP code 456)\nContent: " + response.getContent());
				} else if (response.getHttpCode() == HttpURLConnection.HTTP_FORBIDDEN) {
					throw new Exception("Error read available DeepL languages. Invalid or unauthorized API key (HTTP code 403)\nContent: " + response.getContent());
				} else {
					throw new Exception("Error read available DeepL languages. HTTP code: " + response.getHttpCode() + "\nContent: " + response.getContent());
				}
			}
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new Exception("Cannot read available DeepL languages: Interrupted", e);
		} catch (final Exception e) {
			throw new Exception("Cannot read available DeepL languages: " + e.getMessage(), e);
		}
	}

	/**
	 * Translates a text with automatic detection of the source language.
	 *
	 * @param sourceText text to translate
	 * @param targetLanguage code of the target language (case insensitive), see {@link #getSupportedTargetLanguages()}
	 * @return translated text
	 * @throws Exception if the language is not supported or the translation fails
	 */
	public String translate(final String sourceText, final String targetLanguage) throws Exception {
		return translate(null, sourceText, targetLanguage);
	}

	/**
	 * Translates a text.
	 *
	 * @param sourceLanguage code of the source language (case insensitive), see {@link #getSupportedLanguages()}, or
	 *            null/blank for automatic detection
	 * @param sourceText text to translate
	 * @param targetLanguage code of the target language (case insensitive), see {@link #getSupportedTargetLanguages()}
	 * @return translated text
	 * @throws Exception if a language is not supported or the translation fails
	 */
	public String translate(final String sourceLanguage, final String sourceText, final String targetLanguage) throws Exception {
		try {
			final HttpRequest request = new HttpRequest(HttpMethod.POST, baseUrl+ "/v2/translate");

			request.addHeader("Authorization", "DeepL-Auth-Key " + apiKey);
			request.addHeader("Content-Type", "application/json");

			final JsonObject bodyJsonObject = new JsonObject();
			if (StringUtilities.isNotBlank(sourceLanguage)) {
				final String sourceLanguageCode = sourceLanguage.toUpperCase(Locale.ROOT);
				if (!getSupportedLanguages().contains(sourceLanguageCode)) {
					throw new Exception("Unsupported source language: " + sourceLanguage);
				}
				bodyJsonObject.add("source_lang", sourceLanguageCode);
			}
			if (StringUtilities.isBlank(targetLanguage)) {
				throw new Exception("Missing target language");
			}
			final String targetLanguageCode = targetLanguage.toUpperCase(Locale.ROOT);
			// Target languages include regional variants like "EN-US", the source language list is accepted for compatibility
			if (!getSupportedTargetLanguages().contains(targetLanguageCode) && !getSupportedLanguages().contains(targetLanguageCode)) {
				throw new Exception("Unsupported target language: " + targetLanguage);
			}
			bodyJsonObject.add("target_lang", targetLanguageCode);
			bodyJsonObject.add("text", new JsonArray().add(sourceText));

			request.setRequestBody(JsonWriter.getJsonItemString(bodyJsonObject));

			int retryCount = 0;
			while (true) {
				final HttpResponse response = HttpUtilities.executeHttpRequest(request, proxy);
				if (response.getHttpCode() == HttpURLConnection.HTTP_OK) {
					final JsonObject jsonData = (JsonObject) JsonReader.readJsonItemString(response.getContent());
					return (String) ((JsonObject) ((JsonArray) jsonData.get("translations")).get(0)).getSimpleValue("text");
				} else if (response.getHttpCode() == HTTP_TOO_MANY_REQUESTS || response.getHttpCode() >= 500) {
					if (retryCount < MAX_RETRY_COUNT) {
						// too many requests or temporary server error => retry with exponential backoff
						Thread.sleep(getBackoffMilliseconds(retryCount));
						retryCount++;
					} else {
						throw new Exception("Error http code: " + response.getHttpCode() + "\nToo many requests or server error, retries exhausted\nContent: " + response.getContent());
					}
				} else if (response.getHttpCode() == HTTP_QUOTA_EXCEEDED) {
					throw new Exception("Translation quota exceeded (HTTP code 456)\nContent: " + response.getContent());
				} else if (response.getHttpCode() == HttpURLConnection.HTTP_FORBIDDEN) {
					throw new Exception("Invalid or unauthorized API key (HTTP code 403)\nContent: " + response.getContent());
				} else {
					throw new Exception("Error http code: " + response.getHttpCode() + "\nContent: " + response.getContent());
				}
			}
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new Exception("Cannot translate text by DeepL: Interrupted", e);
		} catch (final Exception e) {
			throw new Exception("Cannot translate text by DeepL: " + e.getMessage(), e);
		}
	}

	/**
	 * Calculates the exponential backoff delay for a given retry attempt (0-based),
	 * capped at MAX_BACKOFF_MILLISECONDS and with a small random jitter added
	 * to avoid synchronized retries when multiple requests are throttled at once.
	 */
	private static long getBackoffMilliseconds(final int retryCount) {
		final long exponentialDelay = INITIAL_BACKOFF_MILLISECONDS * (1L << retryCount);
		final long cappedDelay = Math.min(exponentialDelay, MAX_BACKOFF_MILLISECONDS);
		final long jitter = (long) (Math.random() * cappedDelay * 0.2);
		return cappedDelay + jitter;
	}
}
