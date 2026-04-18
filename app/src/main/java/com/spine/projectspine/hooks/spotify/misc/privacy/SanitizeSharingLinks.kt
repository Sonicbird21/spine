package com.spine.projectspine.hooks.spotify.misc.privacy

import android.content.ClipData
import com.spine.projectspine.core.hook.BaseHook
import com.spine.projectspine.core.hook.scopedHook
import com.spine.projectspine.hooks.spotify.patches.SanitizeSharingLinksPatch
import de.robv.android.xposed.XposedHelpers

fun BaseHook.sanitizeSharingLinks() {
    ::shareCopyUrlFingerprint.hookMethod(
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
