package com.davicolzer.zureader.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.davicolzer.zureader.R

/** A barra de cima das telas: voltar, o título e, à direita, as [actions] da tela. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(@StringRes title: Int, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        navigationIcon = { BarAction(R.drawable.ic_pixel_back, stringResource(R.string.back), onBack) },
        title = { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) },
        actions = actions,
    )
}

/** Um botão de ícone na barra de cima. */
@Composable
fun BarAction(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), description, Modifier.size(22.dp))
    }
}

/** A lixeira da barra de cima (apagar o item salvo, depois da confirmação). */
@Composable
fun DeleteAction(onClick: () -> Unit) = BarAction(R.drawable.ic_pixel_trash, stringResource(R.string.result_delete), onClick)
