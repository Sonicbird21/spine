package org.sys.config.hooks.spotify.misc.privacy

import android.content.ClipData
import org.sys.config.core.hook.BaseHook
import org.sys.config.core.hook.scopedHook
import org.sys.config.hooks.spotify.patches.SanitizeSharingLinksPatch
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
