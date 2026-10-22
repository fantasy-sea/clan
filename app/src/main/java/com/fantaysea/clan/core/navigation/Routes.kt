package com.fantaysea.clan.core.navigation

import kotlinx.serialization.Serializable


//导航路径
@Serializable
data object Onboarding
@Serializable
data class Second(val id: String)
//导航图路径
@Serializable
data object HomeGraph
//导航图子路径
@Serializable
data object Home
@Serializable
data object HomeControl
@Serializable
data object HomeShare
@Serializable
data class HomeControlSession(val deviceId: String)
@Serializable
data class HomeControlledSession(val deviceId: String)
