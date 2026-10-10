package com.kenny.localmanager.file

import org.junit.Assert.assertEquals
import org.junit.Test

/** 详情面板“压缩包内容（第一层）”的聚合逻辑。 */
class ZipFirstLevelTest {

    @Test
    fun 多顶层_目录带斜杠排序显示() {
        val r = buildZipFirstLevel(
            listOf(
                "docs/readme.md",
                "docs/sub/a.txt",
                "root.txt",
                "zz.bin",
                "docs/" // 显式目录条目
            )
        )

        assertEquals(ZipFirstLevelResult.Ok(listOf("docs/", "root.txt", "zz.bin")), r)
    }

    @Test
    fun 单根目录_展开为第一层子项() {
        val r = buildZipFirstLevel(
            listOf(
                "markdown/basic.md",
                "markdown/extend.md",
                "markdown/imgs/",
                "markdown/imgs/x.svg",
                "markdown/Makefile"
            )
        )

        assertEquals(
            ZipFirstLevelResult.OkSingleDir("markdown", listOf("Makefile", "basic.md", "extend.md", "imgs/")),
            r
        )
    }

    @Test
    fun 单根目录无子项_展开为空子项() {
        // 保持既有语义：单根即使只有目录条目自身也走 OkSingleDir，子项为空
        assertEquals(ZipFirstLevelResult.OkSingleDir("only", emptyList()), buildZipFirstLevel(listOf("only/")))
    }

    @Test
    fun 平铺文件_各自为顶层() {
        val r = buildZipFirstLevel(listOf("a.txt", "b/c.txt", "d.txt"))

        assertEquals(ZipFirstLevelResult.Ok(listOf("a.txt", "b/", "d.txt")), r)
    }

    @Test
    fun 空包_返回空清单() {
        assertEquals(ZipFirstLevelResult.Ok(emptyList()), buildZipFirstLevel(emptyList()))
    }
}
