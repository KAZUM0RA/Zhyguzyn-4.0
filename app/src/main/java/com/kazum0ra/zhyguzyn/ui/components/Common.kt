package com.kazum0ra.zhyguzyn.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kazum0ra.zhyguzyn.ui.theme.AppColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import com.kazum0ra.zhyguzyn.AppContainer
import com.kazum0ra.zhyguzyn.R
import com.kazum0ra.zhyguzyn.ZhyguzynApp
import com.kazum0ra.zhyguzyn.domain.OdometerReading

/** ViewModel, що отримує залежності з AppContainer. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as ZhyguzynApp).container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/**
 * Верхня панель у стилі застосунку: темна, з тонкою лінією знизу.
 * [onBack] == null — без стрілки «назад» (для вкладок нижнього меню).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    Column {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.Medium) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            },
            actions = { actions() },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
        )
        HorizontalDivider(color = AppColors.CardBorder)
    }
}

/** Картка з рамкою, як на головному екрані. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = verticalArrangement,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppColors.Card)
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(24.dp))
            .padding(20.dp),
        content = content,
    )
}

/** Велика кнопка дії з рамкою (напр. «Залили пальне»). */
@Composable
fun BigActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, AppColors.CardBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = AppColors.Button,
            contentColor = AppColors.TextPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

/** Підпис і значення в колонці («Вистачить на» / «≈ 420 км»). */
@Composable
fun ValueColumn(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = AppColors.TextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        }
    }
}

/** Поле для десяткового числа з текстом помилки. */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' || it == '-' }) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: supportingText)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier,
    )
}

/** Показник лічильника, коли закінчиться пальне: значення і підказка про обнулення. */
@Composable
fun EmptyAtColumn(reading: OdometerReading, modifier: Modifier = Modifier) {
    ValueColumn(
        label = stringResource(R.string.empty_at),
        value = stringResource(R.string.empty_at_value, Format.km(Math.round(reading.km).toDouble())),
        hint = when (reading.resets) {
            0 -> null
            1 -> stringResource(R.string.empty_at_one_reset)
            else -> stringResource(R.string.empty_at_resets, reading.resets)
        },
        modifier = modifier,
    )
}
