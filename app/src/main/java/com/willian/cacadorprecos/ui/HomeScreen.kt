package com.willian.cacadorprecos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.TextButton
import com.willian.cacadorprecos.data.Repo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willian.cacadorprecos.data.AppState
import com.willian.cacadorprecos.data.Fmt
import com.willian.cacadorprecos.data.Product

/** Há alerta ativo? Queda acima do percentual ou preço-alvo atingido. */
fun hasAlert(p: Product): Boolean {
    val cur = p.current?.price ?: return false
    val ch = p.changePct
    if (ch != null && ch <= -p.alertPct) return true
    val alvo = p.targetPrice
    return alvo != null && cur <= alvo
}

fun atLowest(p: Product): Boolean {
    val cur = p.current?.price ?: return false
    val low = p.lowest?.price ?: return false
    return p.history.size > 1 && cur <= low
}

@Composable
fun HomeScreen(
    state: AppState,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onCheckAll: () -> Unit,
    onRefreshOne: (String) -> Unit
) {
    var filter by rememberSaveable { mutableStateOf("todos") }
    var excluir by remember { mutableStateOf<Product?>(null) }
    val lista = state.products.filter { p ->
        when (filter) {
            "queda" -> (p.changePct ?: 0.0) < -0.05
            "alerta" -> hasAlert(p)
            else -> true
        }
    }
    val nQueda = state.products.count { (it.changePct ?: 0.0) < -0.05 }
    val nAlerta = state.products.count { hasAlert(it) }
    val s = state.settings

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                val sub = when {
                    state.checking -> "Verificando preços agora…"
                    s.lastRun > 0L -> "Última verificação " + Fmt.time(s.lastRun) + " · a cada " + Fmt.interval(s.intervalMin)
                    else -> "Verificação automática a cada " + Fmt.interval(s.intervalMin)
                }
                Header(
                    title = "Caçador de Preços",
                    subtitle = sub,
                    trailing = {
                        IconButton(onClick = onCheckAll) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Verificar todos os produtos agora", tint = Color.White)
                        }
                    },
                    extra = {
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Tile(Modifier.weight(1f), state.products.size.toString(), "Produtos", Pal.Navy)
                            Tile(Modifier.weight(1f), nQueda.toString(), "Em queda", Pal.GoodFg)
                            Tile(Modifier.weight(1f), nAlerta.toString(), "Alertas", Pal.Navy)
                        }
                    }
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChipBtn("Todos", filter == "todos", { filter = "todos" })
                    ChipBtn("Em queda", filter == "queda", { filter = "queda" })
                    ChipBtn("Alertas", filter == "alerta", { filter = "alerta" })
                }
            }
            if (state.products.isEmpty()) {
                item {
                    AppCard(Modifier.padding(horizontal = 20.dp)) {
                        Text("Nenhum produto ainda", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Toque no botão + para cadastrar o primeiro. O app passa a procurar preços no intervalo definido em Ajustes.",
                            fontSize = 13.sp, color = Pal.Muted, lineHeight = 19.sp
                        )
                    }
                }
            }
            items(lista, key = { it.id }) { p ->
                ProductCard(
                    p, onClick = { onOpen(p.id) }, onRefresh = { onRefreshOne(p.id) },
                    onEdit = { onOpen(p.id) }, onDelete = { excluir = p }
                )
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            containerColor = Pal.Teal,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Adicionar produto")
        }
    }
    val alvo = excluir
    if (alvo != null) {
        AlertDialog(
            onDismissRequest = { excluir = null },
            title = { Text("Excluir produto?") },
            text = { Text("O histórico e o menor valor registrado de " + alvo.name + " serão apagados.") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.removeProduct(alvo.id)
                    excluir = null
                }) { Text("Excluir", color = Pal.Danger) }
            },
            dismissButton = { TextButton(onClick = { excluir = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun Tile(modifier: Modifier, number: String, label: String, color: Color) {
    AppCard(modifier, padding = 12.dp) {
        Text(number, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 12.sp, color = Pal.Muted)
    }
}

@Composable
private fun ProductCard(p: Product, onClick: () -> Unit, onRefresh: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val cur = p.current
    val tone = toneFor(p.changePct)
    AppCard(
        Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        onClick = onClick,
        padding = 16.dp
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp, maxLines = 2)
                if (p.model.isNotBlank()) {
                    Text("Modelo " + p.model, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Pal.FlatFg)
                }
                val n = p.offers.size
                val sub = if (cur != null) cur.store + (if (n > 1) " · menor de $n lojas" else "") + " · " + Fmt.time(cur.time) else
                    if (p.paused) "Pausado" else "Aguardando a primeira leitura"
                Text(sub, fontSize = 12.sp, color = Pal.Muted)
                val erro = p.lastError
                if (cur == null && erro != null) {
                    Text(erro, fontSize = 11.sp, color = Pal.Muted, lineHeight = 15.sp, maxLines = 3)
                }
            }
            val ch = p.changePct
            if (ch != null) Pill(Fmt.pct(ch), tone.bg, tone.fg, Modifier.padding(top = 4.dp))
            IconButton(onClick = onRefresh, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar este produto agora", tint = Pal.Teal)
            }
        }
        if (cur != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.money(cur.price), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    val low = p.lowest
                    if (low != null) {
                        Text("Menor registrado " + Fmt.money(low.price), fontSize = 12.sp, color = Pal.Muted)
                    }
                }
                Sparkline(p.history.map { it.price }, tone.line, Modifier.size(width = 112.dp, height = 36.dp))
            }
        }
        val baixo = atLowest(p)
        val alerta = hasAlert(p)
        if (baixo || alerta) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (baixo) Pill("Menor preço registrado", Pal.GoldPill, Pal.GoldFg)
                if (alerta) Pill("Alerta de queda", Pal.Teal, Color.White)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onEdit) { Text("Editar", color = Pal.Teal, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onDelete) { Text("Excluir", color = Pal.Danger, fontWeight = FontWeight.Bold) }
        }
    }
}
