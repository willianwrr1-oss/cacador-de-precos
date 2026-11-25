package com.willian.cacadorprecos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willian.cacadorprecos.data.AppState
import com.willian.cacadorprecos.data.Fmt
import com.willian.cacadorprecos.data.ProductLink
import com.willian.cacadorprecos.data.Repo
import com.willian.cacadorprecos.data.Stores
import com.willian.cacadorprecos.work.Scheduler

@Composable
fun SourcesScreen(state: AppState) {
    val ctx = LocalContext.current
    val s = state.settings
    var dialogo by remember { mutableStateOf(false) }
    var editarLoja by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(
            title = "Fontes",
            subtitle = "Onde o app procura preços, nas duas frentes de pesquisa."
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("Lojas")
            AppCard(padding = 4.dp) {
                Stores.ALL.forEach { st ->
                    val ligada = st.name !in s.disabledStores
                    val nLinks = state.products.count { p -> p.links.any { it.store == st.name } }
                    val lida = state.status[st.name]
                    val nota = buildString {
                        append(if (nLinks > 0) nLinks.toString() + (if (nLinks == 1) " produto com link" else " produtos com link") else "Só busca aberta")
                        append(" · ")
                        if (!ligada) append("pausada")
                        else if (lida == null) append("ainda sem leitura")
                        else if (lida.ok) append("lida em " + Fmt.time(lida.time))
                        else append("falhou: " + lida.message)
                    }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(36.dp).background(Pal.FlatBg, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(st.name.take(1), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(Modifier.weight(1f).clickable { editarLoja = st.name }) {
                            Text(st.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                (if (s.storeUrls.containsKey(st.name)) "Link editado · " else "") + "Toque para editar o link",
                                fontSize = 12.sp, color = Pal.Teal, fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                nota, fontSize = 12.sp, lineHeight = 17.sp,
                                color = if (lida != null && !lida.ok && ligada) Pal.UpFg else Pal.Muted
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = ligada,
                            onCheckedChange = { on ->
                                Repo.setSettings { cfg ->
                                    cfg.copy(
                                        disabledStores = if (on) cfg.disabledStores - st.name else cfg.disabledStores + st.name
                                    )
                                }
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                                checkedTrackColor = Pal.Teal,
                                uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
                                uncheckedTrackColor = Pal.OffTrack,
                                uncheckedBorderColor = Pal.OffTrack
                            )
                        )
                    }
                    Rule()
                }
                TextButton(
                    onClick = { dialogo = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text("+ Adicionar link de loja a um produto", color = Pal.Teal, fontWeight = FontWeight.Bold)
                }
            }

            SectionLabel("Busca em grandes lojas")
            AppCard {
                SwitchRow(
                    "Pesquisar além dos links cadastrados",
                    "Procura o mesmo produto nas lojas ligadas acima.",
                    s.openSearch,
                    { on -> Repo.setSettings { it.copy(openSearch = on) } }
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Só as lojas da lista são consultadas. Resultados de sites fora dela são ignorados, e preços muito diferentes do histórico do produto são descartados.",
                    fontSize = 12.sp, color = Pal.Muted, lineHeight = 18.sp
                )
            }
        }
    }

    val lojaEd = Stores.ALL.firstOrNull { it.name == editarLoja }
    if (lojaEd != null) {
        var texto by remember(lojaEd.name) { mutableStateOf(Stores.template(lojaEd, s)) }
        var erro by remember(lojaEd.name) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editarLoja = null },
            title = { Text("Link de busca · " + lojaEd.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Endereço usado para procurar o produto nesta loja. Escreva {busca} onde entra o nome do produto.",
                        fontSize = 13.sp, color = Pal.FlatFg, lineHeight = 18.sp
                    )
                    Spacer(Modifier.size(10.dp))
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it; erro = null },
                        label = { Text("Endereço de busca") },
                        isError = erro != null,
                        supportingText = { if (erro != null) Text(erro ?: "") },
                        minLines = 2, maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Padrão: " + Stores.defaultTemplate(lojaEd),
                        fontSize = 11.sp, color = Pal.Muted, lineHeight = 15.sp
                    )
                    if (s.storeUrls.containsKey(lojaEd.name)) {
                        TextButton(onClick = {
                            Repo.setSettings { it.copy(storeUrls = it.storeUrls - lojaEd.name) }
                            editarLoja = null
                        }) { Text("Restaurar padrão", color = Pal.Danger) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = texto.trim()
                    if (!(u.startsWith("http://") || u.startsWith("https://"))) {
                        erro = "Comece com https://"
                    } else if (!u.contains(Stores.MARCA)) {
                        erro = "Inclua {busca} no endereço"
                    } else {
                        Repo.setSettings {
                            if (u == Stores.defaultTemplate(lojaEd)) it.copy(storeUrls = it.storeUrls - lojaEd.name)
                            else it.copy(storeUrls = it.storeUrls + (lojaEd.name to u))
                        }
                        editarLoja = null
                    }
                }) { Text("Salvar", color = Pal.Teal, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { editarLoja = null }) { Text("Cancelar") } }
        )
    }

    if (dialogo) {
        var escolhido by remember { mutableStateOf<String?>(null) }
        var link by remember { mutableStateOf("") }
        var erro by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { dialogo = false },
            title = { Text("Adicionar link de loja") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (state.products.isEmpty()) {
                        Text("Cadastre um produto primeiro.", color = Pal.Muted)
                    }
                    state.products.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { escolhido = p.id },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = escolhido == p.id,
                                onClick = { escolhido = p.id },
                                colors = RadioButtonDefaults.colors(selectedColor = Pal.Teal)
                            )
                            Text(p.name, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it; erro = null },
                        singleLine = true,
                        label = { Text("Endereço da página do produto") },
                        isError = erro != null,
                        supportingText = { if (erro != null) Text(erro ?: "") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = link.trim()
                    val alvo = escolhido
                    if (alvo == null) {
                        erro = "Escolha um produto"
                    } else if (!(u.startsWith("http://") || u.startsWith("https://"))) {
                        erro = "Cole o endereço completo, começando com https://"
                    } else {
                        Repo.editProduct(alvo) { it.copy(links = it.links + ProductLink(Stores.nameFor(u), u)) }
                        Scheduler.runOne(ctx, alvo)
                        dialogo = false
                    }
                }) { Text("Adicionar", color = Pal.Teal, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { dialogo = false }) { Text("Cancelar") } }
        )
    }
}
