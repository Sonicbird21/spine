package com.spotify.music.hooks.spotify.features.privacy.sanitizesharinglinks

import android.content.ClipData
import com.spotify.music.core.hook.BaseHook
import com.spotify.music.core.hook.scopedHook
import com.spotify.music.hooks.spotify.patches.SanitizeSharingLinksPatch
import de.robv.android.xposed.XposedHelpers

fun BaseHook.sanitizeSharingLinks() {
    ::ShareCopyUrlMethodFingerprint.hookMethod(
        scopedHook(
            XposedHelpers.findMethodExact(
                ClipData::class.java.name,
                classLoader,
                "newPlainText",
                CharSequence::class.java,
                CharSequence::class.java,
            )
        ) {
            before { param ->
                val url = param.args[1] as? String ?: return@before
                param.args[1] = SanitizeSharingLinksPatch.sanitizeSharingLink(url)
            }
        }
    )
}
