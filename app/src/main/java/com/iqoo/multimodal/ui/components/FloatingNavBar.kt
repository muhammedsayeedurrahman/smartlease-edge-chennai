package com.iqoo.multimodal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iqoo.multimodal.ui.theme.PillBackground
import com.iqoo.multimodal.ui.theme.PillSelected
import com.iqoo.multimodal.ui.theme.TextPrimary
import com.iqoo.multimodal.ui.theme.TextSecondary

@Composable
fun FloatingNavBar(
    selectedScreen: String,
    onScreenSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .height(70.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(35.dp), spotColor = Color.Black.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(35.dp),
        color = PillBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NavItem(
                title = "Image",
                icon = Icons.Default.CameraAlt,
                isSelected = selectedScreen == "Image",
                onClick = { onScreenSelected("Image") },
                modifier = Modifier.weight(1f)
            )
            
            // Middle gap
            Spacer(modifier = Modifier.width(16.dp))

            NavItem(
                title = "Audio",
                icon = Icons.Default.Mic,
                isSelected = selectedScreen == "Audio",
                onClick = { onScreenSelected("Audio") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun NavItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) PillSelected else Color.Transparent
    val contentColor = if (isSelected) TextPrimary else TextSecondary

    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
