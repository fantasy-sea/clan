package com.fantaysea.clan

import com.fantaysea.clan.core.navigation.HomeDestination
import com.fantaysea.clan.core.navigation.HomeGraph
import com.fantaysea.clan.core.navigation.OnboardingDestination
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
        val descriptor = serializer<HomeDestination.ControlSession>().descriptor
        assertEquals(1, descriptor.elementsCount)
        assertEquals("deviceId", descriptor.getElementName(0))
        assertEquals(PrimitiveKind.STRING, descriptor.getElementDescriptor(0).kind)
    }

    @Test
    fun `all routes have distinct serial names`() {
        val names = listOf(
            serializer<HomeDestination.Home>(),
            serializer<HomeDestination.ControlHome>(),
            serializer<HomeDestination.Share>(),
            serializer<HomeDestination.ControlSession>(),
            serializer<HomeDestination.ControlledSession>(),
            serializer<HomeGraph>(),
            serializer<OnboardingDestination>(),
        ).map { it.descriptor.serialName }

        assertTrue(names.all { it.isNotBlank() })
        assertEquals(names.size, names.toSet().size)
    }
}
