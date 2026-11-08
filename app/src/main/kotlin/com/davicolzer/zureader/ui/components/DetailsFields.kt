package com.davicolzer.zureader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R

/**
 * Nome (de onde veio: "Lápide da floresta") e descrição livre de um item, os dois opcionais.
 * O texto do item (a tradução, o conteúdo do QR) continua separado: estes campos são para a
 * pessoa reconhecer o item depois.
 */
@Composable
fun DetailsFields(
    name: String,
    description: String,
    onChange: (name: String, description: String) -> Unit,
    modifier: Modifier = Modifier,
    /** `false` = só o nome (ex.: em cima da foto, com a descrição embaixo dela). */
    showDescription: Boolean = true,
    /** `false` = só a descrição (o nome já aparece em cima, em outro lugar da tela). */
    showName: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (showName) {
        OutlinedTextField(
            name,
            { onChange(it, description) },
            singleLine = true,
            label = { Text(stringResource(R.string.details_name)) },
            placeholder = { Text(stringResource(R.string.details_name_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_pixel_pin), null, Modifier.size(18.dp)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
      }
        if (showDescription) {
            OutlinedTextField(
                description,
                { onChange(name, it) },
                minLines = 1,
                maxLines = 4,
                label = { Text(stringResource(R.string.details_description)) },
                placeholder = { Text(stringResource(R.string.details_description_hint)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_pixel_note), null, Modifier.size(18.dp)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
