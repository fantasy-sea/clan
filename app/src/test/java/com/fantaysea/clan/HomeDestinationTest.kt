package com.fantaysea.clan

import com.fantaysea.clan.core.navigation.DeviceListDest
import com.fantaysea.clan.core.navigation.InterconnectGraph
import com.fantaysea.clan.core.navigation.ModuleEntryDest
import com.fantaysea.clan.core.navigation.OnboardingDestination
import com.fantaysea.clan.core.navigation.SessionControlDest
import com.fantaysea.clan.core.navigation.SessionControlledDest
import com.fantaysea.clan.core.navigation.ShareScreenDest
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * type-safe 路由依赖 Kotlin 序列化编译器插件生成序列化器。
 * 这里直接取序列化器, 一旦插件在 AGP 内置 Kotlin 下失效会在运行期抛异常, 从而被单测捕获。
 */
class HomeDestinationTest {

    @Test
    fun `session route serializer carries deviceId`() {
        val descriptor = serializer<SessionControlledDest>().descriptor
        assertEquals(1, descriptor.elementsCount)
        assertEquals("deviceId", descriptor.getElementName(0))
        assertEquals(PrimitiveKind.STRING, descriptor.getElementDescriptor(0).kind)
    }

    @Test
    fun `all routes have distinct serial names`() {
        val names = listOf(
            serializer<ModuleEntryDest>(),
            serializer<DeviceListDest>(),
            serializer<ShareScreenDest>(),
            serializer<SessionControlDest>(),
            serializer<SessionControlledDest>(),
            serializer<InterconnectGraph>(),
            serializer<OnboardingDestination>(),
        ).map { it.descriptor.serialName }

        assertTrue(names.all { it.isNotBlank() })
        assertEquals(names.size, names.toSet().size)
    }
}
