package anhiutangerinee.kittisu.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import anhiutangerinee.kittisu.R

/** Numeric 3x4 keypad for PIN entry; submits automatically at [maxLength] digits. */
@Composable
fun PinKeypad(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLength: Int = 12,
    onSubmit: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "•".repeat(pin.length),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (digit in 1..9) {
                item {
                    OutlinedButton(
                        enabled = enabled,
                        onClick = {
                            if (pin.length < maxLength) pin += digit.toString()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.6f),
                    ) {
                        Text(digit.toString(), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            item {}
            item {
                OutlinedButton(
                    enabled = enabled,
                    onClick = { if (pin.length < maxLength) pin += "0" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f),
                ) {
                    Text("0", style = MaterialTheme.typography.titleLarge)
                }
            }
            item {
                TextButton(
                    enabled = enabled && pin.isNotEmpty(),
                    onClick = { pin = pin.dropLast(1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f),
                ) {
                    Icon(Icons.AutoMirrored.TwoTone.Backspace, contentDescription = "delete")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.height(8.dp))
            TextButton(enabled = enabled && pin.isNotEmpty(), onClick = { onSubmit(pin) }) {
                Text(stringResource(android.R.string.ok))
            }
        }
    }
}
