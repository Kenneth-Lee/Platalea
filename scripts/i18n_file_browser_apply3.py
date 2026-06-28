#!/usr/bin/env python3
"""Apply batch 3 i18n - player, gpg, playlist, parse errors"""
from pathlib import Path

KT = Path(__file__).resolve().parents[1] / "app/src/main/java/com/kenny/localmanager/ui/FileBrowserApp.kt"
text = KT.read_text(encoding="utf-8")

REPLACEMENTS = [
    ('private fun parseFileSearchSize(raw: String): Long? {', 'private fun parseFileSearchSize(context: Context, raw: String): Long? {'),
    ('private fun parseFileSearchDate(raw: String, endOfDay: Boolean): Long? {', 'private fun parseFileSearchDate(context: Context, raw: String, endOfDay: Boolean): Long? {'),
    ('?: throw IllegalArgumentException("大小格式无效：$raw。支持 1024、10KB、1.5MB、2GB")',
     '?: throw IllegalArgumentException(context.getString(R.string.browser_size_format_invalid, raw))'),
    ('?: throw IllegalArgumentException("大小数值无效：$raw")',
     '?: throw IllegalArgumentException(context.getString(R.string.browser_size_invalid, raw))'),
    ('else -> throw IllegalArgumentException("不支持的大小单位：$unit")',
     'else -> throw IllegalArgumentException(context.getString(R.string.browser_size_unit_unsupported, unit))'),
    ('throw IllegalArgumentException("大小必须为非负数：$raw")',
     'throw IllegalArgumentException(context.getString(R.string.browser_size_must_non_negative, raw))'),
    ('?: throw IllegalArgumentException("日期格式无效：$raw，应为 yyyy-MM-dd")',
     '?: throw IllegalArgumentException(context.getString(R.string.browser_date_format_invalid, raw))'),
    ('parseFileSearchSize(fileSearchMinSize)', 'parseFileSearchSize(context, fileSearchMinSize)'),
    ('parseFileSearchSize(fileSearchMaxSize)', 'parseFileSearchSize(context, fileSearchMaxSize)'),
    ('parseFileSearchDate(fileSearchModifiedAfter, endOfDay = false)', 'parseFileSearchDate(context, fileSearchModifiedAfter, endOfDay = false)'),
    ('parseFileSearchDate(fileSearchModifiedBefore, endOfDay = true)', 'parseFileSearchDate(context, fileSearchModifiedBefore, endOfDay = true)'),
    ('clipboardManager?.setPrimaryClip(ClipData.newPlainText("路径", fullPath))',
     'clipboardManager?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.browser_full_path), fullPath))'),
    ('Toast.makeText(context, "当前列表没有可播放的音频文件", Toast.LENGTH_SHORT).show()',
     'Toast.makeText(context, context.getString(R.string.browser_no_audio_in_list), Toast.LENGTH_SHORT).show()'),
    ('contentDescription = "从列表移除"', 'contentDescription = stringResource(R.string.browser_remove_from_list)'),
    ('Toast.makeText(context, "已创建空播放列表", Toast.LENGTH_SHORT).show()',
     'Toast.makeText(context, context.getString(R.string.browser_empty_playlist_created), Toast.LENGTH_SHORT).show()'),
    ('Toast.makeText(context, "目录列表不能作为移入目标", Toast.LENGTH_SHORT).show()',
     'Toast.makeText(context, context.getString(R.string.browser_playlist_dir_no_move_target), Toast.LENGTH_SHORT).show()'),
    ('Toast.makeText(context, "目标播放列表已不存在", Toast.LENGTH_SHORT).show()',
     'Toast.makeText(context, context.getString(R.string.browser_playlist_target_missing), Toast.LENGTH_SHORT).show()'),
    ('val action = if (transfer.move) "移动" else "复制"',
     'val action = if (transfer.move) context.getString(R.string.pending_list_action_move) else context.getString(R.string.pending_list_action_copy)'),
    ('val duplicateHint = if (result.skippedCount > 0) "，目标列表已存在同一首" else ""',
     'val duplicateHint = if (result.skippedCount > 0) context.getString(R.string.browser_playlist_duplicate_hint) else ""'),
    ('Toast.makeText(context, "已${action}到「${target.name}」$duplicateHint", Toast.LENGTH_SHORT).show()',
     'Toast.makeText(context, context.getString(R.string.browser_playlist_action_result, action, target.name, duplicateHint), Toast.LENGTH_SHORT).show()'),
    ('PLAYER_AUDIO_ENGINE_MEDIA_PLAYER to "系统 MediaPlayer"', 'PLAYER_AUDIO_ENGINE_MEDIA_PLAYER to context.getString(R.string.browser_system_media_player)'),
    ('PLAYER_AUDIO_PRESET_FLAT to "原始"', 'PLAYER_AUDIO_PRESET_FLAT to context.getString(R.string.browser_flat_preset)'),
    ('PLAYER_AUDIO_PRESET_VOCAL to "人声清晰"', 'PLAYER_AUDIO_PRESET_VOCAL to context.getString(R.string.browser_vocal_preset)'),
    ('PLAYER_AUDIO_PRESET_BASS to "低频增强"', 'PLAYER_AUDIO_PRESET_BASS to context.getString(R.string.browser_bass_preset)'),
    ('PLAYER_AUDIO_PRESET_CAR to "车载"', 'PLAYER_AUDIO_PRESET_CAR to context.getString(R.string.browser_car_preset)'),
    ('PLAYER_AUDIO_PRESET_HEADPHONE to "耳机"', 'PLAYER_AUDIO_PRESET_HEADPHONE to context.getString(R.string.browser_headphone_preset)'),
    ('title = { Text("播放器配置") }', 'title = { Text(stringResource(R.string.browser_player_config_title)) }'),
    ('Text("这些配置只保存在本机，用来对比不同手机上的播放效果。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)',
     'Text(stringResource(R.string.browser_player_settings_local_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)'),
    ('Text("播放内核", style = MaterialTheme.typography.titleSmall)', 'Text(stringResource(R.string.browser_player_engine), style = MaterialTheme.typography.titleSmall)'),
    ('Text("切换内核后从下一首或重新开始播放时生效。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)',
     'Text(stringResource(R.string.browser_engine_switch_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)'),
    ('Text("稳定播放保持唤醒", style = MaterialTheme.typography.bodyLarge)',
     'Text(stringResource(R.string.browser_wake_lock), style = MaterialTheme.typography.bodyLarge)'),
    ('Text("播放时请求 PARTIAL_WAKE_LOCK，减少息屏后卡顿或中断。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)',
     'Text(stringResource(R.string.browser_wake_lock_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)'),
    ('Text("本 App 音效", style = MaterialTheme.typography.bodyLarge)',
     'Text(stringResource(R.string.browser_audio_effects), style = MaterialTheme.typography.bodyLarge)'),
    ('Text("使用 Android Equalizer/BassBoost/LoudnessEnhancer，只影响当前播放器。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)',
     'Text(stringResource(R.string.browser_audio_effects_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)'),
    ('Text("音效预设", style = MaterialTheme.typography.titleSmall)', 'Text(stringResource(R.string.browser_audio_preset), style = MaterialTheme.typography.titleSmall)'),
    ('Text("高品质输出偏好", style = MaterialTheme.typography.bodyLarge)',
     'Text(stringResource(R.string.browser_high_quality_output), style = MaterialTheme.typography.bodyLarge)'),
    ('Text("ExoPlayer 下启用音频 offload 偏好和更大的缓冲；是否真正无损直出取决于手机系统。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)',
     'Text(stringResource(R.string.browser_high_quality_output_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)'),
    ('Icon(Icons.Default.Settings, contentDescription = "播放器配置")',
     'Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.browser_player_config_title))'),
    ('Text(if (showPlaybackDiagnostics) "隐藏诊断" else "播放诊断")',
     'Text(if (showPlaybackDiagnostics) stringResource(R.string.browser_hide_diagnostics) else stringResource(R.string.browser_playback_diagnostics))'),
    ('"内核：${diagnostics.engine.ifBlank { "未知" }}"', 'context.getString(R.string.browser_engine_value, diagnostics.engine.ifBlank { context.getString(R.string.common_unknown_error) })'),
    ('"来源：${if (diagnostics.playbackSource == "direct") "直接播放" else diagnostics.playbackSource}"',
     'context.getString(R.string.browser_source_value, if (diagnostics.playbackSource == "direct") context.getString(R.string.browser_source_direct) else diagnostics.playbackSource)'),
    ('"输出：${diagnostics.outputDevice.ifBlank { "未知" }}"',
     'context.getString(R.string.browser_output_value, diagnostics.outputDevice.ifBlank { context.getString(R.string.common_unknown_error) })'),
    ('diagnostics.outputDeviceSource.takeIf { it.isNotBlank() }?.let { "输出依据：$it" }',
     'diagnostics.outputDeviceSource.takeIf { it.isNotBlank() }?.let { context.getString(R.string.browser_output_based_on, it) }'),
    ('"高品质输出偏好：${diagnostics.highQualityOutput.ifBlank { "未知" }}"',
     'context.getString(R.string.browser_hq_output_value, diagnostics.highQualityOutput.ifBlank { context.getString(R.string.common_unknown_error) })'),
    ('"音效：${diagnostics.audioEffects.ifBlank { "未知" }}"',
     'context.getString(R.string.browser_effects_value, diagnostics.audioEffects.ifBlank { context.getString(R.string.common_unknown_error) })'),
    ('"加载切换：${diagnostics.bufferEvents}"', 'context.getString(R.string.browser_buffer_events, diagnostics.bufferEvents)'),
    ('"错误次数：${diagnostics.playerErrors}"', 'context.getString(R.string.browser_error_count, diagnostics.playerErrors)'),
    ('diagnostics.lastError?.let { "最后错误：$it" }', 'diagnostics.lastError?.let { context.getString(R.string.browser_last_error, it) }'),
    ('diagnostics.mimeType?.let { "类型：$it" }', 'diagnostics.mimeType?.let { context.getString(R.string.browser_type_value, it) }'),
    ('diagnostics.sourceQuality?.let { "音源性质：$it" }', 'diagnostics.sourceQuality?.let { context.getString(R.string.browser_source_nature, it) }'),
    ('diagnostics.bitrate?.let { "码率：$it bps" }', 'diagnostics.bitrate?.let { context.getString(R.string.browser_bitrate, it) }'),
    ('diagnostics.sampleRate?.let { "采样率：$it Hz" }', 'diagnostics.sampleRate?.let { context.getString(R.string.browser_sample_rate, it) }'),
    ('diagnostics.bitsPerSample?.let { "位深：$it bit" }', 'diagnostics.bitsPerSample?.let { context.getString(R.string.browser_bit_depth, it) }'),
    ('"目录列表 · 重新排序时从源目录刷新 · 删除曲目会同步删除源文件"',
     'context.getString(R.string.browser_playlist_dir_list_refresh_hint)'),
    ('text = { Text("复制到列表") }', 'text = { Text(stringResource(R.string.browser_copy_to_list)) }'),
    ('text = { Text("移动到列表") }', 'text = { Text(stringResource(R.string.browser_move_to_list)) }'),
    ('"${pl.trackCount} 首 · 书签 $bookmarkCount"',
     'context.getString(R.string.browser_playlist_track_bookmark, pl.trackCount, bookmarkCount)'),
    ('"目录列表 · 删除曲目会同步删除源文件"', 'context.getString(R.string.browser_playlist_dir_list_hint)'),
    ('title = { Text("新建空播放列表") }', 'title = { Text(stringResource(R.string.browser_new_empty_playlist)) }'),
    ('label = { Text("列表名称") }', 'label = { Text(stringResource(R.string.browser_playlist_name_label)) }'),
    ('title = { Text(if (transfer.move) "移动到播放列表" else "复制到播放列表") }',
     'title = { Text(if (transfer.move) stringResource(R.string.browser_move_to_playlist) else stringResource(R.string.browser_copy_to_playlist)) }'),
    ('"没有可移入的普通播放列表。目录列表不能作为目标，请先新建普通播放列表。"',
     'context.getString(R.string.browser_playlist_no_normal_move_target)'),
    ('if (isObfuscate) "快速混淆" else "快速去混淆"',
     'if (isObfuscate) context.getString(R.string.context_quick_obfuscate) else context.getString(R.string.context_quick_deobfuscate)'),
    ('Text(if (isObfuscate) "混淆中…" else "去混淆中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)',
     'Text(if (isObfuscate) stringResource(R.string.browser_obfuscating) else stringResource(R.string.browser_deobfuscating), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)'),
    ('passwordLabel: String = "密码"', 'passwordLabel: String = context.getString(R.string.browser_enter_password)'),
    ('if (isDecrypt) "GnuPG 解密" else "GnuPG 加密"', 'if (isDecrypt) context.getString(R.string.context_gpg_decrypt) else context.getString(R.string.context_gpg_encrypt)'),
    ('if (isDecrypt) "解密中…" else "加密中…"', 'if (isDecrypt) stringResource(R.string.browser_decrypting) else stringResource(R.string.browser_encrypting)'),
    ('"GnuPG 加密"', 'context.getString(R.string.context_gpg_encrypt)'),
    ('Text("使用密码对称加密", style = MaterialTheme.typography.bodyMedium)',
     'Text(stringResource(R.string.browser_gpg_use_symmetric_encrypt), style = MaterialTheme.typography.bodyMedium)'),
    ('Text("或选择一个公钥进行非对称加密", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)',
     'Text(stringResource(R.string.browser_gpg_or_select_public_key), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)'),
    ('Text("加密中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)',
     'Text(stringResource(R.string.browser_encrypting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)'),
    (') { Text("保存") }', ') { Text(stringResource(R.string.common_save)) }'),
    ('Button(onClick = onSymmetric, modifier = Modifier.fillMaxWidth()) { Text("密码解密（对称）") }',
     'Button(onClick = onSymmetric, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.browser_gpg_password_decrypt)) }'),
    ('Button(onClick = onSecretKey, modifier = Modifier.fillMaxWidth()) { Text("私钥解密") }',
     'Button(onClick = onSecretKey, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.browser_gpg_secret_key_decrypt)) }'),
    ('Button(onClick = onSymmetric, modifier = Modifier.fillMaxWidth()) { Text("对称加密（密码）") }',
     'Button(onClick = onSymmetric, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.browser_gpg_symmetric_encrypt)) }'),
    ('Button(onClick = onPublicKey, modifier = Modifier.fillMaxWidth()) { Text("公钥加密") }',
     'Button(onClick = onPublicKey, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.browser_op_public_key_encrypt)) }'),
    ('Text("公钥加密", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)',
     'Text(stringResource(R.string.browser_op_public_key_encrypt), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)'),
    ('}) { Text("加密") }', '}) { Text(stringResource(R.string.quick_crypto_action_encrypt_short)) }'),
]

