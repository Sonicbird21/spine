package com.spine.projectspine.hooks.spotify

import android.app.Application
import com.spine.projectspine.core.hook.BaseHook
import com.spine.projectspine.core.hook.HookFunction
import com.spine.projectspine.core.utils.Logger
import com.spine.projectspine.hooks.spotify.misc.privacy.sanitizeSharingLinks
import com.spine.projectspine.hooks.spotify.misc.unlockPremium
import com.spine.projectspine.hooks.spotify.misc.widgets.fixThirdPartyLaunchersWidgets
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
