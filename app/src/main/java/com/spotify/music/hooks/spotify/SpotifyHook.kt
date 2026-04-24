package com.spotify.music.hooks.spotify

import android.app.Application
import com.spotify.music.core.feature.HookFeature
import com.spotify.music.core.hook.BaseHook
import com.spotify.music.core.hook.HookFunction
import com.spotify.music.core.utils.Logger
import com.spotify.music.hooks.spotify.features.network.blockdealer.BlockDealerFeature
import com.spotify.music.hooks.spotify.features.network.blocktracking.BlockTrackingFeature
import com.spotify.music.hooks.spotify.features.privacy.sanitizesharinglinks.SanitizeSharingLinksFeature
import com.spotify.music.hooks.spotify.features.premium.unlockpremium.UnlockPremiumFeature
import com.spotify.music.hooks.spotify.features.ui.fixwidgets.FixWidgetsFeature
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class SpotifyHook(app: Application, lpparam: LoadPackageParam) : BaseHook(app, lpparam) {
    override val hooks: Array<HookFunction> = emptyArray()

    override val features: List<HookFeature> = listOf(
        UnlockPremiumFeature(),
        SanitizeSharingLinksFeature(),
        FixWidgetsFeature(),
        BlockDealerFeature(),
        BlockTrackingFeature(),
    )

    init {
        Logger.info("SpotifyHook initialized for ${lpparam.packageName} with ${features.size} features")
    }
}
