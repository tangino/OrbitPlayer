package com.antigravity.equalizer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.antigravity.equalizer.R
import com.antigravity.equalizer.ui.theme.*

@Composable
fun PreampAndLimiterControl(
    preampGainDb: Float,
    onPreampGainChanged: (Float) -> Unit,
    limiterEnabled: Boolean,
    onLimiterToggle: (Boolean) -> Unit,
    isClipping: Boolean,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Preamp 控制
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.preamp),
                    tint = if (isEnabled) AccentOrange else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.preamp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = if (preampGainDb > 0) "+%.1f dB".format(preampGainDb) else "%.1f dB".format(preampGainDb),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) AccentOrange else TextSecondary
                )
                if (isClipping) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DangerRed)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.clip_warning),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Slider(
                value = preampGainDb,
                onValueChange = onPreampGainChanged,
                valueRange = -12f..12f,
                enabled = isEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = AccentOrange,
                    activeTrackColor = AccentOrange,
                    inactiveTrackColor = SurfaceDark
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Limiter 快速开关
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (limiterEnabled && isEnabled) AccentPurple.copy(alpha = 0.2f) else SurfaceDark)
                .clickable(enabled = isEnabled) { onLimiterToggle(!limiterEnabled) }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = stringResource(R.string.limiter),
                tint = if (limiterEnabled && isEnabled) AccentPurple else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.limiter),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (limiterEnabled && isEnabled) TextPrimary else TextSecondary
            )
        }
    }
}
