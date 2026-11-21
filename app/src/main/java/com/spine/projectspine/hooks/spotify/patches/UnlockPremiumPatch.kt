package com.spine.projectspine.hooks.spotify.patches

import com.spine.projectspine.core.utils.Logger
import com.spine.projectspine.hooks.spotify.misc.NativeSessionProtection
import de.robv.android.xposed.XposedHelpers
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object UnlockPremiumPatch {
    private data class OverrideAttribute(
        val key: String,
        val overrideValue: Any,
        // Default false: product-state attributes differ by Spotify version/account, so an
        // absent one is normal, not a warning. Override still applies when present.
        val isExpected: Boolean = false,
    )

    /** ISO-8601 UTC instant one year ahead of process start (matches Eevee's oneYearFromNow). */
    private val oneYearFromNowIso: String =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusYears(1)
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_INSTANT)

    // Keys stripped from the product state entirely, mirroring what EeveeSpotify removes on iOS
    // (DynamicPremium modifyAttributes + EeveePremiumForce stripKeys). These are the server's
    // own anti-abuse / entitlement-validation signals; leaving the REAL (free) values in place
    // while every other attribute claims premium is the inconsistency the backend cross-checks
    // to invalidate the session. In particular `at-signal` is a change-triggered renewal signal
    // (its mutation forces a token renew that re-fetches the real free state and exposes the
    // spoof), and the eligibility/trial/upsell keys mark the account as non-premium. Eevee works
    // on iOS precisely because it removes these; the Android module was leaving them all intact.
    private val stripKeys = setOf(
        "at-signal",
        "strider-key",
        "feature-set-id-masked",
        "payment-state",
        "last-premium-activation-date",
        "on-demand-trial",
        "on-demand-trial-in-progress",
        "smart-shuffle",
        "is-eligible-for-trial",
        "is-eligible-for-upsell",
        "upsell-state",
        "ad-session-persistence",
        "ad-formats-preroll-video",
        "is-premium-eligible",
    )

    private val premiumOverrides = listOf(

        OverrideAttribute("type", "premium"),
        OverrideAttribute("ads", false),
        OverrideAttribute("on-demand", true),
        OverrideAttribute("streaming-rules", ""),
        OverrideAttribute("catalogue", "premium"),
        OverrideAttribute("product", "premium"),
        OverrideAttribute("player-license", "premium"),
        OverrideAttribute("shuffle", false),
        OverrideAttribute("name", "Spotify Premium"),
        OverrideAttribute("player-license-v2", "premium", isExpected = false),
        // Eevee uses the bare "pr:premium,tc:0" (proven to not trip detection). Matching a real
        // account's full rt: rate-plan string is both unnecessary (Eevee shows exact-match isn't
        // the detection mechanism — stripping the validation keys below is) and account-specific.
        OverrideAttribute("financial-product", "pr:premium,tc:0"),
        // Values taken from Eevee's forcedPremiumString (ad-tracking off). Harmless to detection
        // since they're not validation keys; kept for ad-id privacy parity with Eevee.
        OverrideAttribute("ab-ad-player-targeting", false),
        OverrideAttribute("allow-advertising-id-transmission", false),
        OverrideAttribute("restrict-advertising-id-transmission", true),
        OverrideAttribute("audio-ad-frequency", false),
        OverrideAttribute("video-ad-frequency", false),
        OverrideAttribute("ad-formats", ""),
        OverrideAttribute("unrestricted", true),
        OverrideAttribute("shuffle-eligible", true),
        OverrideAttribute("social-connect", true),
        OverrideAttribute("tracks-in-collection-enabled", true),
        OverrideAttribute("is-eligible-premium-unboxing", true),
        OverrideAttribute("can_use_superbird", true),
        OverrideAttribute("manager", true),
        OverrideAttribute("nft-disabled", true),
        OverrideAttribute("previous-streaming-rules", ""),
        // high-bitrate intentionally NOT forced: requesting the premium-quality encrypted
        // file gets its audio key denied (drm_denied) on a free account -> undecryptable
        // audio -> decode fail -> skip. Let the player pick the standard file whose key is granted.
        OverrideAttribute("shuffle-mode", false),
        OverrideAttribute("pick-and-shuffle", false),
        // Eevee forces these to one year out (proven). The exact value isn't the detection
        // vector — the stripped validation keys below are.
        OverrideAttribute("subscription-enddate", oneYearFromNowIso),
        OverrideAttribute("product-expiry", oneYearFromNowIso),
        OverrideAttribute("trial-ends-at", "999999999"),
        OverrideAttribute("current-period-end", "999999999"),
        OverrideAttribute("premium-promotion-eligible", false),
        OverrideAttribute("payments-initial-campaign", "default"),
        // Server-pushed logout triggers: neutralize only if present, never seed (same as Eevee).
        OverrideAttribute("forced_logout", "", isExpected = false),
        OverrideAttribute("forced_logout_abroad_since", "", isExpected = false),
        OverrideAttribute("force_logout", "", isExpected = false),
        OverrideAttribute("logout_required", false, isExpected = false),
        OverrideAttribute("session_invalidated", false, isExpected = false),
    )

    private var homeSectionIds: List<Int>? = null
    private var browseSectionIds: List<Int>? = null
    private var npvScrollSectionIds: List<Int>? = null

    // Diagnostic: raw server values of the entitlement/logout keys, logged whenever they
    // change, so the moment Spotify flags the account is timestamped in logcat.
    private val watchKeys = listOf(
        "type", "catalogue", "product", "player-license", "player-license-v2",
        "on-demand", "streaming-rules", "previous-streaming-rules", "shuffle", "ads",
        "financial-product", "payment-state", "subscription-enddate", "product-expiry",
        "forced_logout", "force_logout", "forced_logout_abroad_since",
        "logout_required", "session_invalidated",
    )
    private var lastSnapshot: String? = null

    private fun logServerSnapshot(attributes: Map<String, *>) {
        runCatching {
            val snap = watchKeys.mapNotNull { key ->
                val attribute = attributes[key] ?: return@mapNotNull null
                val value = runCatching { XposedHelpers.getObjectField(attribute, "value_") }.getOrNull()
                "$key=$value"
            }.joinToString(" ")
            if (snap.isNotEmpty() && snap != lastSnapshot) {
                lastSnapshot = snap
                Logger.info("[PS-SNAPSHOT] $snap")
            }
        }
    }

    @Volatile private var stripReported = false

    // Remove the server's validation/anti-abuse keys from the live product-state map (Eevee's
    // removeValue approach). Runs unconditionally — even when premium is gated off — because the
    // churn these keys drive (esp. at-signal renewals re-fetching the real free state) is what
    // exposes the spoof. The proto map may be immutable; if so, removal throws and we fall back
    // to blanking the value (coerced to the attribute's own type) so the key at least stops
    // carrying its real signal. shortcut: blanking is weaker than removal for keys the backend
    // treats "present-but-empty" differently from "absent"; upgrade to byte-level parse rewrite
    // if testing shows a blanked key still triggers invalidation.
    private fun stripValidationKeys(attributes: Map<String, *>) {
        val mutable = attributes as? MutableMap<String, Any?>
        stripKeys.forEach { key ->
            val attribute = attributes[key] ?: return@forEach
            val removed = mutable != null && runCatching { mutable.remove(key); mutable[key] == null }.getOrDefault(false)
            if (!removed) {
                // Immutable map: neutralize in place by blanking value_ to the attribute's type.
                runCatching {
                    val original = XposedHelpers.getObjectField(attribute, "value_")
                    val blank = coerce("", original)
                    if (blank != null && blank != original) {
                        XposedHelpers.setObjectField(attribute, "value_", blank)
                    }
                }
            }
        }
        if (!stripReported) {
            stripReported = true
            Logger.info("[STRIP] validation keys ${if (mutable != null) "removed" else "blanked (immutable map)"}")
        }
    }

    fun overrideAttributes(attributes: Map<String, *>) {
        logServerSnapshot(attributes)
        stripValidationKeys(attributes)
        // Only apply premium while the client-token is healthy. During (re)auth the gate
        // closes, we leave the real (free) state so the client-token handshake isn't
        // wedged, then it reopens and premium re-applies. Self-healing across restarts.
        if (!NativeSessionProtection.clientTokenReady()) return
        try {
            premiumOverrides.forEach { override ->
                val attribute = attributes[override.key]
                if (attribute == null) {
                    if (override.isExpected) {
                        Logger.warn("Attribute ${override.key} expected but not found")
                    }
                    return@forEach
                }

                val originalValue = runCatching {
                    XposedHelpers.getObjectField(attribute, "value_")
                }.getOrNull() ?: return@forEach

                // value_ is a proto oneof; a wrong type makes the typed getter throw ClassCastException.
                val newValue = coerce(override.overrideValue, originalValue)
                if (newValue == null) {
                    Logger.warn("Attribute ${override.key} is ${originalValue.javaClass.simpleName}, skipping override")
                    return@forEach
                }
                if (newValue == originalValue) return@forEach

                Logger.infoDebugOnly(
                    "Overriding account attribute ${override.key} from $originalValue to $newValue"
                )
                XposedHelpers.setObjectField(attribute, "value_", newValue)
            }
        } catch (ex: Exception) {
            Logger.error("overrideAttributes failure", ex)
        }
    }

    private fun coerce(value: Any, original: Any): Any? = when {
        value.javaClass == original.javaClass -> value
        original is String && value is Boolean -> if (value) "1" else "0"
        original is Boolean && value is String -> value == "1" || value == "true"
        else -> null
    }

    fun removeStationString(spotifyUriOrUrl: String): String {
        return try {
            spotifyUriOrUrl.replace("spotify:station:", "spotify:")
        } catch (ex: Exception) {
            Logger.error("removeStationString failure", ex)
            spotifyUriOrUrl
        }
    }

    fun removeHomeSections(sections: MutableList<*>) {
        if (sections.isEmpty()) return
        if (homeSectionIds == null) {
            val classLoader = sections.firstNotNullOfOrNull { it }?.javaClass?.classLoader ?: return
            homeSectionIds = listOfNotNull(
                getIntConstantOrNull("com.spotify.casita.v1.resolved.Section", "VIDEO_BRAND_AD_FIELD_NUMBER", classLoader),
                getIntConstantOrNull("com.spotify.casita.v1.resolved.Section", "IMAGE_BRAND_AD_FIELD_NUMBER", classLoader),
            )
        }
        removeSections(sections, fieldName = "featureTypeCase_", idsToRemove = homeSectionIds!!)
    }

    fun removeBrowseSections(sections: MutableList<*>) {
        if (sections.isEmpty()) return
        if (browseSectionIds == null) {
            val classLoader = sections.firstNotNullOfOrNull { it }?.javaClass?.classLoader ?: return
            browseSectionIds = listOfNotNull(
                getIntConstantOrNull("com.spotify.browsita.v1.resolved.Section", "BRAND_ADS_FIELD_NUMBER", classLoader),
            )
        }
        removeSections(sections, fieldName = "sectionTypeCase_", idsToRemove = browseSectionIds!!)
    }

    fun removeNpvSections(sections: MutableList<*>) {
        if (sections.isEmpty()) return
        if (npvScrollSectionIds == null) {
            val classLoader = sections.firstNotNullOfOrNull { it }?.javaClass?.classLoader ?: return
            npvScrollSectionIds = listOfNotNull(
                getIntConstantOrNull("com.spotify.scrollsita.v1.Section", "IMAGE_BRAND_AD_FIELD_NUMBER", classLoader)
            )
        }
        removeSections(sections, fieldName = "sectionTypeCase_", idsToRemove = npvScrollSectionIds!!)
    }

    private fun removeSections(sections: MutableList<*>, fieldName: String, idsToRemove: List<Int>) {
        if (idsToRemove.isEmpty()) return
        runCatching {
            val iterator = sections.iterator()
            while (iterator.hasNext()) {
                val section = iterator.next() ?: continue
                val featureTypeId = XposedHelpers.getIntField(section, fieldName)
                if (idsToRemove.contains(featureTypeId)) {
                    iterator.remove()
                }
            }
        }.onFailure { err ->
            Logger.error("removeSections failure", err)
        }
    }

    private fun getIntConstantOrNull(className: String, fieldName: String, classLoader: ClassLoader): Int? {
        return runCatching {
            val clazz = Class.forName(className, false, classLoader)
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.getInt(null)
        }.onFailure {
            Logger.error("Failed getting field $fieldName in $className", it)
        }.getOrNull()
    }
}
