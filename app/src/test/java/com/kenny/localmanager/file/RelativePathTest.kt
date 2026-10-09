package com.kenny.localmanager.file

import org.junit.Assert.assertEquals
import org.junit.Test

/** relativePathFromCurrentDir：目标根算起路径 → 相对当前目录显示形式 */
class RelativePathTest {

    @Test
    fun sameDirGivesFileName() {
        assertEquals("x.zip", relativePathFromCurrentDir("test/x.zip", "test"))
    }

    @Test
    fun fromRootGivesFullPath() {
        assertEquals("books/x.zip", relativePathFromCurrentDir("books/x.zip", "/"))
        assertEquals("x.zip", relativePathFromCurrentDir("x.zip", "/"))
    }

    @Test
    fun crossDirGoesUp() {
        assertEquals("../books/x.zip", relativePathFromCurrentDir("books/x.zip", "test"))
        assertEquals("../c.zip", relativePathFromCurrentDir("a/b/c.zip", "a/b/d"))
        assertEquals("../../x.zip", relativePathFromCurrentDir("x.zip", "a/b"))
    }

    @Test
    fun targetInsideSubdirOfCurrent() {
        assertEquals("b/c.zip", relativePathFromCurrentDir("a/b/c.zip", "a"))
        assertEquals("d/c.zip", relativePathFromCurrentDir("a/d/c.zip", "a"))
    }

    @Test
    fun targetIsCurrentDirItself() {
        assertEquals(".", relativePathFromCurrentDir("a/b", "a/b"))
    }

    @Test
    fun sameNameDirNotOverconsumed() {
        // 目标文件名与 base 末段目录同名：目录段匹配不能吞掉文件名
        assertEquals("b.zip", relativePathFromCurrentDir("a/b.zip", "a"))
        assertEquals("../a/b.zip", relativePathFromCurrentDir("a/b.zip", "b"))
    }
}
