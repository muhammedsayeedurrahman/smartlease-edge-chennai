package com.smartlease.edge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.House
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartlease.edge.ui.theme.CorporateYellow

@Composable
fun WelcomeScreen(
    onCreateProperty: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Business,
                contentDescription = "Corporate Logo",
                modifier = Modifier.size(72.dp),
                tint = CorporateYellow
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Welcome to SmartLease",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Professional property verification powered by advanced AI.\nSelect a property type to start.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(40.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CompactPropertyTypeCard(
                    title = "New Home",
                    icon = Icons.Rounded.House,
                    onClick = { onCreateProperty(false) }
                )
                CompactPropertyTypeCard(
                    title = "New Flat",
                    icon = Icons.Rounded.Apartment,
                    onClick = { onCreateProperty(true) }
                )
            }
        }
    }
}

@Composable
fun CompactPropertyTypeCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.elevatedCardColors(
            containerColor = CorporateYellow,
            contentColor = Color.Black
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(32.dp),
                tint = Color.Black
            )
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = title, 
                style = MaterialTheme.typography.titleMedium, 
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