EXTRA = {
    "browser_playlist_dir_no_move_target": ("目录列表不能作为移入目标", "Directory playlists cannot be move targets"),
    "browser_source_direct": ("直接播放", "Direct playback"),
    "browser_gpg_use_symmetric_encrypt": ("使用密码对称加密", "Use password symmetric encryption"),
    "browser_gpg_or_select_public_key": ("或选择一个公钥进行非对称加密", "Or select a public key for asymmetric encryption"),
    "browser_gpg_password_decrypt": ("密码解密（对称）", "Password decrypt (symmetric)"),
    "browser_gpg_secret_key_decrypt": ("私钥解密", "Private key decrypt"),
    "browser_gpg_symmetric_encrypt": ("对称加密（密码）", "Symmetric encrypt (password)"),
    "browser_decrypting": ("解密中…", "Decrypting…"),
    "context_gpg_decrypt": ("GnuPG 解密", "GnuPG decrypt"),
    "context_gpg_encrypt": ("GnuPG 加密", "GnuPG encrypt"),
    "browser_search_result_size_modified": ("大小 %1$s · 修改 %2$s", "Size %1$s · modified %2$s"),
    "browser_unknown": ("未知", "Unknown"),
}

for path in [Path(__file__).resolve().parents[1] / "app/src/main/res/values/strings.xml",
             Path(__file__).resolve().parents[1] / "app/src/main/res/values-en/strings.xml"]:
    c = path.read_text(encoding='utf-8')
    lang = 0 if 'values-en' not in str(path) else 1
    adds = []
    for k, (zh, en) in EXTRA.items():
        if f'name="{k}"' not in c:
            v = (zh if lang == 0 else en).replace('&', '&amp;').replace('<', '&lt;')
            adds.append(f'    <string name="{k}">{v}</string>')
    if adds:
        c = c.replace('</resources>', '\n'.join(adds) + '\n</resources>')
        path.write_text(c, encoding='utf-8')

count = 0
for old, new in REPLACEMENTS:
    if old in text:
        text = text.replace(old, new)
        count += 1
    else:
        print(f"MISSING: {old[:65]}...")
KT.write_text(text, encoding="utf-8")
print(f"Applied {count}/{len(REPLACEMENTS)}")
