package com.kenny.localmanager.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * MdLinkUtils 单元测试：外链/主机名判断、相对路径提取。
 */
class MdLinkUtilsTest {

    @Test
    fun looksLikeExternalUrl_emptyOrBlank_returnsFalse() {
        assertEquals(false, MdLinkUtils.looksLikeExternalUrl(""))
        assertEquals(false, MdLinkUtils.looksLikeExternalUrl("   "))
    }

    @Test
    fun looksLikeExternalUrl_hasScheme_returnsFalse() {
        assertEquals(false, MdLinkUtils.looksLikeExternalUrl("https://example.com"))
        assertEquals(false, MdLinkUtils.looksLikeExternalUrl("http://a.b"))
        assertEquals(false, MdLinkUtils.looksLikeExternalUrl("content://authority/path"))
        assertEquals(false, MdLinkUtils.looksLikeExternalUrl("file:///path/doc.md"))
    }

    @Test
    fun looksLikeExternalUrl_www_returnsTrue() {
        assertEquals(true, MdLinkUtils.looksLikeExternalUrl("www.baidu.com"))
        assertEquals(true, MdLinkUtils.looksLikeExternalUrl("  www.example.org  "))
    }

    @Test
    fun looksLikeExternalUrl_domainWithTwoDotsNoSlash_returnsTrue() {
        assertEquals(true, MdLinkUtils.looksLikeExternalUrl("example.com"))
        assertEquals(true, MdLinkUtils.looksLikeExternalUrl("a.b.c"))
    }

    @Test
    fun looksLikeExternalUrl_pathOrRelative_returnsFalse() {
        assertEquals(false, MdLinkUtils.looksLikeExternalUrl("docs/手册.md"))
        assertEquals(false, MdLinkUtils.looksLikeExternalUrl("extend.md#锚点"))
        assertEquals(false, MdLinkUtils.looksLikeExternalUrl("#section"))
    }

    @Test
    fun looksLikeExternalUrl_singleDotFilename_returnsFalse() {
        assertEquals(false, MdLinkUtils.looksLikeExternalUrl("readme.md"))
        assertEquals(false, MdLinkUtils.looksLikeExternalUrl("test1.md"))
    }

    @Test
    fun looksLikeHostname_blankOrWithSlash_returnsFalse() {
        assertEquals(false, MdLinkUtils.looksLikeHostname(""))
        assertEquals(false, MdLinkUtils.looksLikeHostname("  "))
        assertEquals(false, MdLinkUtils.looksLikeHostname("path/to/file"))
    }

    @Test
    fun looksLikeHostname_www_returnsTrue() {
        assertEquals(true, MdLinkUtils.looksLikeHostname("www.baidu.com"))
    }

    @Test
    fun looksLikeHostname_twoDots_returnsTrue() {
        assertEquals(true, MdLinkUtils.looksLikeHostname("a.b.c"))
        assertEquals(true, MdLinkUtils.looksLikeHostname("www.example.com"))
    }

    @Test
    fun looksLikeHostname_singleDotLikeFilename_returnsFalse() {
        assertEquals(false, MdLinkUtils.looksLikeHostname("example.com"))
    }

    @Test
    fun looksLikeHostname_singleDotOrNoDot_returnsFalse() {
        assertEquals(false, MdLinkUtils.looksLikeHostname("readme.md"))
        assertEquals(false, MdLinkUtils.looksLikeHostname("test1"))
        assertEquals(false, MdLinkUtils.looksLikeHostname("file"))
    }

    @Test
    fun hostnameSegmentFromUrl_fileUrlWithHostname_returnsSegment() {
        assertEquals("www.bing.com", MdLinkUtils.hostnameSegmentFromUrl("file:///www.bing.com"))
    }

    @Test
    fun hostnameSegmentFromUrl_fileUrlWithPath_returnsNullWhenLastSegmentNotHostname() {
        assertNull(MdLinkUtils.hostnameSegmentFromUrl("file:///path/to/readme.md"))
        assertNull(MdLinkUtils.hostnameSegmentFromUrl("file:///data/content/extend.md#锚点"))
    }

    @Test
    fun hostnameSegmentFromUrl_emptyPath_returnsNull() {
        assertNull(MdLinkUtils.hostnameSegmentFromUrl("file:///"))
    }

    @Test
    fun extractRelativePathFromRequest_emptyBase_returnsNull() {
        assertNull(MdLinkUtils.extractRelativePathFromRequest("", "http://a/b"))
    }

    @Test
    fun extractRelativePathFromRequest_resourceUnderBase_returnsRelativePath() {
        val currentFileUri = "content://authority/tree/1/document/folder/readme.md"
        val resourceUrl = "content://authority/tree/1/document/folder/docs/手册.md"
        assertEquals("docs/手册.md", MdLinkUtils.extractRelativePathFromRequest(currentFileUri, resourceUrl))
    }

    @Test
    fun extractRelativePathFromRequest_resourceNotUnderBase_returnsNull() {
        val currentFileUri = "content://authority/folder/readme.md"
        assertNull(
            MdLinkUtils.extractRelativePathFromRequest(
                currentFileUri,
                "content://authority/other/image.png"
            )
        )
    }

    @Test
    fun extractRelativePathFromRequest_emptyRelative_returnsNull() {
        assertNull(
            MdLinkUtils.extractRelativePathFromRequest(
                "content://authority/folder/",
                "content://authority/folder/"
            )
        )
    }
}
