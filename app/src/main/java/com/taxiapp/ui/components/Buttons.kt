package com.taxiapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.taxiapp.R
import com.taxiapp.ui.theme.TaxiTheme

/** The one amber action on a screen. Shows a spinner instead of the label while [loading]. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = TaxiTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accent,
            contentColor = c.onAccent,
            disabledContainerColor = if (loading) c.accent else c.surfaceRaised,
            disabledContentColor = if (loading) c.onAccent else c.textMuted,
        ),
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = c.onAccent, strokeWidth = 2.5.dp)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Round back button used at the top of full-screen flows. */
@Composable
fun BackButton(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(c.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                painterResource(R.drawable.ic_arrow_left),
                contentDescription = contentDescription,
                tint = c.text,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Quiet companion to [PrimaryButton] for the second choice on a screen. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @androidx.annotation.DrawableRes icon: Int? = null,
    contentColor: androidx.compose.ui.graphics.Color = TaxiTheme.colors.text,
) {
    val c = TaxiTheme.colors
    androidx.compose.foundation.layout.Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceRaised)
            .pressable(onClick = onClick),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = contentColor, modifier = Modifier.size(18.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}
