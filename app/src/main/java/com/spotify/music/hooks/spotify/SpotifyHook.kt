package com.spotify.music.hooks.spotify

import android.app.Application
import com.spotify.music.core.hook.BaseHook
import com.spotify.music.core.hook.HookFunction
import com.spotify.music.core.utils.Logger
import com.spotify.music.hooks.spotify.misc.privacy.sanitizeSharingLinks
import com.spotify.music.hooks.spotify.misc.unlockPremium
import com.spotify.music.hooks.spotify.misc.widgets.fixThirdPartyLaunchersWidgets
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class SpotifyHook(app: Application, lpparam: LoadPackageParam) : BaseHook(app, lpparam) {
    override val hooks: Array<HookFunction> = arrayOf(
        ::unlockPremium,
        ::sanitizeSharingLinks,
        ::fixThirdPartyLaunchersWidgets,
    )

    init {
        Logger.info("SpotifyHook initialized for ${lpparam.packageName}")
    }
}
