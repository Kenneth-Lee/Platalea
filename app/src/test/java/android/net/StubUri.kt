package android.net

import android.os.Parcel

/**
 * JVM 单测 Uri 桩（文件浏览功能拆解设计 第一批）：Uri 构造器
 * package-private，仅同包可继承。测试构造 DocumentFileModel 用；
 * 全部成员返回哑值——被测逻辑只读文件名。
 */
internal class StubUri : Uri() {
    override fun buildUpon(): Builder = Builder()
    override fun getScheme(): String? = null
    override fun getEncodedSchemeSpecificPart(): String? = null
    override fun getSchemeSpecificPart(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getAuthority(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getUserInfo(): String? = null
    override fun getHost(): String? = null
    override fun getPort(): Int = -1
    override fun getEncodedPath(): String? = null
    override fun getPath(): String? = null
    override fun getLastPathSegment(): String? = null
    override fun getPathSegments(): MutableList<String> = mutableListOf()
    override fun getEncodedQuery(): String? = null
    override fun getQuery(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getFragment(): String? = null
    override fun isHierarchical(): Boolean = false
    override fun isOpaque(): Boolean = false
    override fun isRelative(): Boolean = true
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = Unit
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
    override fun toString(): String = "stub-uri-${System.identityHashCode(this)}"
}
