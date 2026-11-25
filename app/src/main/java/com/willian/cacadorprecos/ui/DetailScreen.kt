package com.willian.cacadorprecos.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willian.cacadorprecos.data.AppState
import com.willian.cacadorprecos.data.Fmt
import com.willian.cacadorprecos.data.ProductLink
import com.willian.cacadorprecos.data.Repo
import com.willian.cacadorprecos.data.Stores
import com.willian.cacadorprecos.net.PriceParser
import com.willian.cacadorprecos.work.Scheduler

@Composable
fun DetailScreen(state: AppState, id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val p = state.products.firstOrNull { it.id == id }
    LaunchedEffect(p == null) { if (p == null) onBack() }
    if (p == null) {
        Box(Modifier.fillMaxSize())
        return
    }

    var novoLink by remember { mutableStateOf("") }
    var erroLink by remember { mutableStateOf<String?>(null) }
    var editando by remember { mutableStateOf<String?>(null) }
    var confirmarRemover by remember { mutableStateOf(false) }
    var descEd by remember(id) { mutableStateOf(p.name) }
    var modEd by remember(id) { mutableStateOf(p.model) }
    var catEd by remember(id) { mutableStateOf(p.category) }
    var alvoEd by remember(id) {
        mutableStateOf(p.targetPrice?.let { String.format(java.util.Locale("pt", "BR"), "%.2f", it) } ?: "")
    }

    val cur = p.current
    val tone = toneFor(p.changePct)
    val precos = p.history.map { it.price }
    val media = if (precos.isEmpty()) null else precos.average()
    val base: Double? = when (p.compare) {
        "media" -> media
        "menor" -> if (precos.isEmpty()) null else precos.min()
        else -> cur?.price
    }
    val gatilho = if (base == null) null else base * (1 - p.alertPct / 100.0)
    val baseRotulo = when (p.compare) {
        "media" -> "a média das leituras do período"
        "menor" -> "o menor preço do período"
        else -> "o último preço"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(
            title = p.name,
            subtitle = listOf(p.model, p.category).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
            leading = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
            },
            trailing = {
                IconButton(onClick = { confirmarRemover = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Excluir produto", tint = Color.White)
                }
            }
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ---- preço atual e gráfico ----
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Melhor preço agora", Modifier.weight(1f))
                    val ch = p.changePct
                    if (ch != null) Pill(Fmt.pct(ch), tone.bg, tone.fg)
                }
                if (cur == null) {
                    Spacer(Modifier.height(6.dp))
                    Text("Aguardando a primeira leitura", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    val err = p.lastError
                    if (err != null) {
                        Text(err, fontSize = 13.sp, color = Pal.UpFg)
                    } else {
                        Text("Toque em Verificar agora para buscar o preço.", fontSize = 13.sp, color = Pal.Muted)
                    }
                } else {
                    Text(Fmt.money(cur.price), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Text(cur.store + " · lido em " + Fmt.time(cur.time), fontSize = 13.sp, color = Pal.Muted)
                    Spacer(Modifier.height(12.dp))
                    PriceChart(precos, p.lowest?.price, Modifier.fillMaxWidth().height(140.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Fmt.day(p.history.first().time), fontSize = 11.sp, color = Pal.Muted)
                        Text(Fmt.day(p.history.last().time), fontSize = 11.sp, color = Pal.Muted)
                    }
                    Spacer(Modifier.height(12.dp))
                    Rule()
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Stat("Máximo", Fmt.money(precos.max()), Modifier.weight(1f))
                        Stat("Média", Fmt.money(media ?: 0.0), Modifier.weight(1f))
                        Stat("Mínimo", Fmt.money(precos.min()), Modifier.weight(1f))
                    }
                }
            }

            // ---- preços encontrados na última busca ----
            if (cur != null) {
                val ofertas = p.offers.ifEmpty { listOf(cur) }
                val menor = ofertas.first().price
                AppCard {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Preços encontrados", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            ofertas.size.toString() + if (ofertas.size == 1) " loja" else " lojas",
                            fontSize = 12.sp, color = Pal.Muted
                        )
                    }
                    Text(
                        "Última busca: " + Fmt.time(p.lastCheck.takeIf { it > 0 } ?: cur.time) + ". Toque em uma loja para abrir o anúncio.",
                        fontSize = 12.sp, color = Pal.Muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp)
                    )
                    ofertas.forEachIndexed { i, o ->
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp)
                                .clickable(enabled = o.url.isNotBlank()) {
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(o.url)))
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, "Não foi possível abrir o link", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                (i + 1).toString() + "º", Modifier.width(30.dp),
                                fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
                                color = if (i == 0) Pal.Teal else Pal.Muted
                            )
                            Text(o.store, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Fmt.money(o.price), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                if (i == 0) {
                                    Text("mais barato", fontSize = 11.sp, color = Pal.GoodFg, fontWeight = FontWeight.SemiBold)
                                } else {
                                    Text(
                                        "+" + Fmt.money(o.price - menor), fontSize = 11.sp, color = Pal.Muted
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Rule()
                    }
                    if (ofertas.any { it.store.endsWith("(similar)") }) {
                        Text(
                            "\"similar\" = anúncio parecido, mas sem o modelo no título. Confira antes de comprar; ele não dispara alerta se houver preço confirmado.",
                            fontSize = 12.sp, color = Pal.Muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (ofertas.size < 3) {
                        Text(
                            "Menos de 3 lojas com preço nesta busca." +
                                (p.lastError?.let { " Sem preço em: $it" } ?: ""),
                            fontSize = 12.sp, color = Pal.UpFg, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // ---- menor preço registrado ----
            val low = p.lowest
            if (low != null) {
                AppCard(background = Pal.GoldBg, borderColor = Color(0xFFEBD58A)) {
                    SectionLabel("Menor preço já verificado")
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(Fmt.money(low.price), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(low.store + " · " + Fmt.day(low.time), fontSize = 13.sp, color = Pal.GoldFg)
                    }
                    val fora = p.history.none { it.price <= low.price }
                    if (fora) {
                        Text(
                            "Guardado à parte, pois já saiu do período do histórico.",
                            fontSize = 12.sp, color = Pal.GoldFg, modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            // ---- alerta ----
            AppCard {
                Text("Alerta de preço", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Avisar quando cair", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Stepper(
                        p.alertPct.toString() + "%",
                        onMinus = { Repo.editProduct(id) { it.copy(alertPct = (it.alertPct - 1).coerceAtLeast(1)) } },
                        onPlus = { Repo.editProduct(id) { it.copy(alertPct = (it.alertPct + 1).coerceAtMost(50)) } }
                    )
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel("Comparar com")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChipBtn("Último preço", p.compare == "ultimo", { Repo.editProduct(id) { it.copy(compare = "ultimo") } }, Modifier.weight(1f))
                    ChipBtn("Média", p.compare == "media", { Repo.editProduct(id) { it.copy(compare = "media") } }, Modifier.weight(1f))
                    ChipBtn("Menor do período", p.compare == "menor", { Repo.editProduct(id) { it.copy(compare = "menor") } }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                if (gatilho != null) {
                    Text(
                        "Você recebe o aviso quando o preço ficar abaixo de " + Fmt.money(gatilho) +
                            ", uma queda de " + p.alertPct + "% sobre " + baseRotulo + ".",
                        fontSize = 13.sp, color = Pal.FlatFg, lineHeight = 19.sp
                    )
                } else {
                    Text(
                        "O alerta passa a valer depois da primeira leitura de preço.",
                        fontSize = 13.sp, color = Pal.FlatFg, lineHeight = 19.sp
                    )
                }
                val alvo = p.targetPrice
                if (alvo != null) {
                    Text(
                        "Preço-alvo: " + Fmt.money(alvo),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            // ---- dados do produto e histórico ----
            AppCard {
                Text("Editar produto", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Descrição e modelo definem a busca. O modelo precisa aparecer no anúncio da loja.",
                    fontSize = 12.sp, color = Pal.Muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
                OutlinedTextField(
                    value = descEd, onValueChange = { descEd = it },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    label = { Text("Descrição do produto") }, minLines = 2, maxLines = 3
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = modEd, onValueChange = { modEd = it },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
                    label = { Text("Modelo") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = catEd, onValueChange = { catEd = it },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
                    label = { Text("Categoria (opcional)") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = alvoEd, onValueChange = { alvoEd = it },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
                    label = { Text("Preço-alvo em R$ (opcional)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val d = descEd.trim()
                        val m = modEd.trim()
                        val alvo = if (alvoEd.isBlank()) null else PriceParser.parseMoney(alvoEd)
                        if (d.isEmpty() || m.isEmpty()) {
                            Toast.makeText(ctx, "Informe a descrição e o modelo", Toast.LENGTH_SHORT).show()
                        } else if (alvoEd.isNotBlank() && alvo == null) {
                            Toast.makeText(ctx, "Preço-alvo inválido. Exemplo: 1.599,00", Toast.LENGTH_SHORT).show()
                        } else {
                            Repo.editProduct(id) { it.copy(name = d, model = m, category = catEd.trim(), targetPrice = alvo) }
                            Scheduler.runOne(ctx, id)
                            Toast.makeText(ctx, "Dados salvos. Buscando preços…", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Salvar alterações", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(16.dp))
                Text("Guardar o histórico por", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Leituras mais antigas que o período são apagadas. O menor valor já visto continua guardado.",
                    fontSize = 12.sp, color = Pal.Muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
                KeepPicker(p.keepDays) { d -> Repo.editProduct(id) { it.copy(keepDays = d) } }
            }

            // ---- onde buscar ----
            AppCard(padding = 16.dp) {
                SwitchRow(
                    "Fontes cadastradas", "Lê os links de loja deste produto", p.searchRegistered,
                    { v -> Repo.editProduct(id) { it.copy(searchRegistered = v) } }
                )
                Rule()
                SwitchRow(
                    "Busca em grandes lojas", "Só varejistas de grande porte", p.searchOpen,
                    { v -> Repo.editProduct(id) { it.copy(searchOpen = v) } }
                )
            }

            // ---- links ----
            AppCard {
                Text("Links das lojas", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                if (p.links.isEmpty()) {
                    Text(
                        "Nenhum link cadastrado. Cole o endereço da página do produto em uma loja para acompanhar o preço nela.",
                        fontSize = 13.sp, color = Pal.Muted, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                p.links.forEach { l ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.store, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(l.url, fontSize = 11.sp, color = Pal.Muted, maxLines = 1)
                        }
                        TextButton(onClick = {
                            editando = l.url
                            novoLink = l.url
                            erroLink = null
                        }) { Text("Editar", color = Pal.Teal) }
                        TextButton(onClick = {
                            if (editando == l.url) { editando = null; novoLink = "" }
                            Repo.editProduct(id) { pr -> pr.copy(links = pr.links.filter { it.url != l.url }) }
                        }) { Text("Excluir", color = Pal.Danger) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = novoLink,
                    onValueChange = { novoLink = it; erroLink = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    label = { Text(if (editando != null) "Editando o link: altere o endereço" else "Endereço da página do produto") },
                    isError = erroLink != null,
                    supportingText = { if (erroLink != null) Text(erroLink ?: "") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val u = novoLink.trim()
                        if (!(u.startsWith("http://") || u.startsWith("https://"))) {
                            erroLink = "Cole o endereço completo, começando com https://"
                        } else {
                            val antigo = editando
                            val novo = ProductLink(Stores.nameFor(u), u)
                            Repo.editProduct(id) { pr ->
                                val resto = if (antigo == null) pr.links else pr.links.filter { it.url != antigo }
                                pr.copy(links = resto.filter { it.url != u } + novo)
                            }
                            novoLink = ""
                            editando = null
                            Scheduler.runOne(ctx, id)
                            Toast.makeText(ctx, "Link salvo. Buscando preços…", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (editando != null) "Salvar link" else "Adicionar link", fontWeight = FontWeight.Bold) }
                if (editando != null) {
                    TextButton(
                        onClick = { editando = null; novoLink = ""; erroLink = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancelar edição") }
                }
            }

            // ---- histórico ----
            AppCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Histórico (" + Fmt.keep(p.keepDays) + ")", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("mais recente primeiro", fontSize = 12.sp, color = Pal.Muted)
                }
                if (p.history.size > 30) {
                    Text(
                        "Mostrando as 30 leituras mais recentes de " + p.history.size + ".",
                        fontSize = 12.sp, color = Pal.Muted, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (p.history.isEmpty()) {
                    Text("Ainda sem leituras.", fontSize = 13.sp, color = Pal.Muted, modifier = Modifier.padding(top = 8.dp))
                }
                val rev = p.history.reversed().take(30)
                rev.forEachIndexed { idx, h ->
                    val i = p.history.size - 1 - idx
                    val prev = if (i > 0) p.history[i - 1].price else null
                    val d = if (prev == null) null else (h.price - prev) / prev * 100.0
                    val t = toneFor(d)
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(Fmt.time(h.time), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (idx == 0) {
                                    Spacer(Modifier.size(6.dp))
                                    Pill("atual", Pal.GoodBg, Pal.GoodFg)
                                }
                            }
                            Text(h.store, fontSize = 12.sp, color = Pal.Muted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.money(h.price), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (d == null) "primeira leitura" else Fmt.pct(d),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = t.fg
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Rule()
                }
                if (low != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Menor valor registrado", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Pal.GoldFg)
                            Text(low.store + " · " + Fmt.day(low.time), fontSize = 12.sp, color = Pal.GoldFg)
                        }
                        Text(Fmt.money(low.price), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Pal.GoldFg)
                    }
                }
            }

            // ---- ações ----
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        Scheduler.runOne(ctx, id)
                        Toast.makeText(ctx, "Verificando…", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Teal, contentColor = Color.White)
                ) { Text("Verificar agora", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = { Repo.editProduct(id) { it.copy(paused = !it.paused) } },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (p.paused) "Retomar" else "Pausar", fontWeight = FontWeight.Bold) }
            }
            if (cur != null && cur.url.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cur.url)))
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Não foi possível abrir o link", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Abrir na loja (" + cur.store + ")", fontWeight = FontWeight.Bold) }
            }
            TextButton(onClick = { confirmarRemover = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Excluir produto", color = Pal.Danger, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }

    if (confirmarRemover) {
        AlertDialog(
            onDismissRequest = { confirmarRemover = false },
            title = { Text("Excluir produto?") },
            text = { Text("O histórico e o menor valor registrado de " + p.name + " serão apagados.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarRemover = false
                    Repo.removeProduct(id)
                    onBack()
                }) { Text("Excluir", color = Pal.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmarRemover = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = Pal.Muted)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
