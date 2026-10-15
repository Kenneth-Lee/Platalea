package com.kenny.localmanager.family

import com.kenny.localmanager.file.DocumentFileModel

/**
 * 公告转发请求（家庭网络域数据）：待转发到留言板的内容。
 *
 * 为什么引入：转发由其他域发起（目录页选文件、留言板页选消息），显示为
 * 转发目标选择流程（BulletinForwardTargetFlow 功能页）。"有没有待转发的
 * 公告"是家庭网络域的判断——请求槽住在 [FamilyNetworkManager]，发起方
 * 命令式直呼 requestForward，页面投影该状态，宿主不中转。
 */
sealed class BulletinForwardPayload {
    /** 文件转发：目录页选中文件（textContent 为附加文字，可空）。 */
    data class Files(
        val items: List<DocumentFileModel>,
        val textContent: String = ""
    ) : BulletinForwardPayload()

    /** 消息转发：留言板页转发既有留言（源会话供回链展示）。 */
    data class Messages(
        val sourceSession: BulletinBoardOpenSession,
        val messages: List<BulletinMessage>
    ) : BulletinForwardPayload()
}
