package io.grimoire.api.source

/**
 * A [Source] whose authenticated features — typically access to chapters that
 * are otherwise reported as [io.grimoire.api.model.Chapter.locked] — are
 * unlocked by signing in through a WebView.
 *
 * The host opens [loginUrl] in a WebView so the user can complete any sign-in
 * flow, including social providers (Google, Facebook, …) that rely on OAuth
 * redirects. Session cookies the site sets land in the shared Android
 * `CookieManager` and are automatically replayed on the source's OkHttp
 * requests by [io.grimoire.api.network.WebViewCookieJar], so no extra transport
 * wiring is needed. Cookies persist across app restarts; [isLoggedIn] is the
 * authoritative check for whether a usable session currently exists.
 */
interface WebViewLoginSource : Source {

    /** URL to load in a WebView so the user can sign in. */
    val loginUrl: String

    /**
     * Once the WebView navigates to a URL containing this substring, the host
     * may treat login as complete and close the WebView. `null` means the user
     * closes the WebView manually.
     */
    val loginSuccessUrl: String? get() = null

    /**
     * Whether a valid signed-in session currently exists — typically by
     * inspecting cookies or making a lightweight authenticated request.
     *
     * Must run off the main thread and must not throw; report "unknown" as
     * `false`.
     */
    suspend fun isLoggedIn(): Boolean

    /**
     * Clears the stored session for this source (sign out), e.g. by expiring
     * its cookies. Must run off the main thread and must not throw.
     */
    suspend fun logout()
}
