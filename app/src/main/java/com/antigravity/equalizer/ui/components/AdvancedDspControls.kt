package com.antigravity.equalizer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.equalizer.R
import com.antigravity.equalizer.ui.theme.*

/**
 * 前级增益下方 DSP 核心旋钮控制区：
 * 1. Bass Boost (低音增强) 旋转旋钮
 * 2. Treble Boost (高音增强) 旋转旋钮
 * 3. Compressor (动态压缩) 开关
 */
@Composable
fun AdvancedDspControls(
    bassBoostEnabled: Boolean,
    bassBoostStrength: Float,
    onBassBoostToggle: (Boolean) -> Unit,
    onBassBoostStrengthChange: (Float) -> Unit,
    trebleBoostEnabled: Boolean,
    trebleBoostStrength: Float,
    onTrebleBoostToggle: (Boolean) -> Unit,
    onTrebleBoostStrengthChange: (Float) -> Unit,
    compressorEnabled: Boolean,
    onCompressorToggle: (Boolean) -> Unit,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. 低音增强 (Bass Boost 旋钮)
        DspRotaryKnob(
            title = stringResource(R.string.bass_boost),
            strength = bassBoostStrength,
            enabled = isEnabled && bassBoostEnabled,
            onStrengthChanged = onBassBoostStrengthChange,
            onToggleEnabled = onBassBoostToggle,
            modifier = Modifier.weight(1f)
        )

        // 2. 高音增强 (Treble Boost 旋钮)
        DspRotaryKnob(
            title = stringResource(R.string.treble_boost),
            strength = trebleBoostStrength,
            enabled = isEnabled && trebleBoostEnabled,
            onStrengthChanged = onTrebleBoostStrengthChange,
            onToggleEnabled = onTrebleBoostToggle,
            modifier = Modifier.weight(1f)
        )

        // 3. Compressor 动态压缩器开关卡片
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(72.dp)
                .height(118.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (compressorEnabled && isEnabled) AccentOrange.copy(alpha = 0.22f) else Color(0xFF1E212D))
                .clickable(enabled = isEnabled) { onCompressorToggle(!compressorEnabled) }
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Compress,
                contentDescription = stringResource(R.string.compressor),
                tint = if (compressorEnabled && isEnabled) AccentOrange else TextSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.compressor),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (compressorEnabled && isEnabled) TextPrimary else TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (compressorEnabled && isEnabled) "ON" else "OFF",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (compressorEnabled && isEnabled) AccentOrange else TextSecondary
            )
        }
    }
}
