/*
 * Copyright (c) 2020 Proton Technologies AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.android.ui.home.profiles

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.Profile.Companion.getTempProfile
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.tv.main.MainViewModel
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val userData: UserData,
    private val vpnStateMonitor: VpnStateMonitor,
    private val vpnConnectionManager: VpnConnectionManager,
    private val serverManager: ServerManager,
    userPlanManager: UserPlanManager,
    certificateRepository: CertificateRepository
) : MainViewModel(userData, userPlanManager, certificateRepository) {

    // Temporary method to help java activity collect a flow
    fun collectPlanChange(activity: AppCompatActivity, onChange: (UserPlanManager.InfoChange.PlanChange) -> Unit) {
        activity.lifecycleScope.launch {
            userPlanChangeEvent.collect {
                onChange(it)
            }
        }
    }

    // Convert to a suspend method and remove the callback once HomeActivity is in Kotlin.
    fun reconnectToSameCountry(connectCallback: (newProfile: Profile) -> Unit) {
        DebugUtils.debugAssert("Is connected") { vpnStateMonitor.isConnected }
        val connectedCountry: String = vpnStateMonitor.connectionParams!!.server.exitCountry
        val exitCountry: VpnCountry? =
            serverManager.getVpnExitCountry(connectedCountry, userData.isSecureCoreEnabled)
        val newServer = if (exitCountry != null) {
            serverManager.getBestScoreServer(exitCountry)
        } else {
            serverManager.getBestScoreServer(userData.isSecureCoreEnabled)
        }
        if (newServer != null) {
            val newProfile = getTempProfile(newServer, serverManager)
            vpnConnectionManager.disconnectWithCallback { connectCallback(newProfile) }
        } else {
            val toOrFrom = if (userData.isSecureCoreEnabled) "to" else "from"
            ProtonLogger.log("Unable to find a server to connect to when switching $toOrFrom Secure Core")
            vpnConnectionManager.disconnect()
        }
    }
}
