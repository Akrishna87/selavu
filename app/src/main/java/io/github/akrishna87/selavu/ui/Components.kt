package io.github.akrishna87.selavu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.selavu.core.Account
import io.github.akrishna87.selavu.core.AccountType
import io.github.akrishna87.selavu.core.CardBill
import io.github.akrishna87.selavu.core.Category
import io.github.akrishna87.selavu.core.shortDate
import io.github.akrishna87.selavu.core.Ledger
import io.github.akrishna87.selavu.core.flow
import io.github.akrishna87.selavu.core.Money
import io.github.akrishna87.selavu.core.Period
import io.github.akrishna87.selavu.core.PeriodType
import io.github.akrishna87.selavu.core.Transaction
import io.github.akrishna87.selavu.core.TxType
import java.time.LocalDate

/** A tap target with a soft grey highlight, like a tapped row on iOS. */
fun Modifier.tap(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = null,
    indication = androidx.compose.material3.ripple(color = Color.Gray),
    role = Role.Button,
    onClick = onClick,
)

/** A large title, as at the top of iOS screens. */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.displaySmall, color = palette.label,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
    )
}

/** iOS navigation bar: optional left and right buttons, the title centred. */
@Composable
fun NavBar(title: String, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { leading?.invoke() }
        Text(
            title, style = MaterialTheme.typography.titleMedium, color = palette.label,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 88.dp),
        )
        Box(Modifier.align(Alignment.CenterEnd)) { trailing?.invoke() }
    }
}

/** A plain text button in the tint colour, like "Cancel" and "Save" in a navigation bar. */
@Composable
fun TextAction(text: String, bold: Boolean = false, enabled: Boolean = true, color: Color = palette.tint, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = if (enabled) color else palette.tertiaryLabel,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.tap(onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

/** "‹ Settings": back to the previous screen. */
@Composable
fun BackAction(label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).tap(onClick).padding(end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Back", tint = palette.tint, modifier = Modifier.size(32.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = palette.tint)
    }
}

/** A white rounded card on the grey background: an inset grouped list section. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(palette.card),
        content = content,
    )
}

/** The thin line between rows of a card, starting at [inset] like iOS. */
@Composable
fun RowDivider(inset: Dp = 16.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(0.5.dp).background(palette.separator))
}

/** Grey capitals above a card, as in iOS grouped lists. */
@Composable
fun SectionHeader(title: String, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel) }
    }
}

/** Grey small print under a card. */
@Composable
fun SectionFooter(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp),
    )
}

/** iOS segmented control: a grey track with the chosen segment as a raised white pill. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(9.dp)).background(palette.fill).padding(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).fillMaxWidth().height(28.dp)
                    .then(
                        if (on) Modifier.shadow(2.dp, RoundedCornerShape(7.dp)).background(palette.segment, RoundedCornerShape(7.dp))
                        else Modifier,
                    )
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = 13.sp, letterSpacing = (-0.1).sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = palette.label, maxLines = 1,
                )
            }
        }
    }
}

/** The period being looked at as a large title, with ‹ › to step and Day/Week/Month/Year under it. */
@Composable
fun PeriodHeader(
    type: PeriodType,
    period: Period,
    onType: (PeriodType) -> Unit,
    onShift: (Long) -> Unit,
    onToday: () -> Unit,
) {
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).tap(onToday).padding(start = 16.dp, top = 8.dp, bottom = 4.dp)) {
                Text(period.label(today), style = MaterialTheme.typography.displaySmall, color = palette.label, maxLines = 1)
                Text(
                    period.subLabel(today) ?: " ",
                    style = MaterialTheme.typography.bodyMedium, color = palette.secondaryLabel,
                )
            }
            RoundIcon(Icons.Rounded.ChevronLeft, "Previous") { onShift(-1) }
            Spacer(Modifier.width(10.dp))
            RoundIcon(Icons.Rounded.ChevronRight, "Next") { onShift(1) }
        }
        Segmented(
            PeriodType.entries.map { it.label }, PeriodType.entries.indexOf(type),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) { onType(PeriodType.entries[it]) }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(palette.fill).tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = palette.tint, modifier = Modifier.size(24.dp))
    }
}

/** A pill to pick from, like the chips in iOS filters: blue when chosen, grey otherwise. */
@Composable
fun Pill(text: String, selected: Boolean, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
            .background(if (selected) palette.tint else palette.fill)
            .tap(onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(
            text, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else palette.label, maxLines = 1,
        )
    }
}

