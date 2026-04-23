package org.sys.config.hooks.spotify

import android.app.Application
import org.sys.config.core.hook.BaseHook
import org.sys.config.core.hook.HookFunction
import org.sys.config.core.utils.Logger
import org.sys.config.hooks.spotify.misc.privacy.sanitizeSharingLinks
import org.sys.config.hooks.spotify.misc.unlockPremium
import org.sys.config.hooks.spotify.misc.widgets.fixThirdPartyLaunchersWidgets
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
