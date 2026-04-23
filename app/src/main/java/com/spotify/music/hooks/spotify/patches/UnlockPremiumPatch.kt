package com.spotify.music.hooks.spotify.patches

import com.spotify.music.core.utils.Logger
import de.robv.android.xposed.XposedHelpers

object UnlockPremiumPatch {
    private data class OverrideAttribute(
        val key: String,
        val overrideValue: Any,
        val isExpected: Boolean = true,
    )

    private val premiumOverrides = listOf(
        OverrideAttribute("ads", false), // !! true = no kick 
        OverrideAttribute("player-license", "premium"),
     //   OverrideAttribute("player-license-v2", "premium", isExpected = false),
        OverrideAttribute("shuffle", false),
        OverrideAttribute("on-demand", true),
        OverrideAttribute("streaming", true),
        OverrideAttribute("pick-and-shuffle", false),
        OverrideAttribute("streaming-rules", ""),
        OverrideAttribute("nft-disabled", "1"),
        OverrideAttribute("type", "premium"),
        OverrideAttribute("can_use_superbird", true, isExpected = false),
        OverrideAttribute("tablet-free", false, isExpected = false),
    )

    private var homeSectionIds: List<Int>? = null
    private var browseSectionIds: List<Int>? = null
    private var npvScrollSectionIds: List<Int>? = null

    fun overrideAttributes(attributes: Map<String, *>) {
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

                if (override.overrideValue == originalValue) return@forEach

                Logger.infoDebugOnly(
                    "Overriding account attribute ${override.key} from $originalValue to ${override.overrideValue}"
                )
                XposedHelpers.setObjectField(attribute, "value_", override.overrideValue)
            }
        } catch (ex: Exception) {
            Logger.error("overrideAttributes failure", ex)
        }
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
                getIntConstantOrNull("com.spotify.home.evopage.homeapi.proto.Section", "VIDEO_BRAND_AD_FIELD_NUMBER", classLoader),
                getIntConstantOrNull("com.spotify.home.evopage.homeapi.proto.Section", "IMAGE_BRAND_AD_FIELD_NUMBER", classLoader),
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