/** A rounded square with the category's emoji on a light wash of its colour. */
@Composable
fun CategoryIcon(category: Category?, size: Dp = 34.dp) {
    val color = categoryColor(category?.color ?: 0xFF8E8E93)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(color.copy(alpha = if (palette.dark) 0.30f else 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(category?.emoji ?: "❔", fontSize = (size.value * 0.52f).sp, textAlign = TextAlign.Center)
    }
}

/** A coloured rounded square with a white symbol, as in the iOS Settings app. */
@Composable
fun SettingsIcon(icon: ImageVector, color: Color) {
    Box(Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

/**
 * One entry in a list. Its amount is what it did to the money in [currency] (see [flow]), or
 * [change] when given, such as what it did to one account.
 */
@Composable
fun TransactionRow(
    t: Transaction,
    ledger: Ledger,
    currency: String,
    subtitle: String? = null,
    change: Long? = null,
    onClick: () -> Unit,
) {
    val transfer = t.type == TxType.TRANSFER
    val category = ledger.category(t.categoryId)
    // Which account, when there's more than one; for a transfer, from where to where.
    val accounts = if (transfer) "${ledger.account(t.accountId)?.name ?: "?"} → ${ledger.account(t.toAccountId)?.name ?: "?"}"
    else ledger.account(t.accountId)?.name?.takeIf { ledger.accounts.size > 1 }
    val flow = change ?: ledger.flow(t, currency)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).tap(onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (transfer) TransferIcon() else CategoryIcon(category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (transfer) "Transfer" else category?.name ?: "Deleted category",
                style = MaterialTheme.typography.bodyLarge, color = palette.label,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val second = listOfNotNull(subtitle, accounts, t.note.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (second.isNotEmpty()) {
                Text(
                    second, style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (t.recurringId != null) {
            Icon(Icons.Rounded.Repeat, contentDescription = "Repeats", Modifier.size(15.dp), tint = palette.tertiaryLabel)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            if (flow == null) Money.format(t.amount, currency) else Money.format(flow, currency, sign = true),
            style = MaterialTheme.typography.bodyLarge,
            color = if (flow != null && flow > 0) palette.green else palette.label,
        )
    }
}

@Composable
fun TransferIcon(size: Dp = 34.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(palette.fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = palette.secondaryLabel, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
fun AccountIcon(account: Account?, size: Dp = 34.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(palette.fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(account?.type?.emoji ?: "🗂️", fontSize = (size.value * 0.52f).sp)
    }
}

/** An account's balance as people say it: what a credit card is owed, what anything else holds. */
fun balanceText(account: Account, balance: Long): String = when {
    account.type == AccountType.CARD && balance < 0 -> "${Money.format(-balance, account.currency)} owed"
    else -> Money.format(balance, account.currency)
}

/** A card bill in a few words: "₹3,000 due 5 Nov", "Overdue: ₹3,000", "Paid". */
fun billLine(bill: CardBill, currency: String, today: LocalDate): String {
    val amount = Money.format(bill.remaining, currency)
    val days = bill.daysLeft(today)
    return when {
        bill.remaining == 0L -> if (bill.statementOwed == 0L) "Nothing due" else "Bill paid"
        days < 0 -> "Overdue: $amount was due ${shortDate(bill.dueDate, today)}"
        days == 0L -> "$amount due today"
        days == 1L -> "$amount due tomorrow"
        else -> "$amount due ${shortDate(bill.dueDate, today)}"
    }
}

/** The colour for a bill line: red when overdue, orange when due within a few days. */
@Composable
fun billColor(bill: CardBill, today: LocalDate): Color = when {
    bill.remaining == 0L -> palette.secondaryLabel
    bill.overdue(today) -> palette.red
    bill.daysLeft(today) <= 3 -> palette.orange
    else -> palette.secondaryLabel
}

/** Switches between currency groups; shown only when there's more than one. */
@Composable
fun CurrencySwitcher(currencies: List<String>, current: String, onChoose: (String) -> Unit) {
    if (currencies.size < 2) return
    Segmented(
        currencies.map { it.trim() }, currencies.indexOf(current).coerceAtLeast(0),
        Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) { onChoose(currencies[it]) }
}

/** A row in a settings-style card: icon, title, grey value on the right, and a chevron. */
@Composable
fun ListRow(
    title: String,
    value: String? = null,
    icon: (@Composable () -> Unit)? = null,
    chevron: Boolean = true,
    titleColor: Color = palette.label,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    subtitle: String? = null,
    subtitleColor: Color = palette.secondaryLabel,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp)
            .then(if (onClick != null) Modifier.tap(onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = subtitleColor) }
        }
        value?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = palette.secondaryLabel, maxLines = 1, textAlign = TextAlign.End)
        }
        trailing?.invoke(this)
        if (chevron && onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = palette.tertiaryLabel, modifier = Modifier.size(22.dp))
        }
    }
}

/** A borderless text field with a grey placeholder, for use inside a card row or a grey well. */
@Composable
fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    prefix: String? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = style.copy(color = palette.label),
        singleLine = true,
        cursorBrush = SolidColor(palette.tint),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                prefix?.let { Text(it, style = style, color = if (value.isEmpty()) palette.tertiaryLabel else palette.label) }
                Box {
                    if (value.isEmpty()) Text(placeholder, style = style, color = palette.tertiaryLabel, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

/** A text field in a grey rounded well, for dialogs. */
@Composable
fun WellField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    prefix: String? = null,
) {
    PlainField(
        value, onValueChange, placeholder,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.fill).padding(horizontal = 12.dp, vertical = 11.dp),
        keyboardOptions = keyboardOptions, prefix = prefix,
    )
}

@Composable
fun EmptyState(emoji: String, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.size(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = palette.label, textAlign = TextAlign.Center)
        Spacer(Modifier.size(2.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryLabel, textAlign = TextAlign.Center)
    }
}
